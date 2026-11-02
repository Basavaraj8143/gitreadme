package com.byteharvest.mdreader.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

fun localDocumentFileName(sourceUri: String, displayName: String): String {
    val safeName = displayName
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .trim(' ', '.')
        .ifEmpty { "document.md" }
    val uriHash = MessageDigest.getInstance("SHA-256")
        .digest(sourceUri.toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString("") { "%02x".format(it) }
    return "${uriHash}_$safeName"
}

fun isMarkdownDocument(displayName: String, mimeType: String?): Boolean {
    val extensionMatches = displayName.endsWith(".md", ignoreCase = true) ||
        displayName.endsWith(".markdown", ignoreCase = true)
    val hasNoExtension = displayName.substringAfterLast('.', "").isEmpty()
    val mimeTypeMatches = mimeType?.substringBefore(';')?.trim()?.lowercase() in setOf(
        "text/markdown",
        "application/markdown",
        "application/x-markdown",
        "text/x-markdown",
    )
    return extensionMatches || (hasNoExtension && mimeTypeMatches)
}

suspend fun importLocalMarkdown(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val queriedName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameColumn >= 0) cursor.getString(nameColumn) else null
    }
    val displayName = queriedName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: "document"
    val mimeType = context.contentResolver.getType(uri)

    require(isMarkdownDocument(displayName, mimeType)) {
        "Please select a Markdown (.md or .markdown) file"
    }

    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: throw IllegalStateException("Could not read file")

    val localDir = File(context.filesDir, "local_docs").apply { mkdirs() }
    val storedName = if (displayName.substringAfterLast('.', "").isEmpty()) "$displayName.md" else displayName
    File(localDir, localDocumentFileName(uri.toString(), storedName)).apply {
        writeText(content)
    }
}
