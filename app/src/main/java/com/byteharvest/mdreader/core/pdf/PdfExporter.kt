package com.byteharvest.mdreader.core.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.byteharvest.mdreader.core.markdown.Inline
import com.byteharvest.mdreader.core.markdown.MarkdownDoc
import com.byteharvest.mdreader.core.markdown.MdBlock
import com.byteharvest.mdreader.core.markdown.plain
import java.io.OutputStream

/**
 * Local PDF generation with android.graphics.pdf (no network, no backend). A4 pages, paginated line by line.
 * Limitations: inline bold/italic/links lose styling, images become "[image: alt]", tables become text rows.
 */
object PdfExporter {
    private const val W = 595
    private const val H = 842
    private const val M = 48

    private fun text(list: List<Inline>): String = buildString {
        for (i in list) when (i) {
            is Inline.Img -> append("[image: ${i.alt.ifBlank { i.src.substringAfterLast('/') }}]")
            is Inline.Break -> append('\n')
            is Inline.Strong -> append(text(i.c))
            is Inline.Emph -> append(text(i.c))
            is Inline.Strike -> append(text(i.c))
            is Inline.Link -> append(text(i.c))
            else -> append(i.plain())
        }
    }

    fun render(doc: MarkdownDoc, out: OutputStream) {
        val pdf = PdfDocument()
        try {
            var pageNo = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            var canvas = page.canvas
            var y = M.toFloat()
            val contentWidth = W - 2 * M

            fun newPage() {
                pdf.finishPage(page)
                pageNo++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                canvas = page.canvas
                y = M.toFloat()
            }

            fun draw(s: String, size: Float, bold: Boolean, mono: Boolean, indent: Int, gap: Float) {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size
                    color = Color.BLACK
                    typeface = when { mono -> Typeface.MONOSPACE; bold -> Typeface.DEFAULT_BOLD; else -> Typeface.DEFAULT }
                }
                val width = (contentWidth - indent).coerceAtLeast(100)
                val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, width).setLineSpacing(0f, 1.2f).build()
                for (i in 0 until layout.lineCount) {
                    val top = layout.getLineTop(i)
                    val bottom = layout.getLineBottom(i)
                    if (y + (bottom - top) > H - M) newPage()
                    canvas.save()
                    canvas.translate((M + indent).toFloat(), y - top)
                    canvas.clipRect(0f, top.toFloat(), width.toFloat(), bottom.toFloat())
                    layout.draw(canvas)
                    canvas.restore()
                    y += (bottom - top)
                }
                y += gap
            }

            fun emit(b: MdBlock, indent: Int) {
                when (b) {
                    is MdBlock.Heading -> draw(text(b.inlines), floatArrayOf(22f, 19f, 16f, 14f, 13f, 12f)[(b.level - 1).coerceIn(0, 5)], true, false, indent, 8f)
                    is MdBlock.Paragraph -> draw(text(b.inlines), 11f, false, false, indent, 8f)
                    is MdBlock.Code -> draw(b.text, 9f, false, true, indent, 8f)
                    is MdBlock.Quote -> b.children.forEach { emit(it, indent + 14) }
                    is MdBlock.ListBlock -> b.items.forEachIndexed { idx, item ->
                        val prefix = when {
                            item.checked != null -> if (item.checked) "[x] " else "[ ] "
                            b.ordered -> "${b.start + idx}. "
                            else -> "\u2022 "
                        }
                        val first = item.blocks.firstOrNull()
                        if (first is MdBlock.Paragraph) draw(prefix + text(first.inlines), 11f, false, false, indent, 4f)
                        else draw(prefix.trim(), 11f, false, false, indent, 4f)
                        item.blocks.drop(if (first is MdBlock.Paragraph) 1 else 0).forEach { emit(it, indent + 14) }
                    }
                    is MdBlock.Table -> {
                        if (b.header.isNotEmpty()) draw(b.header.joinToString("  |  ") { text(it) }, 10f, true, false, indent, 2f)
                        b.rows.forEach { r -> draw(r.joinToString("  |  ") { text(it) }, 10f, false, false, indent, 2f) }
                        y += 6f
                    }
                    MdBlock.Rule -> {
                        if (y + 12 > H - M) newPage()
                        val paint = Paint().apply { color = Color.GRAY; strokeWidth = 0.8f }
                        canvas.drawLine(M.toFloat(), y + 4, (W - M).toFloat(), y + 4, paint)
                        y += 12f
                    }
                }
            }

            doc.blocks.forEach { emit(it, 0) }
            pdf.finishPage(page)
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }
}
