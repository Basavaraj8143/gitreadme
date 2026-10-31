package com.byteharvest.mdreader.ui.repositories

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.data.local.RepositoryEntity
import com.byteharvest.mdreader.ui.appViewModel
import com.byteharvest.mdreader.ui.browser.syncMessage
import com.byteharvest.mdreader.ui.components.DocListItem
import com.byteharvest.mdreader.ui.components.EmptyState
import com.byteharvest.mdreader.ui.components.LoadingBox
import com.byteharvest.mdreader.ui.components.relativeTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RepositoriesViewModel(private val c: AppContainer) : ViewModel() {
    val repos: StateFlow<List<RepositoryEntity>?> =
        c.repository.repositories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val refreshing = MutableStateFlow<Set<String>>(emptySet())
    val downloading = MutableStateFlow<Pair<String, Pair<Int, Int>>?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun refresh(id: String) {
        if (id in refreshing.value) return
        viewModelScope.launch {
            refreshing.update { it + id }
            messages.tryEmit(syncMessage(c.repository.refresh(id)))
            refreshing.update { it - id }
        }
    }

    fun downloadForOffline(id: String) {
        if (downloading.value != null) return
        viewModelScope.launch {
            downloading.value = id to (0 to 0)
            try {
                val report = c.repository.downloadForOffline(id) { done, total ->
                    downloading.value = id to (done to total)
                }
                messages.tryEmit(
                    "Offline download complete: ${report.downloaded} downloaded, ${report.alreadyCached} already saved, ${report.failed} unavailable."
                )
            } catch (e: IllegalStateException) {
                messages.tryEmit(e.message ?: "Could not download repository for offline use.")
            } finally {
                downloading.value = null
            }
        }
    }

    fun delete(id: String) { viewModelScope.launch { c.repository.deleteRepository(id) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoriesScreen(onBack: () -> Unit, onOpenRepo: (String) -> Unit) {
    val vm = appViewModel { c, _ -> RepositoriesViewModel(c) }
    val repos by vm.repos.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<RepositoryEntity?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Repositories") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val list = repos
        when {
            list == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            list.isEmpty() -> EmptyState(
                Icons.Filled.Inbox, "No saved repositories",
                "Repositories you open are saved here for offline reading.",
                Modifier.padding(pad).fillMaxSize(),
            )
            else -> LazyColumn(Modifier.padding(pad)) {
                items(list, key = { it.id }) { r ->
                    DocListItem(
                        title = "${r.owner}/${r.name}",
                        subtitle = buildString {
                            append("Branch ${r.branch}")
                            r.lastSyncAt?.let { append(" · synced ${relativeTime(it)}") }
                            if (r.syncState != "OK") append(" · ${r.syncState.lowercase()}")
                            downloading?.takeIf { it.first == r.id }?.let {
                                append("\nDownloading for offline · ${it.second.first}/${it.second.second}")
                            }
                            if (!r.description.isNullOrBlank()) append("\n${r.description}")
                        },
                        icon = if (r.isPrivate) Icons.Filled.Lock else Icons.Filled.Folder,
                        onClick = { onOpenRepo(r.id) },
                        trailingContent = {
                            Row {
                                if (downloading?.first == r.id) {
                                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                                } else {
                                    IconButton({ vm.downloadForOffline(r.id) }, enabled = downloading == null) {
                                        Icon(Icons.Filled.DownloadForOffline, "Download ${r.name} for offline reading")
                                    }
                                }
                                if (r.id in refreshing) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                                else IconButton({ vm.refresh(r.id) }, enabled = downloading == null) {
                                    Icon(Icons.Filled.Refresh, "Refresh ${r.name}")
                                }
                                IconButton({ confirmDelete = r }, enabled = downloading == null) {
                                    Icon(Icons.Filled.Delete, "Remove ${r.name}")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${r.owner}/${r.name}?") },
            text = { Text("Its cached documents, bookmarks and history on this device are deleted. Nothing on GitHub is touched.") },
            confirmButton = { TextButton({ vm.delete(r.id); confirmDelete = null }) { Text("Remove") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
