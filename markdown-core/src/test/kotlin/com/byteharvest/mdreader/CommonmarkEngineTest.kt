package com.byteharvest.mdreader

import com.byteharvest.mdreader.core.markdown.CommonmarkEngine
import com.byteharvest.mdreader.core.markdown.MdBlock
import com.byteharvest.mdreader.core.markdown.countMatches
import com.byteharvest.mdreader.core.markdown.searchRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonmarkEngineTest {
    private val engine = CommonmarkEngine()

    @Test fun tocPreservesLevelsAndDuplicatesGetStableAnchors() {
        val doc = engine.parse("# Title\n\ntext\n\n## Intro\n\n## Intro\n\n### Deep dive!\n")
        assertEquals(listOf(1, 2, 2, 3), doc.toc.map { it.level })
        assertEquals(listOf("title", "intro", "intro-1", "deep-dive"), doc.toc.map { it.anchor })
    }
    @Test fun tablesParse() {
        val t = engine.parse("| a | b |\n|---|---|\n| 1 | 2 |\n").blocks.filterIsInstance<MdBlock.Table>().single()
        assertEquals(2, t.header.size); assertEquals(1, t.rows.size)
    }
    @Test fun taskListsParse() {
        val l = engine.parse("- [x] done\n- [ ] todo\n- plain\n").blocks.filterIsInstance<MdBlock.ListBlock>().single()
        assertEquals(listOf<Boolean?>(true, false, null), l.items.map { it.checked })
    }
    @Test fun fencedCodeKeepsLanguage() {
        val c = engine.parse("```kotlin\nval x = 1\n```\n").blocks.filterIsInstance<MdBlock.Code>().single()
        assertEquals("kotlin", c.lang); assertEquals("val x = 1", c.text)
    }
    @Test fun htmlImgBecomesImage() {
        val doc = engine.parse("<p align=\"center\"><img src=\"images/a.png\" alt=\"A\"></p>\n")
        assertTrue(doc.blocks.filterIsInstance<MdBlock.Paragraph>().isNotEmpty())
    }
    @Test fun searchCountsMatchRenderedRuns() {
        val b = engine.parse("Hello **hello** `HELLO`\n").blocks.first()
        assertEquals(3, b.searchRuns().sumOf { countMatches(it, "hello") })
    }
}
