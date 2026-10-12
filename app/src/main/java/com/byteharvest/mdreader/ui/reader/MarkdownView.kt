package com.byteharvest.mdreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.byteharvest.mdreader.core.markdown.Inline
import com.byteharvest.mdreader.core.markdown.MarkdownDoc
import com.byteharvest.mdreader.core.markdown.MdBlock
import com.byteharvest.mdreader.core.markdown.plain

class ImageSpec(val url: String, val headers: Map<String, String>, val cache: Boolean)

class RenderCtx(
    val baseSp: Float,
    val query: String,
    val activeBlock: Int,
    val onLink: (String) -> Unit,
    val image: (String) -> ImageSpec?,
)

private class Palette(val link: Color, val codeBg: Color, val hl: Color, val hlActive: Color, val outline: Color)

@Composable
private fun palette(): Palette {
    val cs = MaterialTheme.colorScheme
    return Palette(cs.primary, cs.surfaceVariant, Color(0x66FFEB3B), Color(0xAAFF9800), cs.outlineVariant)
}

/** One LazyColumn item per top-level block, so long documents stay smooth. */
@Composable
fun MarkdownBlocks(doc: MarkdownDoc, listState: LazyListState, ctx: RenderCtx, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(doc.blocks) { index, block -> BlockView(block, ctx, index == ctx.activeBlock) }
        }
    }
}

@Composable
private fun bodyStyle(ctx: RenderCtx, scale: Float = 1f): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(fontSize = (ctx.baseSp * scale).sp, lineHeight = (ctx.baseSp * scale * 1.6f).sp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockView(b: MdBlock, ctx: RenderCtx, active: Boolean) {
    val p = palette()
    when (b) {
        is MdBlock.Heading -> Column(Modifier.padding(top = if (b.level <= 2) 8.dp else 4.dp)) {
            val scale = floatArrayOf(2f, 1.6f, 1.35f, 1.2f, 1.1f, 1f)[(b.level - 1).coerceIn(0, 5)]
            InlineText(
                b.inlines, ctx, active,
                bodyStyle(ctx, scale).copy(fontWeight = FontWeight.Bold, lineHeight = (ctx.baseSp * scale * 1.3f).sp),
            )
            if (b.level <= 2) HorizontalDivider(Modifier.padding(top = 6.dp))
        }
        is MdBlock.Paragraph -> {
            val imgs = remember(b) { ArrayList<Pair<Inline.Img, String?>>().also { collectImages(b.inlines, null, it) } }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (b.inlines.plain().isNotBlank()) InlineText(b.inlines, ctx, active, bodyStyle(ctx))
                if (imgs.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        imgs.forEach { (img, link) -> MdImage(img, link, ctx) }
                    }
                }
            }
        }
        is MdBlock.Code -> Surface(color = p.codeBg, shape = RoundedCornerShape(8.dp)) {
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                val text = remember(b, ctx.query, active) { buildAnnotatedString { appendHighlighted(b.text, ctx.query, active, p) } }
                Text(text, fontFamily = FontFamily.Monospace, fontSize = (ctx.baseSp * 0.85f).sp, softWrap = false)
            }
        }
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(p.outline))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { b.children.forEach { BlockView(it, ctx, active) } }
        }
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.items.forEachIndexed { idx, item ->
                Row {
                    val marker = when {
                        item.checked != null -> if (item.checked) "\u2611" else "\u2610"
                        b.ordered -> "${b.start + idx}."
                        else -> "\u2022"
                    }
                    Text(marker, style = bodyStyle(ctx), modifier = Modifier.width(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { item.blocks.forEach { BlockView(it, ctx, active) } }
                }
            }
        }
        is MdBlock.Table -> {
            val cols = maxOf(b.header.size, b.rows.maxOfOrNull { it.size } ?: 0)
            Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, p.outline, RoundedCornerShape(6.dp))) {
                if (b.header.isNotEmpty()) TableRowView(b.header, cols, true, ctx, active, p)
                b.rows.forEach { TableRowView(it, cols, false, ctx, active, p) }
            }
        }
        MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun TableRowView(cells: List<List<Inline>>, cols: Int, header: Boolean, ctx: RenderCtx, active: Boolean, p: Palette) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        for (i in 0 until cols) {
            Box(
                Modifier.width(160.dp).fillMaxHeight().border(0.5.dp, p.outline)
                    .background(if (header) p.codeBg else Color.Transparent).padding(8.dp)
            ) {
                InlineText(cells.getOrElse(i) { emptyList() }, ctx, active, bodyStyle(ctx, 0.9f), bold = header)
            }
        }
    }
}

@Composable
private fun InlineText(inlines: List<Inline>, ctx: RenderCtx, active: Boolean, style: TextStyle, modifier: Modifier = Modifier, bold: Boolean = false) {
    val p = palette()
    val text = remember(inlines, ctx, active, p.link) { buildAnnotatedString { appendInlines(inlines, ctx, active, p) } }
    Text(text, style = if (bold) style.copy(fontWeight = FontWeight.Bold) else style, modifier = modifier)
}

private fun collectImages(list: List<Inline>, link: String?, out: MutableList<Pair<Inline.Img, String?>>) {
    for (i in list) when (i) {
        is Inline.Img -> out.add(i to link)
        is Inline.Link -> collectImages(i.c, i.dest, out)
        is Inline.Strong -> collectImages(i.c, link, out)
        is Inline.Emph -> collectImages(i.c, link, out)
        is Inline.Strike -> collectImages(i.c, link, out)
        else -> {}
    }
}

private fun AnnotatedString.Builder.appendHighlighted(s: String, q: String, active: Boolean, p: Palette) {
    if (q.isEmpty()) { append(s); return }
    var start = 0
    while (true) {
        val idx = s.indexOf(q, start, ignoreCase = true)
        if (idx < 0) { append(s.substring(start)); break }
        append(s.substring(start, idx))
        withStyle(SpanStyle(background = if (active) p.hlActive else p.hl)) { append(s.substring(idx, idx + q.length)) }
        start = idx + q.length
    }
}

private fun AnnotatedString.Builder.appendInlines(list: List<Inline>, ctx: RenderCtx, active: Boolean, p: Palette) {
    for (i in list) when (i) {
        is Inline.Text -> appendHighlighted(i.s, ctx.query, active, p)
        is Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = p.codeBg)) {
            appendHighlighted(i.s, ctx.query, active, p)
        }
        is Inline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInlines(i.c, ctx, active, p) }
        is Inline.Emph -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(i.c, ctx, active, p) }
        is Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInlines(i.c, ctx, active, p) }
        is Inline.Link -> withLink(
            LinkAnnotation.Clickable(
                tag = i.dest,
                styles = TextLinkStyles(SpanStyle(color = p.link, textDecoration = TextDecoration.Underline)),
                linkInteractionListener = { ctx.onLink(i.dest) },
            )
        ) { appendInlines(i.c, ctx, active, p) }
        is Inline.Img -> {} // images are laid out separately (see Paragraph)
        Inline.Break -> append('\n')
        Inline.Soft -> append(' ')
    }
}

@Composable
private fun MdImage(img: Inline.Img, link: String?, ctx: RenderCtx) {
    val spec = ctx.image(img.src)
    if (spec == null) {
        Text("[image: ${img.alt.ifBlank { img.src }}]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        return
    }
    val context = LocalContext.current
    val req = remember(spec) {
        ImageRequest.Builder(context).data(spec.url).apply {
            spec.headers.forEach { (k, v) -> addHeader(k, v) }
            if (!spec.cache) { // private repository content is never written to disk/memory caches
                diskCachePolicy(CachePolicy.DISABLED)
                memoryCachePolicy(CachePolicy.DISABLED)
            }
            crossfade(true)
        }.build()
    }
    AsyncImage(
        model = req,
        contentDescription = img.alt.ifBlank { null },
        contentScale = ContentScale.Fit,
        modifier = Modifier.clickable(enabled = link != null) { link?.let(ctx.onLink) },
    )
}
