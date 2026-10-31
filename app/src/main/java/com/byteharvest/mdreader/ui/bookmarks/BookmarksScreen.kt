package com.byteharvest.mdreader.ui.bookmarks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.data.local.BookmarkItem
import com.byteharvest.mdreader.data.local.OfflineRepositoryItem
import com.byteharvest.mdreader.ui.appViewModel
import com.byteharvest.mdreader.ui.components.DocListItem
import com.byteharvest.mdreader.ui.components.EmptyState
import com.byteharvest.mdreader.ui.components.LoadingBox
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookmarksViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<BookmarkItem>?> =
        c.repository.bookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val offlineRepos: StateFlow<List<OfflineRepositoryItem>?> =
        c.repository.offlineRepositories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun remove(b: BookmarkItem) { viewModelScope.launch { c.repository.removeBookmark(b.repoId, b.path) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenDoc: (String, String) -> Unit,
    onOpenRepo: (String) -> Unit,
) {
    val vm = appViewModel { c, _ -> BookmarksViewModel(c) }
    val items by vm.items.collectAsStateWithLifecycle()
    val offlineRepos by vm.offlineRepos.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Bookmarked files") },
                    icon = { Icon(Icons.Filled.BookmarkBorder, contentDescription = null) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Offline repos") },
                    icon = { Icon(Icons.Filled.DownloadDone, contentDescription = null) },
                )
            }
            if (selectedTab == 0) {
                when (val list = items) {
                    null -> LoadingBox(Modifier.fillMaxSize())
                    emptyList<BookmarkItem>() -> EmptyState(
                        Icons.Filled.BookmarkBorder, "No bookmarks yet",
                        "Tap the bookmark icon while reading a document to save it here.",
                        Modifier.fillMaxSize(),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { "${it.repoId}|${it.path}" }) { b ->
                            DocListItem(
                                title = b.title,
                                subtitle = "${b.owner}/${b.repoName} · ${b.path}",
                                icon = Icons.Filled.Bookmark,
                                onClick = { onOpenDoc(b.repoId, b.path) },
                                trailingContent = { IconButton({ vm.remove(b) }) { Icon(Icons.Filled.Delete, "Remove bookmark") } },
                            )
                        }
                    }
                }
            } else {
                when (val list = offlineRepos) {
                    null -> LoadingBox(Modifier.fillMaxSize())
                    emptyList<OfflineRepositoryItem>() -> EmptyState(
                        Icons.Filled.DownloadDone,
                        "No offline-ready repositories",
                        "Open a repository on GitHub, tap its three-dot menu, and choose Make available offline.",
                        Modifier.fillMaxSize(),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.repoId }) { repo ->
                            DocListItem(
                                title = "${repo.owner}/${repo.repoName}",
                                subtitle = buildString {
                                    append("Branch ${repo.branch} · ${repo.cachedDocs} Markdown files saved")
                                    if (!repo.description.isNullOrBlank()) append("\n${repo.description}")
                                },
                                icon = Icons.Filled.Folder,
                                onClick = { onOpenRepo(repo.repoId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
