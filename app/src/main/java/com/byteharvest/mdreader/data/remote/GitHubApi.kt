package com.byteharvest.mdreader.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class GitHubError { Network, Unauthorized, RateLimited, Forbidden, NotFound, Http, Parse }

class GitHubException(val kind: GitHubError, message: String) : Exception(message)

@Serializable data class OwnerDto(val login: String)

@Serializable
data class RepoDto(
    val name: String,
    @SerialName("default_branch") val defaultBranch: String,
    val description: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val owner: OwnerDto,
)

@Serializable data class BranchDto(val name: String)
@Serializable data class TreeItemDto(val path: String, val type: String, val sha: String)
@Serializable data class TreeDto(val tree: List<TreeItemDto> = emptyList(), val truncated: Boolean = false)
@Serializable data class UserDto(val login: String)

object GitHubUrls {
    private fun api(vararg segs: String) = HttpUrl.Builder().scheme("https").host("api.github.com")
        .apply { segs.forEach { addPathSegment(it) } }

    fun repo(o: String, n: String): HttpUrl = api("repos", o, n).build()
    fun branches(o: String, n: String): HttpUrl = api("repos", o, n, "branches").addQueryParameter("per_page", "100").build()
    fun tree(o: String, n: String, branch: String): HttpUrl =
        api("repos", o, n, "git", "trees", branch).addQueryParameter("recursive", "1").build()
    fun contents(o: String, n: String, branch: String, path: String): HttpUrl =
        api("repos", o, n, "contents").addPathSegments(path).addQueryParameter("ref", branch).build()
    fun user(): HttpUrl = api("user").build()
    fun userRepos(): HttpUrl = api("user", "repos").addQueryParameter("per_page", "100").addQueryParameter("sort", "updated").build()
    fun raw(o: String, n: String, branch: String, path: String): HttpUrl =
        HttpUrl.Builder().scheme("https").host("raw.githubusercontent.com")
            .addPathSegment(o).addPathSegment(n).addPathSegments(branch).addPathSegments(path).build()
    fun blob(o: String, n: String, branch: String, path: String): String =
        HttpUrl.Builder().scheme("https").host("github.com").addPathSegment(o).addPathSegment(n)
            .addPathSegment("blob").addPathSegments(branch).addPathSegments(path).build().toString()
}

/** Read-only GitHub REST client. The token (if any) is only ever sent to api.github.com. */
class GitHubApi(
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getRepo(owner: String, name: String): RepoDto = decode(get(GitHubUrls.repo(owner, name)))
    suspend fun getUserRepos(): List<RepoDto> = decode(get(GitHubUrls.userRepos()))
    suspend fun getBranches(owner: String, name: String): List<BranchDto> = decode(get(GitHubUrls.branches(owner, name)))
    suspend fun getTree(owner: String, name: String, branch: String): TreeDto = decode(get(GitHubUrls.tree(owner, name, branch)))
    suspend fun getFileRaw(owner: String, name: String, branch: String, path: String): String =
        get(GitHubUrls.contents(owner, name, branch, path), raw = true)

    /** Validates a token WITHOUT storing it. Returns the account login. */
    suspend fun validateToken(token: String): String = decode<UserDto>(get(GitHubUrls.user(), token = token)).login

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (e: SerializationException) {
        throw GitHubException(GitHubError.Parse, "Unexpected response from GitHub.")
    }

    private suspend fun get(url: HttpUrl, raw: Boolean = false, token: String? = tokenProvider()): String =
        withContext(Dispatchers.IO) {
            val rb = Request.Builder().url(url)
                .header("Accept", if (raw) "application/vnd.github.raw+json" else "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
            if (token != null) rb.header("Authorization", "Bearer $token")
            try {
                client.newCall(rb.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else throw mapError(resp)
                }
            } catch (e: IOException) {
                throw GitHubException(GitHubError.Network, "Network error. Check your connection and try again.")
            }
        }

    private fun mapError(resp: Response): GitHubException {
        val remaining = resp.header("x-ratelimit-remaining")
        val reset = resp.header("x-ratelimit-reset")?.toLongOrNull()
        return when {
            resp.code == 401 -> GitHubException(GitHubError.Unauthorized, "GitHub rejected the saved token (expired or revoked). Sign in again in Settings.")
            (resp.code == 403 || resp.code == 429) && (remaining == "0" || resp.header("retry-after") != null) -> {
                val mins = reset?.let { ((it * 1000 - System.currentTimeMillis()) / 60000).coerceAtLeast(1) }
                val hint = if (mins != null) " Try again in about $mins min." else ""
                GitHubException(GitHubError.RateLimited, "GitHub rate limit reached.$hint Signing in with a token raises the limit.")
            }
            resp.code == 403 -> GitHubException(GitHubError.Forbidden, "Access denied by GitHub.")
            resp.code == 404 -> GitHubException(GitHubError.NotFound, "Not found. The repository, branch or file may not exist, or it is private (add a token in Settings).")
            else -> GitHubException(GitHubError.Http, "GitHub returned an error (${resp.code}).")
        }
    }
}
