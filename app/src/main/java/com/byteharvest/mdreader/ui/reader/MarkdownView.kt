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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import android.widget.Toast
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

private class Palette(
    val link: Color,
    val codeBg: Color,
    val hl: Color,
    val hlActive: Color,
    val outline: Color,
    val codeText: Color,
    val codeKeyword: Color,
    val codeType: Color,
    val codeString: Color,
    val codeFunction: Color,
    val codeNumber: Color,
    val codeComment: Color,
)

@Composable
private fun palette(): Palette {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    return Palette(
        link = cs.primary,
        codeBg = cs.surfaceVariant,
        hl = Color(0x66FFEB3B),
        hlActive = Color(0xAAFF9800),
        outline = cs.outlineVariant,
        codeText = if (dark) Color(0xFFE7E2DC) else Color(0xFF292824),
        codeKeyword = if (dark) Color(0xFFE7A6D8) else Color(0xFF9A2875),
        codeType = if (dark) Color(0xFFD4AEF5) else Color(0xFF7041A6),
        codeString = if (dark) Color(0xFFA8D7A0) else Color(0xFF237A45),
        codeFunction = if (dark) Color(0xFF9CD6ED) else Color(0xFF246B8C),
        codeNumber = if (dark) Color(0xFFE3BD84) else Color(0xFF9A5A10),
        codeComment = if (dark) Color(0xFF9A9A94) else Color(0xFF77736D),
    )
}

/** One LazyColumn item per top-level block, so long documents stay smooth. */
@Composable
fun MarkdownBlocks(doc: MarkdownDoc, listState: LazyListState, ctx: RenderCtx, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 800.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(doc.blocks) { index, block -> BlockView(block, ctx, index == ctx.activeBlock) }
        }
    }
}

@Composable
private fun bodyStyle(ctx: RenderCtx, scale: Float = 1f): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Serif,
        fontSize = (ctx.baseSp * scale).sp,
        lineHeight = (ctx.baseSp * scale * 1.7f).sp,
        color = MaterialTheme.colorScheme.onSurface,
    )

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
        is MdBlock.Code -> {
            val clipboard = LocalClipboardManager.current
            val context = LocalContext.current
            Surface(
                color = p.codeBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            b.lang?.uppercase() ?: "CODE",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(b.text))
                                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.width(14.dp))
                            Text("Copy", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        val text = remember(b, ctx.query, active) { buildAnnotatedString { appendCode(b.text, ctx.query, active, p) } }
                        Text(
                            text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (ctx.baseSp * 0.85f).sp,
                            lineHeight = (ctx.baseSp * 1.25f).sp,
                            softWrap = false,
                            color = p.codeText,
                        )
                    }
                }
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

private val codeTokenRegex = Regex(
    """(?<comment>//[^\n]*|#[^\n]*|/\*[\s\S]*?\*/)|(?<string>"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)|(?<keyword>\b(?:abstract|as|assert|async|await|break|case|catch|class|const|continue|def|default|do|else|enum|extends|final|finally|for|fun|function|if|implements|import|in|interface|let|new|null|object|package|private|protected|public|return|static|super|switch|this|throw|try|val|var|void|while|yield|true|false|None|self|undefined)\b)|(?<number>\b\d+(?:\.\d+)?\b)|(?<identifier>\b[A-Za-z_$][A-Za-z0-9_$]*\b)""",
)
private val codeTypePrefixRegex = Regex("""(?:class|interface|enum|object|extends|implements|new)\s+$""")

private fun AnnotatedString.Builder.appendCode(source: String, query: String, active: Boolean, p: Palette) {
    var cursor = 0
    for (match in codeTokenRegex.findAll(source)) {
        if (match.range.first > cursor) appendHighlighted(source.substring(cursor, match.range.first), query, active, p)
        val token = match.value
        val color = when {
            match.groups["comment"] != null -> p.codeComment
            match.groups["string"] != null -> p.codeString
            match.groups["keyword"] != null -> p.codeKeyword
            match.groups["number"] != null -> p.codeNumber
            match.groups["identifier"] != null &&
                codeTypePrefixRegex.containsMatchIn(source.substring(0, match.range.first).trimEnd()) -> p.codeType
            match.groups["identifier"] != null &&
                source.substring(match.range.last + 1).trimStart().startsWith("(") -> p.codeFunction
            else -> p.codeText
        }
        withStyle(SpanStyle(color = color)) { appendHighlighted(token, query, active, p) }
        cursor = match.range.last + 1
    }
    if (cursor < source.length) appendHighlighted(source.substring(cursor), query, active, p)
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
