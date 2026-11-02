package com.byteharvest.mdreader.ui.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.core.markdown.CommonmarkEngine
import com.byteharvest.mdreader.core.markdown.MarkdownDoc
import com.byteharvest.mdreader.core.markdown.MarkdownEngine
import com.byteharvest.mdreader.core.markdown.countMatches
import com.byteharvest.mdreader.core.markdown.searchRuns
import com.byteharvest.mdreader.core.pdf.PdfExporter
import com.byteharvest.mdreader.data.local.RepositoryEntity
import com.byteharvest.mdreader.data.local.ReaderThemeMode
import com.byteharvest.mdreader.data.local.SettingsStore
import com.byteharvest.mdreader.data.local.ThemeMode
import com.byteharvest.mdreader.data.local.LOCAL_REPOSITORY_ID
import com.byteharvest.mdreader.data.remote.GitHubUrls
import com.byteharvest.mdreader.data.repository.DocLoad
import com.byteharvest.mdreader.domain.LinkResolver
import com.byteharvest.mdreader.ui.navArg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReaderState(
    val loading: Boolean = true,
    val doc: MarkdownDoc? = null,
    val rawMarkdown: String? = null,
    val error: String? = null,
    val notice: String? = null,
)

/** matches = block index per occurrence, in document order. current indexes into matches (-1 = none). */
data class SearchState(val query: String = "", val matches: List<Int> = emptyList(), val current: Int = -1)

class ReaderViewModel(handle: SavedStateHandle, private val c: AppContainer) : ViewModel() {
    val repoId = navArg(handle, "repoId")
    val path = navArg(handle, "path")
    val title = if (repoId == LOCAL_REPOSITORY_ID) {
        val rawName = path.substringAfterLast('/')
        if (rawName.contains('_')) rawName.substringAfter('_') else rawName
    } else {
        path.substringAfterLast('/')
    }

    private val engine: MarkdownEngine = CommonmarkEngine()
    private val _state = MutableStateFlow(ReaderState())
    val state = _state.asStateFlow()
    private val _search = MutableStateFlow(SearchState())
    val search = _search.asStateFlow()

    // Repo loaded as StateFlow to avoid race conditions in imageSpec() and githubUrl()
    private val _repo = MutableStateFlow<RepositoryEntity?>(null)
    val repo: StateFlow<RepositoryEntity?> = _repo.asStateFlow()

    val fontSp: StateFlow<Float> = c.settings.fontSizeSp.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_FONT)
    val readerTheme: StateFlow<ReaderThemeMode> =
        c.settings.readerTheme.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderThemeMode.DARK)
    val renderedTextSelectionEnabled: StateFlow<Boolean> =
        c.settings.renderedTextSelectionEnabled.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsStore.DEFAULT_RENDERED_TEXT_SELECTION,
        )
    val appTheme: StateFlow<ThemeMode> =
        c.settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val bookmarked: StateFlow<Boolean> = c.repository.isBookmarked(repoId, path).map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        if (repoId == LOCAL_REPOSITORY_ID) {
            loadLocalDoc()
        } else {
            viewModelScope.launch { _repo.value = c.repository.repo(repoId) }
            load(false)
        }
    }

    fun load(force: Boolean) {
        if (repoId == LOCAL_REPOSITORY_ID) {
            loadLocalDoc()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            _repo.value = c.repository.repo(repoId)
            when (val r = c.repository.loadDocument(repoId, path, force)) {
                is DocLoad.Fail -> _state.value = ReaderState(loading = false, error = r.message)
                is DocLoad.Ok -> {
                    val doc = withContext(Dispatchers.Default) { engine.parse(r.text) } // keeps the UI thread free
                    _state.value = ReaderState(loading = false, doc = doc, rawMarkdown = r.text, notice = r.note)
                    c.repository.recordHistory(repoId, path, title)
                    setQuery(_search.value.query)
                }
            }
        }
    }

    private fun loadLocalDoc() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val text = withContext(Dispatchers.IO) {
                    File(path).readText()
                }
                val doc = withContext(Dispatchers.Default) { engine.parse(text) }
                _state.value = ReaderState(loading = false, doc = doc, rawMarkdown = text)
                c.repository.recordHistory(repoId, path, title)
                setQuery(_search.value.query)
            } catch (e: Exception) {
                _state.value = ReaderState(loading = false, error = e.message ?: "Could not read local file.")
            }
        }
    }

    fun setQuery(raw: String) {
        val q = raw.trim()
        val doc = _state.value.doc
        if (q.isEmpty() || doc == null) { _search.value = SearchState(q); return }
        viewModelScope.launch {
            val matches = withContext(Dispatchers.Default) {
                val out = ArrayList<Int>()
                doc.blocks.forEachIndexed { i, b ->
                    repeat(b.searchRuns().sumOf { countMatches(it, q) }) { out.add(i) }
                }
                out
            }
            _search.value = SearchState(q, matches, if (matches.isEmpty()) -1 else 0)
        }
    }

    fun nextMatch() = step(+1)
    fun prevMatch() = step(-1)
    private fun step(d: Int) = _search.update { s ->
        if (s.matches.isEmpty()) s else s.copy(current = (s.current + d + s.matches.size) % s.matches.size)
    }

    fun toggleBookmark() {
        viewModelScope.launch { c.repository.toggleBookmark(repoId, path, title, bookmarked.value) }
    }

    fun changeFont(delta: Float) {
        viewModelScope.launch { c.settings.setFontSize(fontSp.value + delta) }
    }

    suspend fun readingPosition() = c.settings.readerPosition(repoId, path)

    fun saveReadingPosition(index: Int, offset: Int) {
        viewModelScope.launch { c.settings.saveReaderPosition(repoId, path, index, offset) }
    }

    suspend fun exists(p: String): Boolean {
        if (repoId == LOCAL_REPOSITORY_ID) return false
        return c.repository.hasFile(repoId, p)
    }

    fun githubUrl(p: String): String =
        _repo.value?.let { GitHubUrls.blob(it.owner, it.name, it.branch, p) } ?: "https://github.com"

    /** Relative images resolve against this document's repo path + branch; private content gets auth and no caching. */
    fun imageSpec(src: String): ImageSpec? {
        val s = src.trim()
        if (s.startsWith("http://") || s.startsWith("https://")) return ImageSpec(s, emptyMap(), true)
        if (s.startsWith("//")) return ImageSpec("https:$s", emptyMap(), true)
        if (repoId == LOCAL_REPOSITORY_ID) return null
        val r = _repo.value ?: return null
        val resolved = LinkResolver.resolveRepoRef(path, s) ?: return null
        if (!r.isPrivate) return ImageSpec(GitHubUrls.raw(r.owner, r.name, r.branch, resolved).toString(), emptyMap(), true)
        val token = c.repository.token() ?: return null
        return ImageSpec(
            GitHubUrls.contents(r.owner, r.name, r.branch, resolved).toString(),
            mapOf("Authorization" to "Bearer $token", "Accept" to "application/vnd.github.raw+json"),
            cache = false,
        )
    }

    /** Writes the PDF to a user-chosen document URI (Storage Access Framework). */
    suspend fun exportTo(uri: Uri): String = withContext(Dispatchers.IO) {
        val doc = _state.value.doc ?: return@withContext "Nothing to export."
        try {
            c.appContext.contentResolver.openOutputStream(uri)?.use { PdfExporter.render(doc, it) }
                ?: return@withContext "Could not open the destination."
            "PDF saved."
        } catch (e: Exception) {
            "PDF export failed."
        }
    }

    /** Writes the PDF into the app cache (for the share sheet). */
    suspend fun exportToCache(): File? = withContext(Dispatchers.IO) {
        val doc = _state.value.doc ?: return@withContext null
        try {
            val dir = File(c.appContext.cacheDir, "exports").apply { mkdirs() }
            File(dir, title.substringBeforeLast('.') + ".pdf").also { f -> f.outputStream().use { PdfExporter.render(doc, it) } }
        } catch (e: Exception) {
            null
        }
    }
}
