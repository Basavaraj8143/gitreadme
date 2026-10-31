package com.byteharvest.mdreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** All writes use @Upsert (never REPLACE) because REPLACE would cascade-delete child rows. */
@Dao
interface AppDao {
    @Query("SELECT * FROM repositories WHERE id != 'local' ORDER BY lastOpenedAt DESC")
    fun repositories(): Flow<List<RepositoryEntity>>

    @Query(
        "SELECT r.id AS repoId, r.owner AS owner, r.name AS repoName, r.branch AS branch, " +
            "r.description AS description, " +
            "(SELECT COUNT(*) FROM tree_entries t WHERE t.repoId = r.id) AS totalDocs, " +
            "(SELECT COUNT(*) FROM tree_entries t JOIN cached_documents c " +
            "ON c.repoId = t.repoId AND c.path = t.path AND c.sha = t.sha WHERE t.repoId = r.id) AS cachedDocs " +
            "FROM repositories r " +
            "WHERE (SELECT COUNT(*) FROM tree_entries t WHERE t.repoId = r.id) > 0 " +
            "AND (SELECT COUNT(*) FROM tree_entries t WHERE t.repoId = r.id) = " +
            "(SELECT COUNT(*) FROM tree_entries t JOIN cached_documents c " +
            "ON c.repoId = t.repoId AND c.path = t.path AND c.sha = t.sha WHERE t.repoId = r.id) " +
            "ORDER BY r.lastOpenedAt DESC"
    )
    fun offlineRepositories(): Flow<List<OfflineRepositoryItem>>

    @Query("SELECT * FROM repositories WHERE id = :id")
    suspend fun repo(id: String): RepositoryEntity?

    @Query("SELECT * FROM repositories WHERE id = :id")
    fun repoFlow(id: String): Flow<RepositoryEntity?>

    @Upsert suspend fun upsertRepo(r: RepositoryEntity)

    @Query("DELETE FROM repositories WHERE id = :id") suspend fun deleteRepo(id: String)

    @Query("DELETE FROM repositories WHERE isPrivate = 1") suspend fun deletePrivateRepos()

    @Query("UPDATE repositories SET lastOpenedAt = :t WHERE id = :id") suspend fun touchRepo(id: String, t: Long)

    @Query("UPDATE repositories SET lastSyncAt = :t, syncState = :s WHERE id = :id")
    suspend fun setSync(id: String, t: Long, s: String)

    @Query("SELECT path FROM tree_entries WHERE repoId = :id") fun treePaths(id: String): Flow<List<String>>

    @Query("SELECT * FROM tree_entries WHERE repoId = :id") suspend fun treeEntries(id: String): List<TreeEntryEntity>

    @Query("SELECT sha FROM tree_entries WHERE repoId = :id AND path = :path")
    suspend fun treeSha(id: String, path: String): String?

    @Upsert suspend fun upsertTree(list: List<TreeEntryEntity>)

    @Query("DELETE FROM tree_entries WHERE repoId = :id AND path IN (:paths)")
    suspend fun deleteTree(id: String, paths: List<String>)

    @Query("DELETE FROM cached_documents WHERE repoId = :id AND path IN (:paths)")
    suspend fun deleteDocs(id: String, paths: List<String>)

    @Query("SELECT * FROM cached_documents WHERE repoId = :id AND path = :path")
    suspend fun doc(id: String, path: String): CachedDocumentEntity?

    @Query("SELECT path, sha FROM cached_documents WHERE repoId = :id")
    suspend fun cachedDocMeta(id: String): List<DocMeta>

    @Upsert suspend fun upsertDoc(d: CachedDocumentEntity)

    @Query("DELETE FROM cached_documents WHERE repoId = :id") suspend fun clearDocs(id: String)

    @Upsert suspend fun upsertBookmark(b: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE repoId = :id AND path = :path")
    suspend fun deleteBookmark(id: String, path: String)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE repoId = :id AND path = :path")
    fun isBookmarked(id: String, path: String): Flow<Int>

    @Query(
        "SELECT b.repoId AS repoId, b.path AS path, b.title AS title, b.createdAt AS createdAt, " +
            "r.owner AS owner, r.name AS repoName, r.branch AS branch " +
            "FROM bookmarks b JOIN repositories r ON r.id = b.repoId ORDER BY b.createdAt DESC"
    )
    fun bookmarks(): Flow<List<BookmarkItem>>

    @Upsert suspend fun upsertHistory(h: HistoryEntity)

    @Query("DELETE FROM reading_history WHERE repoId = :repoId AND path = :path")
    suspend fun deleteHistory(repoId: String, path: String)

    @Query(
        "SELECT h.repoId AS repoId, h.path AS path, h.title AS title, h.lastOpenedAt AS lastOpenedAt, " +
            "r.owner AS owner, r.name AS repoName, r.branch AS branch " +
            "FROM reading_history h JOIN repositories r ON r.id = h.repoId " +
            "ORDER BY h.lastOpenedAt DESC LIMIT :limit"
    )
    fun recent(limit: Int): Flow<List<RecentDoc>>
}
