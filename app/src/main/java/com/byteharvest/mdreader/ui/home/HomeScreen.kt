package com.byteharvest.mdreader.ui.home

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.R
import com.byteharvest.mdreader.core.localDocumentFileName
import com.byteharvest.mdreader.data.local.LOCAL_REPOSITORY_ID
import com.byteharvest.mdreader.data.local.RecentDoc
import com.byteharvest.mdreader.ui.appViewModel
import com.byteharvest.mdreader.ui.components.DocListItem
import com.byteharvest.mdreader.ui.components.EmptyState
import com.byteharvest.mdreader.ui.components.relativeTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class OpenState(val loading: Boolean = false, val error: String? = null)

@Composable
private fun HomeHero(onOpenGitHub: () -> Unit, onOpenFile: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val titleColor = if (dark) Color(0xFFF9F6FF) else Color(0xFF211448)
    val bodyColor = if (dark) Color(0xFFE0D7F5) else Color(0xFF594B7B)
    val accent = if (dark) Color(0xFF4A3291) else Color(0xFF6B4BD1)
    val buttonText = if (dark) Color(0xFF211448) else Color.White
    val outlineColor = if (dark) Color(0xFFE6DFFF) else Color(0xFF6549BD)
    val cardShape = RoundedCornerShape(28.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    if (dark) listOf(Color(0xFF402C83), Color(0xFF271951))
                    else listOf(Color(0xFFF3ECFF), Color(0xFFE9DEFF)),
                )
            )
    ) {
        ReadingStackIllustration(
            dark = dark,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = if (dark) Color(0xFFF4EEFF) else Color(0xFF6141BD),
                modifier = Modifier.size(24.dp),
            )
            Text(
                "Make room for\ngood reading.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                modifier = Modifier.widthIn(max = 245.dp),
            )
            Text(
                "Open a GitHub repository or bring in a Markdown file to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
                modifier = Modifier.widthIn(max = 270.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenGitHub,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = if (dark) Color(0xFFF8F5FF) else accent, contentColor = buttonText),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_github_mark),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text("GitHub", Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onOpenFile,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.25.dp, outlineColor.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dark) Color.White else Color(0xFF51369E)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Open file", Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ReadingStackIllustration(dark: Boolean, modifier: Modifier = Modifier) {
    val backSheet = if (dark) Color(0xFF9679EA).copy(alpha = 0.58f) else Color(0xFFB7A0F2).copy(alpha = 0.48f)
    val frontSheet = if (dark) Color(0xFFFFFCFF) else Color(0xFFFFFFFF)
    val lineColor = if (dark) Color(0xFFB8A7E9) else Color(0xFFC9B9F5)

    Box(modifier.size(width = 112.dp, height = 132.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 8.dp)
                .size(width = 70.dp, height = 100.dp).graphicsLayer(rotationZ = 13f),
            shape = RoundedCornerShape(10.dp),
            color = backSheet,
        ) {
            Column(
                Modifier.padding(start = 14.dp, top = 22.dp, end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(lineColor.copy(alpha = 0.72f))) }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 2.dp)
                .size(width = 68.dp, height = 92.dp).graphicsLayer(rotationZ = -11f),
            shape = RoundedCornerShape(10.dp),
            color = frontSheet,
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier.padding(start = 13.dp, top = 25.dp, end = 11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(lineColor)) }
            }
        }
    }
}

class HomeViewModel(c: AppContainer) : ViewModel() {
    private val repository = c.repository
    val recent: StateFlow<List<RecentDoc>?> =
        repository.recentDocs(10).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun removeFromRecent(doc: RecentDoc) {
        viewModelScope.launch { repository.removeFromHistory(doc.repoId, doc.path) }
    }
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
                val targetFile = File(localDir, localDocumentFileName(uri.toString(), name))
                targetFile.writeText(text)

                onOpenDoc(LOCAL_REPOSITORY_ID, targetFile.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gitread.me", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenGitHub) {
                        Icon(
                            painterResource(R.drawable.ic_github_mark),
                            contentDescription = "Open from GitHub",
                        )
                    }
                    IconButton(onClick = onBookmarks) { Icon(Icons.Filled.BookmarkBorder, contentDescription = "Bookmarks") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                HomeHero(
                    onOpenGitHub = onOpenGitHub,
                    onOpenFile = { localFileLauncher.launch(arrayOf("text/markdown", "application/markdown")) },
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Recently opened", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            val list = recent
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Inbox, "Nothing read yet",
                        "Your reading history will show up here.",
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            } else if (list != null) {
                items(list, key = { "${it.repoId}|${it.path}" }) { d ->
                    var menuExpanded by remember(d.repoId, d.path) { mutableStateOf(false) }
                    Box {
                        DocListItem(
                            title = d.title,
                            subtitle = if (d.repoId == LOCAL_REPOSITORY_ID) {
                                "On this device · Local Markdown file"
                            } else {
                                "${d.owner}/${d.repoName} · ${d.path}"
                            },
                            icon = Icons.Filled.Description,
                            trailing = relativeTime(d.lastOpenedAt),
                            onLongClick = { menuExpanded = true },
                            onClick = { onOpenDoc(d.repoId, d.path) },
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Remove from recent",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    vm.removeFromRecent(d)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
