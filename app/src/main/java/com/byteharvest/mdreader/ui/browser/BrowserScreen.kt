package com.byteharvest.mdreader.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.data.local.RepositoryEntity
import com.byteharvest.mdreader.domain.filterPaths
import com.byteharvest.mdreader.domain.listDirectory
import com.byteharvest.mdreader.data.repository.SyncReport
import com.byteharvest.mdreader.ui.appViewModel
import com.byteharvest.mdreader.ui.components.EmptyState
import com.byteharvest.mdreader.ui.components.LoadingBox
import com.byteharvest.mdreader.ui.navArg
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun syncMessage(r: SyncReport): String = when {
    r.error != null -> "Refresh failed: ${r.error} Showing saved copy."
    r.failedDocs > 0 -> "Refreshed with problems: ${r.updatedDocs} updated, ${r.failedDocs} could not be updated (old copies kept)."
    r.truncated -> "Updated, but this repository is very large and the file list may be incomplete."
    r.updatedDocs == 0 && r.removedFiles == 0 -> "Already up to date."
    else -> "Updated ${r.updatedDocs} document(s), ${r.removedFiles} removed."
}

class BrowserViewModel(handle: SavedStateHandle, private val c: AppContainer) : ViewModel() {
    val repoId = navArg(handle, "repoId")
    val dir = navArg(handle, "dir")
    val repo: StateFlow<RepositoryEntity?> =
        c.repository.repoFlow(repoId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val paths: StateFlow<List<String>?> =
        c.repository.treePaths(repoId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val branches = MutableStateFlow<List<String>?>(null)
    val navigateTo = Channel<String>(Channel.BUFFERED)

    init { if (dir.isEmpty()) viewModelScope.launch { c.repository.touch(repoId) } }

    fun refresh() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            messages.tryEmit(syncMessage(c.repository.refresh(repoId)))
            _busy.value = false
        }
    }

    fun loadBranches() {
        viewModelScope.launch {
            c.repository.branches(repoId).fold(
                { branches.value = it },
                { messages.tryEmit(it.message ?: "Could not load branches.") },
            )
        }
    }

    fun dismissBranches() { branches.value = null }

    fun switchBranch(branch: String) {
        val r = repo.value ?: return
        branches.value = null
        viewModelScope.launch {
            _busy.value = true
            c.repository.openRepository(r.owner, r.name, branch).fold(
                { navigateTo.trySend(it) },
                { messages.tryEmit(it.message ?: "Could not open branch.") },
            )
            _busy.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onOpenDir: (String, String) -> Unit,
    onOpenDoc: (String, String) -> Unit,
    onOpenRepo: (String) -> Unit,
) {
    val vm = appViewModel { c, h -> BrowserViewModel(h, c) }
    val repo by vm.repo.collectAsStateWithLifecycle()
    val paths by vm.paths.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val branches by vm.branches.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.navigateTo.receiveAsFlow().collect { onOpenRepo(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (vm.dir.isEmpty()) repo?.let { "${it.owner}/${it.name}" } ?: "Repository" else vm.dir.substringAfterLast('/'), maxLines = 1)
                        repo?.let { Text("Branch: ${it.branch}", style = MaterialTheme.typography.labelSmall) }
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton({ searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Filter files")
                    }
                    if (busy) CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(24.dp))
                    else IconButton(vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Switch branch") }, onClick = { menu = false; vm.loadBranches() })
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (searching) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, label = { Text("Filter Markdown files") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val all = paths
            when {
                all == null -> LoadingBox()
                searching && query.isNotBlank() -> {
                    val results = remember(all, query) { filterPaths(all, query) }
                    if (results.isEmpty()) EmptyState(Icons.Filled.Inbox, "No matches", "No Markdown file matches \"$query\".", Modifier.fillMaxWidth())
                    else LazyColumn { items(results, key = { it }) { p -> FileRow(p.substringAfterLast('/'), p) { onOpenDoc(vm.repoId, p) } } }
                }
                else -> {
                    val listing = remember(all, vm.dir) { listDirectory(all, vm.dir) }
                    if (listing.folders.isEmpty() && listing.files.isEmpty()) {
                        EmptyState(Icons.Filled.Inbox, "No Markdown files", "This ${if (vm.dir.isEmpty()) "repository" else "folder"} has no .md or .markdown files.", Modifier.fillMaxWidth())
                    } else {
                        LazyColumn {
                            if (vm.dir.isEmpty()) repo?.description?.takeIf { it.isNotBlank() }?.let { d ->
                                item { Text(d, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            items(listing.folders, key = { "d/$it" }) { f ->
                                ListItem(
                                    headlineContent = { Text(f) },
                                    leadingContent = { Icon(Icons.Filled.Folder, null) },
                                    modifier = Modifier.clickable { onOpenDir(vm.repoId, if (vm.dir.isEmpty()) f else "${vm.dir}/$f") },
                                )
                            }
                            items(listing.files, key = { "f/$it" }) { f ->
                                val full = if (vm.dir.isEmpty()) f else "${vm.dir}/$f"
                                FileRow(f, null) { onOpenDoc(vm.repoId, full) }
                            }
                        }
                    }
                }
            }
        }
    }

    branches?.let { list ->
        AlertDialog(
            onDismissRequest = vm::dismissBranches,
            title = { Text("Switch branch") },
            text = {
                LazyColumn {
                    items(list) { b ->
                        Text(
                            b + if (b == repo?.branch) "  (current)" else "",
                            Modifier.fillMaxWidth().clickable { vm.switchBranch(b) }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(vm::dismissBranches) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FileRow(name: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(Icons.Filled.Description, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
