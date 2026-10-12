package com.byteharvest.mdreader.ui.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byteharvest.mdreader.domain.LinkResolver
import com.byteharvest.mdreader.domain.LinkTarget
import com.byteharvest.mdreader.domain.isMarkdownPath
import com.byteharvest.mdreader.ui.appViewModel
import com.byteharvest.mdreader.ui.components.ErrorBox
import com.byteharvest.mdreader.ui.components.LoadingBox
import kotlinx.coroutines.launch

private fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()

private fun openUrl(c: Context, url: String) {
    val u = Uri.parse(url)
    if (u.scheme?.lowercase() !in setOf("http", "https", "mailto")) { toast(c, "Unsupported link"); return }
    try { c.startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: ActivityNotFoundException) { toast(c, "No app can open this link") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(onBack: () -> Unit, onOpenDoc: (String, String) -> Unit) {
    val vm = appViewModel { c, h -> ReaderViewModel(h, c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val fontSp by vm.fontSp.collectAsStateWithLifecycle()
    val bookmarked by vm.bookmarked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
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
                    title = { Text(vm.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton({ searching = true }, enabled = doc != null) { Icon(Icons.Filled.Search, "Search in document") }
                        IconButton({ showToc = true }, enabled = doc != null) { Icon(Icons.Filled.Toc, "Table of contents") }
                        IconButton(vm::toggleBookmark) {
                            Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (bookmarked) "Remove bookmark" else "Add bookmark")
                        }
                        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Larger text") }, onClick = { vm.changeFont(+2f) })
                            DropdownMenuItem(text = { Text("Smaller text") }, onClick = { vm.changeFont(-2f) })
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; vm.load(true) })
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
                            DropdownMenuItem(text = { Text("Open on GitHub") }, onClick = { menu = false; openUrl(context, vm.githubUrl(vm.path)) })
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
                doc != null -> Column {
                    state.notice?.let {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    MarkdownBlocks(doc, listState, ctx)
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
