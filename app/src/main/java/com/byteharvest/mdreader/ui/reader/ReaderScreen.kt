package com.byteharvest.mdreader.ui.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byteharvest.mdreader.core.markdown.TocEntry
import com.byteharvest.mdreader.data.local.LOCAL_REPOSITORY_ID
import com.byteharvest.mdreader.data.local.ThemeMode
import com.byteharvest.mdreader.domain.LinkResolver
import com.byteharvest.mdreader.domain.LinkTarget
import com.byteharvest.mdreader.domain.isMarkdownPath
import com.byteharvest.mdreader.ui.appViewModel
import com.byteharvest.mdreader.ui.components.ErrorBox
import com.byteharvest.mdreader.ui.components.LoadingBox
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val ReaderDarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF10213A),
    secondary = Color(0xFFD0D0D0),
    onSecondary = Color(0xFF202020),
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF181818),
    onSurface = Color(0xFFE7E2DC),
    onSurfaceVariant = Color(0xFFC5C2BC),
    outline = Color(0xFF77736D),
    outlineVariant = Color(0xFF494642),
    primaryContainer = Color(0xFF29394F),
    onPrimaryContainer = Color(0xFFD8E5FA),
    tertiaryContainer = Color(0xFF33483F),
    onTertiaryContainer = Color(0xFFD1E8D8),
)
private val ReaderLightColors = lightColorScheme(
    primary = Color(0xFF315FA5),
    onPrimary = Color.White,
    secondary = Color(0xFF171717),
    onSecondary = Color.White,
    background = Color(0xFFFAF9F6),
    surface = Color(0xFFFAF9F6),
    surfaceVariant = Color(0xFFF0EFEC),
    onSurface = Color(0xFF292824),
    onSurfaceVariant = Color(0xFF5C5A55),
    outline = Color(0xFF77736D),
    outlineVariant = Color(0xFFD5D2CC),
    primaryContainer = Color(0xFFE5ECF7),
    onPrimaryContainer = Color(0xFF202D42),
    tertiaryContainer = Color(0xFFE5EEE8),
    onTertiaryContainer = Color(0xFF263D30),
)

private fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()

private fun openUrl(c: Context, url: String) {
    val u = Uri.parse(url)
    if (u.scheme?.lowercase() !in setOf("http", "https", "mailto")) { toast(c, "Unsupported link"); return }
    try { c.startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: ActivityNotFoundException) { toast(c, "No app can open this link") }
}

@Composable
private fun DocumentScrubber(
    listState: LazyListState,
    toc: List<TocEntry>,
    modifier: Modifier = Modifier,
) {
    val layoutInfo by remember { derivedStateOf { listState.layoutInfo } }
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val visibleItemsState = rememberUpdatedState(visibleItems)
    val firstVisible = listState.firstVisibleItemIndex
    val isScrolling = listState.isScrollInProgress
    val scope = rememberCoroutineScope()
    var isInteracting by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        label = "documentScrubberAlpha",
    )
    val density = LocalDensity.current

    LaunchedEffect(isScrolling, isInteracting) {
        if (isScrolling || isInteracting) {
            isVisible = true
            if (!isInteracting) {
                delay(900)
                if (!listState.isScrollInProgress) isVisible = false
            }
        } else {
            delay(900)
            if (!isInteracting) isVisible = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .width(36.dp)
            .fillMaxHeight()
            .alpha(alpha)
            .pointerInput(totalItems) {
                val trackPadding = 24.dp.toPx()
                val trackHeight = (size.height - trackPadding * 2).coerceAtLeast(1f)
                var dragY = 0f
                var scrollJob: Job? = null

                fun jumpTo(y: Float) {
                    val maxIndex = (totalItems - visibleItemsState.value).coerceAtLeast(0)
                    if (maxIndex == 0) return
                    val fraction = ((y - trackPadding) / trackHeight).coerceIn(0f, 1f)
                    scrollJob?.cancel()
                    scrollJob = scope.launch { listState.scrollToItem((fraction * maxIndex).toInt()) }
                }

                detectDragGestures(
                    onDragStart = { start ->
                        isInteracting = true
                        isVisible = true
                        dragY = start.y
                        jumpTo(dragY)
                    },
                    onDragEnd = { isInteracting = false },
                    onDragCancel = { isInteracting = false },
                ) { change, amount ->
                    change.consume()
                    dragY = (dragY + amount.y).coerceIn(trackPadding, trackPadding + trackHeight)
                    jumpTo(dragY)
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        val trackPaddingPx = with(density) { 24.dp.toPx() }
        val trackHeightPx = (with(density) { maxHeight.toPx() } - trackPaddingPx * 2).coerceAtLeast(1f)
        val safeTrackHeightPx = trackHeightPx.coerceAtLeast(1f)
        val minThumbHeightPx = with(density) { 36.dp.toPx() }.coerceAtMost(safeTrackHeightPx)
        val thumbHeightPx = (safeTrackHeightPx * visibleItems / totalItems.coerceAtLeast(1))
            .coerceIn(minThumbHeightPx, safeTrackHeightPx)
        val maxIndex = (totalItems - visibleItems).coerceAtLeast(0)
        val progress = if (maxIndex == 0) 0f else firstVisible.toFloat() / maxIndex
        val thumbTopPx = trackPaddingPx + (trackHeightPx - thumbHeightPx) * progress
        val thumbTopDp = with(density) { thumbTopPx.toDp() }
        val currentHeading = toc.lastOrNull { it.blockIndex <= firstVisible }?.title
        val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

        Canvas(
            Modifier.fillMaxSize().semantics {
                contentDescription = "Document position"
                stateDescription = "${(progress * 100).toInt()} percent"
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            },
        ) {
            val trackX = size.width - 8.dp.toPx()
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackX, trackPaddingPx),
                size = Size(3.dp.toPx(), trackHeightPx),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(trackX - 0.5.dp.toPx(), thumbTopPx),
                size = Size(4.dp.toPx(), thumbHeightPx),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }

        if (isInteracting) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 36.dp)
                    .offset { IntOffset(0, thumbTopDp.roundToPx().coerceAtMost((maxHeight - 48.dp).roundToPx())) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp,
            ) {
                Text(
                    currentHeading ?: "${(progress * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(onBack: () -> Unit, onOpenDoc: (String, String) -> Unit) {
    val vm = appViewModel { c, h -> ReaderViewModel(h, c) }
    val appTheme by vm.appTheme.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appTheme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (useDark) ReaderDarkColors else ReaderLightColors) {
        ReaderContent(vm, onBack, onOpenDoc)
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun ReaderContent(vm: ReaderViewModel, onBack: () -> Unit, onOpenDoc: (String, String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val fontSp by vm.fontSp.collectAsStateWithLifecycle()
    val renderedTextSelectionEnabled by vm.renderedTextSelectionEnabled.collectAsStateWithLifecycle()
    val bookmarked by vm.bookmarked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var positionRestored by remember(vm.repoId, vm.path) { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showRawMarkdown by rememberSaveable { mutableStateOf(false) }
    var showToc by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch { toast(context, vm.exportTo(uri)) }
    }

    // Jump to the current match. Clearing the search (current = -1) deliberately does not scroll.
    LaunchedEffect(search.current, search.query) {
        if (search.current in search.matches.indices) listState.animateScrollToItem(search.matches[search.current])
    }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    val doc = state.doc
    LaunchedEffect(doc, positionRestored) {
        val blockCount = doc?.blocks?.size ?: return@LaunchedEffect
        if (!positionRestored) {
            val saved = vm.readingPosition()
            if (blockCount > 0) listState.scrollToItem(saved.index.coerceAtMost(blockCount - 1), saved.offset)
            positionRestored = true
        }
    }
    LaunchedEffect(positionRestored, listState) {
        if (!positionRestored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(500)
            .collect { (index, offset) -> vm.saveReadingPosition(index, offset) }
    }

    val activeBlock = search.matches.getOrNull(search.current) ?: -1
    val ctx = remember(doc, search.query, activeBlock, fontSp) {
        RenderCtx(
            baseSp = fontSp,
            query = search.query,
            activeBlock = activeBlock,
            image = vm::imageSpec,
            onLink = { dest ->
                when (val t = LinkResolver.resolve(vm.path, dest)) {
                    is LinkTarget.Anchor -> {
                        val e = doc?.toc?.firstOrNull { it.anchor == t.id }
                        if (e != null) scope.launch { listState.animateScrollToItem(e.blockIndex) } else toast(context, "Section not found")
                    }
                    is LinkTarget.External -> openUrl(context, t.url)
                    is LinkTarget.RepoFile -> scope.launch {
                        if (isMarkdownPath(t.path)) {
                            if (vm.exists(t.path)) onOpenDoc(vm.repoId, t.path) else toast(context, "File not found in this repository")
                        } else openUrl(context, vm.githubUrl(t.path))
                    }
                    LinkTarget.Invalid -> toast(context, "Unsupported link")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it; vm.setQuery(it) },
                            singleLine = true,
                            placeholder = { Text("Search in document") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.focusRequester(focus),
                        )
                    },
                    navigationIcon = {
                        IconButton({ searching = false; query = ""; vm.setQuery("") }) { Icon(Icons.Filled.Close, "Close search") }
                    },
                    actions = {
                        if (search.query.isNotEmpty()) {
                            Text(
                                if (search.matches.isEmpty()) "No results" else "${search.current + 1}/${search.matches.size}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        IconButton(vm::prevMatch, enabled = search.matches.isNotEmpty()) { Icon(Icons.Filled.KeyboardArrowUp, "Previous match") }
                        IconButton(vm::nextMatch, enabled = search.matches.isNotEmpty()) { Icon(Icons.Filled.KeyboardArrowDown, "Next match") }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null)
                            Text(vm.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    actions = {
                        IconButton({ searching = true }, enabled = doc != null) { Icon(Icons.Filled.Search, "Search in document") }
                        IconButton({ showToc = true }, enabled = doc != null) { Icon(Icons.Filled.Toc, "Table of contents") }
                        IconButton(vm::toggleBookmark) {
                            Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (bookmarked) "Remove bookmark" else "Add bookmark")
                        }
                        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (showRawMarkdown) "View rendered Markdown" else "View raw Markdown") },
                                enabled = doc != null,
                                onClick = { menu = false; showRawMarkdown = !showRawMarkdown },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy document") },
                                enabled = state.rawMarkdown != null,
                                onClick = {
                                    menu = false
                                    state.rawMarkdown?.let {
                                        clipboard.setText(AnnotatedString(it))
                                        toast(context, "Markdown copied")
                                    }
                                },
                            )
                            DropdownMenuItem(text = { Text("Larger text") }, onClick = { vm.changeFont(+2f) })
                            DropdownMenuItem(text = { Text("Smaller text") }, onClick = { vm.changeFont(-2f) })
                            if (vm.repoId != LOCAL_REPOSITORY_ID) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = { menu = false; vm.load(true) },
                                )
                            }
                            DropdownMenuItem(text = { Text("Save as PDF") }, enabled = doc != null, onClick = {
                                menu = false
                                saveLauncher.launch(vm.title.substringBeforeLast('.') + ".pdf")
                            })
                            DropdownMenuItem(text = { Text("Share PDF") }, enabled = doc != null, onClick = {
                                menu = false
                                scope.launch {
                                    val f = vm.exportToCache()
                                    if (f == null) { toast(context, "PDF export failed."); return@launch }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Share PDF"))
                                }
                            })
                            if (vm.repoId != LOCAL_REPOSITORY_ID) {
                                DropdownMenuItem(
                                    text = { Text("Open on GitHub") },
                                    onClick = { menu = false; openUrl(context, vm.githubUrl(vm.path)) },
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                state.loading && doc == null -> LoadingBox()
                state.error != null -> ErrorBox(state.error!!, onRetry = { vm.load(true) })
                doc != null && showRawMarkdown && state.rawMarkdown != null -> SelectionContainer {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
                    ) {
                        LazyColumn(
                            Modifier.widthIn(max = 800.dp).fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                        ) {
                            item {
                                Text(
                                    state.rawMarkdown.orEmpty(),
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                    ),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                }
                doc != null -> Box(Modifier.fillMaxSize()) {
                    Column {
                        val readerContent: @Composable () -> Unit = {
                            Column {
                                state.notice?.let {
                                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                                        Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                MarkdownBlocks(doc, listState, ctx)
                            }
                        }
                        if (renderedTextSelectionEnabled) {
                            SelectionContainer { readerContent() }
                        } else {
                            readerContent()
                        }
                    }
                    if (doc.blocks.size > 1) {
                        DocumentScrubber(
                            listState = listState,
                            toc = doc.toc,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
    }

    if (showToc) {
        ModalBottomSheet(onDismissRequest = { showToc = false }) {
            val toc = doc?.toc.orEmpty()
            if (toc.isEmpty()) {
                Text("No headings in this document.", Modifier.padding(24.dp))
            } else {
                LazyColumn {
                    items(toc) { e ->
                        Text(
                            e.title,
                            style = if (e.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { showToc = false; scope.launch { listState.scrollToItem(e.blockIndex) } }
                                .padding(start = (16 + (e.level - 1) * 16).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
