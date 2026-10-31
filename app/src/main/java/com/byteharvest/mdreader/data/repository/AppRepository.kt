package com.byteharvest.mdreader.data.repository

import androidx.room.withTransaction
import com.byteharvest.mdreader.core.security.AuthState
import com.byteharvest.mdreader.core.security.TokenStore
import com.byteharvest.mdreader.data.local.AppDatabase
import com.byteharvest.mdreader.data.local.BookmarkEntity
import com.byteharvest.mdreader.data.local.CachedDocumentEntity
import com.byteharvest.mdreader.data.local.HistoryEntity
import com.byteharvest.mdreader.data.local.LOCAL_REPOSITORY_ID
import com.byteharvest.mdreader.data.local.OfflineRepositoryItem
import com.byteharvest.mdreader.data.local.RepositoryEntity
import com.byteharvest.mdreader.data.local.TreeEntryEntity
import com.byteharvest.mdreader.data.remote.GitHubApi
import com.byteharvest.mdreader.data.remote.GitHubError
import com.byteharvest.mdreader.data.remote.GitHubException
import com.byteharvest.mdreader.data.remote.RepoDto
import com.byteharvest.mdreader.data.remote.TreeDto
import com.byteharvest.mdreader.domain.isMarkdownPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncReport(
    val updatedDocs: Int = 0,
    val failedDocs: Int = 0,
    val removedFiles: Int = 0,
    val error: String? = null,
    val truncated: Boolean = false,
)

data class OfflineDownloadReport(val downloaded: Int, val alreadyCached: Int, val failed: Int)

data class BookmarkBackup(
    val owner: String,
    val repoName: String,
    val branch: String,
    val path: String,
    val title: String,
    val createdAt: Long,
)

sealed interface DocLoad {
    data class Ok(val text: String, val fromCache: Boolean, val note: String? = null) : DocLoad
    data class Fail(val message: String) : DocLoad
}

/**
 * Cache policy:
 *  - The Markdown file tree (path + blob SHA) is stored per repo+branch and replaced inside one transaction.
 *  - Document text is fetched lazily and reused while its SHA still matches the tree.
 *  - Cached text is only deleted when its file has vanished from a successfully fetched tree,
 *    or replaced after a successful fetch. A failed network call never removes anything.
 */
class AppRepository(
    private val db: AppDatabase,
    private val api: GitHubApi,
    private val tokens: TokenStore,
) {
    private val dao = db.dao()
    private val _authState = MutableStateFlow(
        tokens.load()?.let { AuthState(signedIn = true, username = tokens.username()) } ?: AuthState(signedIn = false)
    )
    val authState = _authState.asStateFlow()

    fun repositories() = dao.repositories()
    fun offlineRepositories() = dao.offlineRepositories()
    fun repoFlow(id: String) = dao.repoFlow(id)
    suspend fun repo(id: String) = dao.repo(id)
    fun treePaths(id: String) = dao.treePaths(id)
    fun recentDocs(limit: Int) = dao.recent(limit)
    suspend fun removeFromHistory(repoId: String, path: String) = dao.deleteHistory(repoId, path)
    fun bookmarks() = dao.bookmarks()
    fun isBookmarked(id: String, path: String) = dao.isBookmarked(id, path)
    suspend fun touch(id: String) = dao.touchRepo(id, System.currentTimeMillis())
    suspend fun hasFile(id: String, path: String) = dao.treeSha(id, path) != null
    suspend fun deleteRepository(id: String) = dao.deleteRepo(id)
    suspend fun clearCachedDocuments(id: String) = dao.clearDocs(id)

    fun makeId(owner: String, name: String, branch: String) = "${owner.lowercase()}/${name.lowercase()}@$branch"

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: GitHubException) {
        if (e.kind == GitHubError.Unauthorized) {
            tokens.clear()
            _authState.value = AuthState(signedIn = false, message = e.message)
        }
        throw e
    }

    private fun mdEntries(id: String, tree: TreeDto) =
        tree.tree.filter { it.type == "blob" && isMarkdownPath(it.path) }.map { TreeEntryEntity(id, it.path, it.sha) }

    private suspend fun applyTree(repo: RepositoryEntity, entries: List<TreeEntryEntity>): Int {
        var removed = 0
        db.withTransaction {
            dao.upsertRepo(repo)
            val keep = entries.map { it.path }.toHashSet()
            val gone = dao.treeEntries(repo.id).map { it.path }.filter { it !in keep }
            gone.chunked(500).forEach { dao.deleteTree(repo.id, it); dao.deleteDocs(repo.id, it) }
            dao.upsertTree(entries)
            removed = gone.size
        }
        return removed
    }

    /** Saves (or updates) a repository + branch. Re-opening an existing one upserts; no duplicates are created. */
    suspend fun openRepository(owner: String, name: String, branch: String?): Result<String> = try {
        val info = call { api.getRepo(owner, name) }
        val b = branch ?: info.defaultBranch
        val tree = call { api.getTree(info.owner.login, info.name, b) }
        val id = makeId(info.owner.login, info.name, b)
        val now = System.currentTimeMillis()
        val existing = dao.repo(id)
        val entity = RepositoryEntity(
            id, info.owner.login, info.name, b, info.description, info.isPrivate,
            existing?.addedAt ?: now, now, now, if (tree.truncated) "PARTIAL" else "OK",
        )
        applyTree(entity, mdEntries(id, tree))
        Result.success(id)
    } catch (e: GitHubException) {
        Result.failure(e)
    }

    suspend fun branches(id: String): Result<List<String>> {
        val r = dao.repo(id) ?: return Result.failure(IllegalStateException("Repository not saved."))
        return try {
            Result.success(call { api.getBranches(r.owner, r.name) }.map { it.name })
        } catch (e: GitHubException) {
            Result.failure(e)
        }
    }

    suspend fun refresh(id: String): SyncReport {
        val repo = dao.repo(id) ?: return SyncReport(error = "Repository is no longer saved.")
        val now = System.currentTimeMillis()
        val tree = try {
            call { api.getTree(repo.owner, repo.name, repo.branch) }
        } catch (e: GitHubException) {
            dao.setSync(id, now, "FAILED") // cache untouched
            return SyncReport(error = e.message)
        }
        val entries = mdEntries(id, tree)
        val removed = applyTree(repo.copy(lastSyncAt = now, syncState = if (tree.truncated) "PARTIAL" else "OK"), entries)
        val shaByPath = entries.associate { it.path to it.sha }
        var updated = 0
        var failed = 0
        for (meta in dao.cachedDocMeta(id)) {
            val sha = shaByPath[meta.path] ?: continue
            if (sha == meta.sha) continue
            try {
                val text = call { api.getFileRaw(repo.owner, repo.name, repo.branch, meta.path) }
                if (text.length <= MAX_CACHE_CHARS) dao.upsertDoc(CachedDocumentEntity(id, meta.path, sha, text, System.currentTimeMillis()))
                updated++
            } catch (e: GitHubException) {
                failed++ // old copy is kept
                if (e.kind == GitHubError.RateLimited) break
            }
        }
        if (failed > 0) dao.setSync(id, now, "PARTIAL")
        return SyncReport(updated, failed, removed, truncated = tree.truncated)
    }

    suspend fun loadDocument(id: String, path: String, force: Boolean = false): DocLoad {
        val repo = dao.repo(id) ?: return DocLoad.Fail("This repository is no longer saved.")
        val cached = dao.doc(id, path)
        val treeSha = dao.treeSha(id, path) ?: return DocLoad.Fail("This file is no longer in the repository.")
        if (cached != null && !force && cached.sha == treeSha) return DocLoad.Ok(cached.content, fromCache = true)
        return try {
            val text = call { api.getFileRaw(repo.owner, repo.name, repo.branch, path) }
            if (text.length <= MAX_CACHE_CHARS) {
                dao.upsertDoc(CachedDocumentEntity(id, path, treeSha, text, System.currentTimeMillis()))
            }
            DocLoad.Ok(text, fromCache = false)
        } catch (e: GitHubException) {
            if (cached != null) DocLoad.Ok(cached.content, true, "Showing saved copy. ${e.message}")
            else DocLoad.Fail(e.message ?: "Could not load this document.")
        }
    }

    suspend fun downloadForOffline(
        id: String,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): OfflineDownloadReport {
        val repo = dao.repo(id) ?: throw IllegalStateException("Repository is no longer saved.")
        val entries = dao.treeEntries(id)
        val cached = dao.cachedDocMeta(id).associate { it.path to it.sha }
        var downloaded = 0
        var alreadyCached = 0
        var failed = 0
        var completed = 0
        for (entry in entries) {
            if (cached[entry.path] == entry.sha) {
                alreadyCached++
                completed++
                onProgress(completed, entries.size)
                continue
            }
            try {
                val text = call { api.getFileRaw(repo.owner, repo.name, repo.branch, entry.path) }
                if (text.length <= MAX_CACHE_CHARS) {
                    dao.upsertDoc(CachedDocumentEntity(id, entry.path, entry.sha, text, System.currentTimeMillis()))
                    downloaded++
                } else {
                    failed++
                }
            } catch (e: GitHubException) {
                failed++
                if (e.kind in setOf(GitHubError.RateLimited, GitHubError.Unauthorized, GitHubError.Forbidden)) {
                    completed++
                    onProgress(completed, entries.size)
                    break
                }
            }
            completed++
            onProgress(completed, entries.size)
        }
        return OfflineDownloadReport(downloaded, alreadyCached, failed + (entries.size - completed))
    }

    suspend fun recordHistory(id: String, path: String, title: String) {
        val now = System.currentTimeMillis()
        if (id == LOCAL_REPOSITORY_ID && dao.repo(id) == null) {
            dao.upsertRepo(
                RepositoryEntity(
                    id = LOCAL_REPOSITORY_ID,
                    owner = "On this device",
                    name = "Local files",
                    branch = "local",
                    description = null,
                    isPrivate = false,
                    addedAt = now,
                    lastOpenedAt = now,
                    lastSyncAt = null,
                    syncState = "OK",
                )
            )
        }
        dao.upsertHistory(HistoryEntity(id, path, title, now))
        dao.touchRepo(id, now)
    }

    suspend fun toggleBookmark(id: String, path: String, title: String, currentlyBookmarked: Boolean) {
        if (currentlyBookmarked) dao.deleteBookmark(id, path)
        else {
            if (id == LOCAL_REPOSITORY_ID && dao.repo(id) == null) {
                recordHistory(id, path, title)
            }
            dao.upsertBookmark(BookmarkEntity(id, path, title, System.currentTimeMillis()))
        }
    }

    suspend fun removeBookmark(id: String, path: String) = dao.deleteBookmark(id, path)

    suspend fun restoreBookmarks(bookmarks: List<BookmarkBackup>): Pair<Int, Int> {
        var restored = 0
        var skipped = 0
        for (bookmark in bookmarks) {
            val id = makeId(bookmark.owner, bookmark.repoName, bookmark.branch)
            if (dao.repo(id) == null) {
                skipped++
                continue
            }
            dao.upsertBookmark(
                BookmarkEntity(id, bookmark.path, bookmark.title, bookmark.createdAt),
            )
            restored++
        }
        return restored to skipped
    }

    // --- authentication (Personal Access Token) ---
    fun token(): String? = tokens.load()

    suspend fun signIn(token: String): Result<String> = try {
        _authState.value = _authState.value.copy(busy = true, message = null)
        val cleanToken = token.trim()
        val login = api.validateToken(cleanToken)
        tokens.save(cleanToken, login)
        _authState.value = AuthState(signedIn = true, message = "Signed in as $login.", username = login)
        Result.success(login)
    } catch (e: GitHubException) {
        _authState.value = _authState.value.copy(busy = false, message = e.message)
        Result.failure(e)
    }

    suspend fun restoreAuthUsername() {
        val token = tokens.load() ?: run {
            _authState.value = AuthState(signedIn = false)
            return
        }
        if (_authState.value.username != null) return
        try {
            val login = call { api.validateToken(token) }
            if (tokens.load() == token) {
                tokens.save(token, login)
                _authState.value = _authState.value.copy(username = login, message = null)
            }
        } catch (e: GitHubException) {
            if (_authState.value.signedIn) {
                _authState.value = _authState.value.copy(message = e.message)
            }
        }
    }

    suspend fun getUserRepos(): Result<List<RepoDto>> = try {
        Result.success(call { api.getUserRepos() })
    } catch (e: GitHubException) {
        Result.failure(e)
    }

    /** Signs out and removes cached private repositories so private content does not outlive the credential. */
    suspend fun signOut() {
        tokens.clear()
        _authState.value = AuthState(signedIn = false, message = "Signed out. Cached private repositories were removed.")
        dao.deletePrivateRepos()
    }

    companion object {
        /** Room reads rows through a ~2 MB cursor window; larger documents are shown but not cached. */
        const val MAX_CACHE_CHARS = 1_000_000
    }
}
