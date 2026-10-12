package com.byteharvest.mdreader.ui.home

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.data.local.RecentDoc
import com.byteharvest.mdreader.ui.appViewModel
import com.byteharvest.mdreader.ui.components.DocListItem
import com.byteharvest.mdreader.ui.components.EmptyState
import com.byteharvest.mdreader.ui.components.relativeTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File

data class OpenState(val loading: Boolean = false, val error: String? = null)

class HomeViewModel(c: AppContainer) : ViewModel() {
    val recent: StateFlow<List<RecentDoc>?> =
        c.repository.recentDocs(10).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGitHub: () -> Unit,
    onOpenDoc: (String, String) -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = appViewModel { c, _ -> HomeViewModel(c) }
    val recent by vm.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val localFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val name = cursor?.use {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (it.moveToFirst() && idx >= 0) it.getString(idx) else null
                } ?: "document.md"

                if (!name.endsWith(".md", ignoreCase = true)) {
                    Toast.makeText(context, "Please select a Markdown (.md) file", Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }

                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Could not read file")

                val localDir = File(context.filesDir, "local_docs").apply { mkdirs() }
                val targetFile = File(localDir, "${System.currentTimeMillis()}_$name")
                targetFile.writeText(text)

                onOpenDoc("local", targetFile.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Read.md", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenGitHub) { Icon(Icons.Filled.Code, contentDescription = "Open from GitHub") }
                    IconButton(onClick = onBookmarks) { Icon(Icons.Filled.BookmarkBorder, contentDescription = "Bookmarks") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { localFileLauncher.launch(arrayOf("text/markdown", "application/markdown")) },
                icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                text = { Text("Open File") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text("Recent Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            }
            val list = recent
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Inbox, "Nothing read yet",
                        "Tap the GitHub icon above to open a repository, or the bottom button to open a local file.",
                        Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            } else if (list != null) {
                items(list, key = { "${it.repoId}|${it.path}" }) { d ->
                    DocListItem(
                        title = d.path.substringAfterLast('/'),
                        subtitle = "${d.owner}/${d.repoName} · ${d.path}",
                        icon = Icons.Filled.Description,
                        trailing = relativeTime(d.lastOpenedAt),
                        onClick = { onOpenDoc(d.repoId, d.path) },
                    )
                }
            }
        }
    }
}
