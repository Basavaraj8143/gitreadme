package com.byteharvest.mdreader.ui.bookmarks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.data.local.BookmarkItem
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

    fun remove(b: BookmarkItem) { viewModelScope.launch { c.repository.removeBookmark(b.repoId, b.path) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(onBack: () -> Unit, onOpenDoc: (String, String) -> Unit) {
    val vm = appViewModel { c, _ -> BookmarksViewModel(c) }
    val items by vm.items.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val list = items
        when {
            list == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            list.isEmpty() -> EmptyState(
                Icons.Filled.BookmarkBorder, "No bookmarks yet",
                "Tap the bookmark icon while reading a document to save it here.",
                Modifier.padding(pad).fillMaxSize(),
            )
            else -> LazyColumn(Modifier.padding(pad)) {
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
    }
}
