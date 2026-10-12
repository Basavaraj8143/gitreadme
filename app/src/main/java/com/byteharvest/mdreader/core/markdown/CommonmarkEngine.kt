package com.byteharvest.mdreader.core.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/** commonmark-java + GFM extensions (tables, strikethrough, task lists, autolinks). */
class CommonmarkEngine : MarkdownEngine {
    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListItemsExtension.create(),
                AutolinkExtension.create(),
            )
        )
        .build()

    override fun parse(source: String): MarkdownDoc {
        val root = parser.parse(source)
        val anchors = AnchorGen()
        val blocks = ArrayList<MdBlock>()
        var n: Node? = root.firstChild
        while (n != null) {
            convert(n, anchors, blocks)
            n = n.next
        }
        val toc = ArrayList<TocEntry>()
        blocks.forEachIndexed { i, b ->
            if (b is MdBlock.Heading) toc.add(TocEntry(b.level, b.inlines.plain(), b.anchor, i))
        }
        return MarkdownDoc(blocks, toc)
    }

    private fun convert(node: Node, anchors: AnchorGen, out: MutableList<MdBlock>) {
        when (node) {
            is Heading -> {
                val ins = inlines(node)
                out.add(MdBlock.Heading(node.level, ins, anchors.next(ins.plain())))
            }
            is Paragraph -> out.add(MdBlock.Paragraph(inlines(node)))
            is FencedCodeBlock -> out.add(
                MdBlock.Code(node.info?.trim()?.split(' ')?.firstOrNull()?.ifEmpty { null }, node.literal.trimEnd('\n'))
            )
            is IndentedCodeBlock -> out.add(MdBlock.Code(null, node.literal.trimEnd('\n')))
            is BlockQuote -> out.add(MdBlock.Quote(children(node, anchors)))
            is BulletList -> out.add(MdBlock.ListBlock(false, 1, items(node, anchors)))
            is OrderedList -> out.add(MdBlock.ListBlock(true, node.startNumber, items(node, anchors)))
            is ThematicBreak -> out.add(MdBlock.Rule)
            is HtmlBlock -> htmlBlock(node.literal, out)
            is TableBlock -> out.add(table(node))
            else -> {}
        }
    }

    private fun children(parent: Node, anchors: AnchorGen): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        var c: Node? = parent.firstChild
        while (c != null) {
            convert(c, anchors, out)
            c = c.next
        }
        return out
    }

    private fun items(list: Node, anchors: AnchorGen): List<Item> {
        val out = ArrayList<Item>()
        var c: Node? = list.firstChild
        while (c != null) {
            if (c is ListItem) {
                val marker = (c.firstChild as? TaskListItemMarker)
                    ?: ((c.firstChild as? Paragraph)?.firstChild as? TaskListItemMarker)
                out.add(Item(children(c, anchors), marker?.isChecked))
            }
            c = c.next
        }
        return out
    }

    private fun table(t: TableBlock): MdBlock.Table {
        var header: List<List<Inline>> = emptyList()
        val rows = ArrayList<List<List<Inline>>>()
        var section: Node? = t.firstChild
        while (section != null) {
            var row: Node? = section.firstChild
            while (row != null) {
                val cells = ArrayList<List<Inline>>()
                var cell: Node? = row.firstChild
                while (cell != null) {
                    cells.add(inlines(cell))
                    cell = cell.next
                }
                if (section is TableHead) header = cells else rows.add(cells)
                row = row.next
            }
            section = section.next
        }
        return MdBlock.Table(header, rows)
    }

    private fun inlines(parent: Node): List<Inline> {
        val out = ArrayList<Inline>()
        var n: Node? = parent.firstChild
        while (n != null) {
            inline(n, out)
            n = n.next
        }
        return out
    }

    private fun inline(n: Node, out: MutableList<Inline>) {
        when (n) {
            is Text -> out.add(Inline.Text(n.literal))
            is Code -> out.add(Inline.Code(n.literal))
            is StrongEmphasis -> out.add(Inline.Strong(inlines(n)))
            is Emphasis -> out.add(Inline.Emph(inlines(n)))
            is Strikethrough -> out.add(Inline.Strike(inlines(n)))
            is Link -> out.add(Inline.Link(n.destination, inlines(n)))
            is Image -> out.add(Inline.Img(n.destination, inlines(n).plain()))
            is SoftLineBreak -> out.add(Inline.Soft)
            is HardLineBreak -> out.add(Inline.Break)
            is HtmlInline -> imgTags(n.literal).forEach { out.add(it) }
            is TaskListItemMarker -> {}
            else -> out.addAll(inlines(n))
        }
    }

    // --- raw HTML: only <img> is understood; other tags are stripped, their text kept ---
    private val imgRe = Regex("<img\\s[^>]*>", RegexOption.IGNORE_CASE)
    private val srcRe = Regex("src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val altRe = Regex("alt\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)

    private fun imgTags(html: String): List<Inline.Img> = imgRe.findAll(html).mapNotNull { m ->
        val src = srcRe.find(m.value)?.groupValues?.get(1) ?: return@mapNotNull null
        Inline.Img(src, altRe.find(m.value)?.groupValues?.get(1).orEmpty())
    }.toList()

    private fun htmlBlock(literal: String, out: MutableList<MdBlock>) {
        val imgs = imgTags(literal)
        if (imgs.isNotEmpty()) out.add(MdBlock.Paragraph(imgs))
        val text = literal
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ").trim()
        if (text.isNotEmpty()) out.add(MdBlock.Paragraph(listOf(Inline.Text(text))))
    }

    /** GitHub-style slugs; duplicates become slug, slug-1, slug-2 in document order. */
    private class AnchorGen {
        private val seen = HashMap<String, Int>()
        fun next(title: String): String {
            val base = title.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
                .trim()
                .replace(Regex("\\s"), "-")
                .ifEmpty { "section" }
            val count = seen[base] ?: 0
            seen[base] = count + 1
            return if (count == 0) base else "$base-$count"
        }
    }
}
