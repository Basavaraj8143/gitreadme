package com.byteharvest.mdreader.domain

import java.net.URLDecoder

fun isMarkdownPath(path: String): Boolean {
    val l = path.lowercase()
    return l.endsWith(".md") || l.endsWith(".markdown")
}

data class DirListing(val folders: List<String>, val files: List<String>)

/**
 * Lists one directory level from a flat list of Markdown file paths.
 * Folders exist only because a Markdown descendant exists, so empty folders never appear.
 * Pure function: no I/O, no mutation of anything remote.
 */
fun listDirectory(mdPaths: List<String>, dir: String): DirListing {
    val prefix = if (dir.isEmpty()) "" else "$dir/"
    val folders = sortedSetOf<String>(String.CASE_INSENSITIVE_ORDER)
    val files = ArrayList<String>()
    for (p in mdPaths) {
        if (!p.startsWith(prefix)) continue
        val rest = p.substring(prefix.length)
        if (rest.isEmpty()) continue
        if (rest.contains('/')) folders.add(rest.substringBefore('/')) else files.add(rest)
    }
    files.sortWith(String.CASE_INSENSITIVE_ORDER)
    return DirListing(folders.toList(), files)
}

fun filterPaths(mdPaths: List<String>, query: String): List<String> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return mdPaths.filter { it.lowercase().contains(q) }.sortedWith(String.CASE_INSENSITIVE_ORDER)
}

sealed interface LinkTarget {
    data class Anchor(val id: String) : LinkTarget
    data class External(val url: String) : LinkTarget
    data class RepoFile(val path: String, val anchor: String?) : LinkTarget
    data object Invalid : LinkTarget
}

object LinkResolver {
    private val schemeRe = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    private fun decode(s: String) = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        s
    }

    fun resolve(currentPath: String, dest: String): LinkTarget {
        val d = dest.trim()
        if (d.isEmpty()) return LinkTarget.Invalid
        if (d.startsWith("#")) return LinkTarget.Anchor(decode(d.substring(1)).lowercase())
        if (d.startsWith("//")) return LinkTarget.External("https:$d")
        if (schemeRe.containsMatchIn(d)) return LinkTarget.External(d)
        val anchor = d.substringAfter('#', "").ifEmpty { null }?.let { decode(it).lowercase() }
        val pathPart = d.substringBefore('#').substringBefore('?')
        if (pathPart.isEmpty()) return anchor?.let { LinkTarget.Anchor(it) } ?: LinkTarget.Invalid
        val resolved = resolveRepoRef(currentPath, pathPart) ?: return LinkTarget.Invalid
        return LinkTarget.RepoFile(resolved, anchor)
    }

    /** Resolves a relative reference against the current document. Null when it escapes the repo root. */
    fun resolveRepoRef(currentPath: String, ref: String): String? {
        val clean = decode(ref.substringBefore('#').substringBefore('?'))
        val stack = ArrayList<String>()
        if (!clean.startsWith("/")) currentPath.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.forEach { stack.add(it) }
        for (seg in clean.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.lastIndex)
                else -> stack.add(seg)
            }
        }
        return if (stack.isEmpty()) null else stack.joinToString("/")
    }
}
