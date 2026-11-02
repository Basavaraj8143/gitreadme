package com.byteharvest.mdreader

import com.byteharvest.mdreader.core.localDocumentFileName
import com.byteharvest.mdreader.core.isMarkdownDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDocumentTest {
    @Test fun recognizesMarkdownExtensionsWithoutCaseSensitivity() {
        assertTrue(isMarkdownDocument("Guide.md", "application/octet-stream"))
        assertTrue(isMarkdownDocument("Guide.MARKDOWN", "text/plain"))
    }

    @Test fun recognizesMarkdownMimeWhenProviderOmitsExtension() {
        assertTrue(isMarkdownDocument("Guide", "text/markdown; charset=utf-8"))
    }

    @Test fun rejectsNonMarkdownFilesEvenWithPlainTextMime() {
        assertFalse(isMarkdownDocument("notes.txt", "text/plain"))
        assertFalse(isMarkdownDocument("archive.zip", "application/octet-stream"))
    }

    @Test fun reopeningSameUriUsesSameStoredFilename() {
        val first = localDocumentFileName("content://provider/document/42", "Guide.md")
        val second = localDocumentFileName("content://provider/document/42", "Guide.md")

        assertEquals(first, second)
        assertTrue(first.endsWith("_Guide.md"))
    }

    @Test fun differentUrisWithSameNameGetSeparateFiles() {
        val first = localDocumentFileName("content://provider/document/42", "Guide.md")
        val second = localDocumentFileName("content://provider/document/43", "Guide.md")

        assertNotEquals(first, second)
    }

    @Test fun displayNameCannotEscapeLocalDocumentFolder() {
        val fileName = localDocumentFileName("content://provider/document/42", "../../Guide.md")

        assertFalse(fileName.contains('/'))
        assertFalse(fileName.contains('\\'))
        assertTrue(fileName.endsWith("_Guide.md"))
    }
}
