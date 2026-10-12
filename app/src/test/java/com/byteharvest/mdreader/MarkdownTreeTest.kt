package com.byteharvest.mdreader

import com.byteharvest.mdreader.domain.filterPaths
import com.byteharvest.mdreader.domain.isMarkdownPath
import com.byteharvest.mdreader.domain.listDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTreeTest {
    private val all = listOf(
        "README.md", "docs/guide.markdown", "docs/api/INSTALL.MD", "src/main.kt", "src/util/x.md",
        "images/logo.png", "notes.txt",
    )
    private val md = all.filter(::isMarkdownPath)

    @Test fun extensionsAreCaseInsensitive() {
        assertTrue(isMarkdownPath("README.md")); assertTrue(isMarkdownPath("a.MARKDOWN")); assertTrue(isMarkdownPath("INSTALL.MD"))
        assertFalse(isMarkdownPath("a.mdx")); assertFalse(isMarkdownPath("notes.txt")); assertFalse(isMarkdownPath("md"))
    }
    @Test fun rootListing() {
        val l = listDirectory(md, "")
        assertEquals(listOf("docs", "src"), l.folders)
        assertEquals(listOf("README.md"), l.files)
    }
    @Test fun nestedListing() {
        val l = listDirectory(md, "docs")
        assertEquals(listOf("api"), l.folders); assertEquals(listOf("guide.markdown"), l.files)
    }
    @Test fun foldersWithoutMarkdownAreHidden() { assertFalse("images" in listDirectory(md, "").folders) }
    @Test fun prefixIsNotConfusedWithSimilarFolder() {
        assertEquals(emptyList<String>(), listDirectory(listOf("docs2/a.md"), "docs").files)
    }
    @Test fun filter() {
        assertEquals(listOf("docs/api/INSTALL.MD"), filterPaths(md, "install"))
        assertTrue(filterPaths(md, " ").isEmpty())
    }
}
