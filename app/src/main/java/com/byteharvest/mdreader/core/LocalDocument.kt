package com.byteharvest.mdreader.core

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
