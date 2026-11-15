package com.byteharvest.mdreader.core.markdown

sealed interface Inline {
    data class Text(val s: String) : Inline
    data class Code(val s: String) : Inline
    data class Strong(val c: List<Inline>) : Inline
    data class Emph(val c: List<Inline>) : Inline
    data class Strike(val c: List<Inline>) : Inline
    data class Link(val dest: String, val c: List<Inline>) : Inline
    data class Img(val src: String, val alt: String) : Inline
    data object Break : Inline
    data object Soft : Inline
}

data class Item(val blocks: List<MdBlock>, val checked: Boolean?)

sealed interface MdBlock {
    data class Heading(val level: Int, val inlines: List<Inline>, val anchor: String) : MdBlock
    data class Paragraph(val inlines: List<Inline>) : MdBlock
    data class Code(val lang: String?, val text: String) : MdBlock
    data class Quote(val children: List<MdBlock>) : MdBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<Item>) : MdBlock
    data class Table(val header: List<List<Inline>>, val rows: List<List<List<Inline>>>) : MdBlock
    data object Rule : MdBlock
}

data class TocEntry(val level: Int, val title: String, val anchor: String, val blockIndex: Int)
data class MarkdownDoc(val blocks: List<MdBlock>, val toc: List<TocEntry>)

/** Rendering/parsing is hidden behind this interface so the library can be swapped later. */
interface MarkdownEngine {
    fun parse(source: String): MarkdownDoc
}

fun List<Inline>.plain(): String = buildString { for (i in this@plain) append(i.plain()) }

fun Inline.plain(): String = when (this) {
    is Inline.Text -> s
    is Inline.Code -> s
    is Inline.Strong -> c.plain()
    is Inline.Emph -> c.plain()
    is Inline.Strike -> c.plain()
    is Inline.Link -> c.plain()
    is Inline.Img -> ""
    Inline.Break, Inline.Soft -> " "
}

private fun List<Inline>.runs(out: MutableList<String>) { for (i in this) i.runs(out) }

private fun Inline.runs(out: MutableList<String>) {
    when (this) {
        is Inline.Text -> out.add(s)
        is Inline.Code -> out.add(s)
        is Inline.Strong -> c.runs(out)
        is Inline.Emph -> c.runs(out)
        is Inline.Strike -> c.runs(out)
        is Inline.Link -> c.runs(out)
        else -> {}
    }
}

/** Text runs exactly as the renderer highlights them; used so match counts agree with what is drawn. */
fun MdBlock.searchRuns(): List<String> {
    val out = ArrayList<String>()
    collectRuns(this, out)
    return out
}

private fun collectRuns(b: MdBlock, out: MutableList<String>) {
    when (b) {
        is MdBlock.Heading -> b.inlines.runs(out)
        is MdBlock.Paragraph -> b.inlines.runs(out)
        is MdBlock.Code -> out.add(b.text)
        is MdBlock.Quote -> b.children.forEach { collectRuns(it, out) }
        is MdBlock.ListBlock -> b.items.forEach { item -> item.blocks.forEach { collectRuns(it, out) } }
        is MdBlock.Table -> (b.header + b.rows.flatten()).forEach { it.runs(out) }
        MdBlock.Rule -> {}
    }
}

fun countMatches(text: String, query: String): Int {
    if (query.isEmpty()) return 0
    var n = 0
    var i = text.indexOf(query, 0, true)
    while (i >= 0) {
        n++
        i = text.indexOf(query, i + query.length, true)
    }
    return n
}
