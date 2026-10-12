package com.byteharvest.mdreader.core

data class RepoInput(val owner: String, val name: String, val branch: String? = null, val path: String? = null)

sealed interface ParseResult {
    data class Ok(val input: RepoInput) : ParseResult
    data class Error(val message: String) : ParseResult
}

/** Parses "owner/repo", github.com URLs (with or without scheme), .git suffixes and git@github.com: URLs. */
object RepoInputParser {
    private val ownerRe = Regex("^[A-Za-z0-9-]+$")
    private val nameRe = Regex("^[A-Za-z0-9_.-]+$")
    private val schemeRe = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    fun parse(raw: String): ParseResult {
        var s = raw.trim()
        if (s.isEmpty()) return ParseResult.Error("Enter a GitHub URL or owner/repository.")
        if (s.startsWith("git@github.com:", ignoreCase = true)) s = s.substring("git@github.com:".length)
        val hasScheme = schemeRe.containsMatchIn(s)
        if (hasScheme) {
            val scheme = s.substringBefore("://").lowercase()
            if (scheme != "http" && scheme != "https") return ParseResult.Error("Only http(s) GitHub URLs are supported.")
            s = s.substringAfter("://")
        }
        s = s.substringBefore('#').substringBefore('?')
        val segs = s.split('/').filter { it.isNotEmpty() }.toMutableList()
        // GitHub user names cannot contain '.', so a dotted first segment is a host name.
        if (hasScheme || segs.firstOrNull()?.contains('.') == true) {
            val host = segs.removeFirstOrNull()?.lowercase()?.substringAfterLast('@')?.substringBefore(':')
            if (host != "github.com" && host != "www.github.com") {
                return ParseResult.Error("Unsupported host. Only github.com repositories are supported.")
            }
        }
        if (segs.size < 2) return ParseResult.Error("Use the form owner/repository, e.g. octocat/Hello-World.")
        val owner = segs[0]
        val name = segs[1].removeSuffix(".git")
        if (!ownerRe.matches(owner)) return ParseResult.Error("\"$owner\" is not a valid GitHub owner name.")
        if (!nameRe.matches(name) || name == "." || name == "..") return ParseResult.Error("\"$name\" is not a valid repository name.")
        var branch: String? = null
        var path: String? = null
        if (segs.size >= 4 && (segs[2] == "tree" || segs[2] == "blob")) {
            branch = segs[3] // branch names containing '/' cannot be told apart from a path in a URL
            path = segs.drop(4).joinToString("/").ifEmpty { null }
        }
        return ParseResult.Ok(RepoInput(owner, name, branch, path))
    }
}
