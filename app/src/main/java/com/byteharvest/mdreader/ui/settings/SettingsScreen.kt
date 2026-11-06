package com.byteharvest.mdreader.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.data.local.LOCAL_REPOSITORY_ID
import com.byteharvest.mdreader.data.local.SettingsStore
import com.byteharvest.mdreader.data.local.ThemeMode
import com.byteharvest.mdreader.data.repository.BookmarkBackup
import com.byteharvest.mdreader.data.work.RepositoryAutoSync
import com.byteharvest.mdreader.ui.appViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val theme: StateFlow<ThemeMode> = c.settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val fontSp: StateFlow<Float> = c.settings.fontSizeSp.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_FONT)
    val renderedTextSelectionEnabled: StateFlow<Boolean> =
        c.settings.renderedTextSelectionEnabled.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsStore.DEFAULT_RENDERED_TEXT_SELECTION,
        )
    val auth = c.repository.authState

    fun setTheme(m: ThemeMode) { viewModelScope.launch { c.settings.setTheme(m) } }
    fun setFont(sp: Float) { viewModelScope.launch { c.settings.setFontSize(sp) } }
    fun setRenderedTextSelectionEnabled(enabled: Boolean) {
        viewModelScope.launch { c.settings.setRenderedTextSelectionEnabled(enabled) }
    }

    fun signIn(token: String) {
        viewModelScope.launch { c.repository.signIn(token) }
    }

    fun signOut() {
        viewModelScope.launch {
            val autoSyncIds = c.settings.autoSyncRepositoryIds.first()
            for (repoId in autoSyncIds) {
                if (c.repository.repo(repoId)?.isPrivate == true) {
                    c.settings.setAutoSyncEnabled(repoId, false)
                    RepositoryAutoSync.cancel(c.appContext, repoId)
                }
            }
            c.repository.signOut()
        }
    }

    suspend fun exportBackup(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val settings = c.settings.snapshot()
            val bookmarks = c.repository.bookmarks().first().filter { it.repoId != LOCAL_REPOSITORY_ID }
            val root = JSONObject()
                .put("formatVersion", 1)
                .put("exportedAt", System.currentTimeMillis())
                .put(
                    "settings",
                    JSONObject()
                        .put("theme", settings.theme.name)
                        .put("fontSizeSp", settings.fontSizeSp.toDouble())
                        .put("renderedTextSelectionEnabled", settings.renderedTextSelectionEnabled),
                )
                .put("bookmarks", JSONArray().apply {
                    bookmarks.forEach { bookmark ->
                        put(
                            JSONObject()
                                .put("owner", bookmark.owner)
                                .put("repository", bookmark.repoName)
                                .put("branch", bookmark.branch)
                                .put("path", bookmark.path)
                                .put("title", bookmark.title)
                                .put("createdAt", bookmark.createdAt),
                        )
                    }
                })
            val output = c.appContext.contentResolver.openOutputStream(uri)
                ?: return@withContext "Could not open the selected backup file."
            output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
            "Settings and bookmarks exported."
        } catch (e: Exception) {
            "Backup export failed: ${e.message ?: "Could not write the file."}"
        }
    }

    suspend fun importBackup(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val input = c.appContext.contentResolver.openInputStream(uri)
                ?: return@withContext "Could not open the selected backup file."
            val root = input.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            if (root.getInt("formatVersion") != 1) return@withContext "This backup format is not supported."

            val settingData = root.getJSONObject("settings")
            val theme = ThemeMode.valueOf(settingData.getString("theme"))
            val fontSize = settingData.getDouble("fontSizeSp").toFloat()
            require(fontSize.isFinite()) { "Backup has an invalid font size." }
            val renderedTextSelectionEnabled =
                if (settingData.has("renderedTextSelectionEnabled")) {
                    settingData.getBoolean("renderedTextSelectionEnabled")
                } else {
                    SettingsStore.DEFAULT_RENDERED_TEXT_SELECTION
                }
            val bookmarksJson = root.getJSONArray("bookmarks")
            val bookmarks = List(bookmarksJson.length()) { index ->
                val item = bookmarksJson.getJSONObject(index)
                BookmarkBackup(
                    owner = item.getString("owner"),
                    repoName = item.getString("repository"),
                    branch = item.getString("branch"),
                    path = item.getString("path"),
                    title = item.getString("title"),
                    createdAt = item.getLong("createdAt"),
                )
            }

            c.settings.setTheme(theme)
            c.settings.setFontSize(fontSize)
            c.settings.setRenderedTextSelectionEnabled(renderedTextSelectionEnabled)
            val (restored, skipped) = c.repository.restoreBookmarks(bookmarks)
            if (skipped > 0) {
                "$restored bookmarks restored; $skipped skipped. Save their repositories first, then import again."
            } else {
                "Settings and $restored bookmarks restored."
            }
        } catch (e: Exception) {
            "Backup import failed: ${e.message ?: "The file is invalid or unreadable."}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onRepos: () -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val theme by vm.theme.collectAsStateWithLifecycle()
    val fontSp by vm.fontSp.collectAsStateWithLifecycle()
    val renderedTextSelectionEnabled by vm.renderedTextSelectionEnabled.collectAsStateWithLifecycle()
    val auth by vm.auth.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { Toast.makeText(context, vm.exportBackup(uri), Toast.LENGTH_LONG).show() }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { Toast.makeText(context, vm.importBackup(uri), Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            ThemeMode.values().forEach { m ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = theme == m,
                        role = Role.RadioButton,
                        onClick = { vm.setTheme(m) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = theme == m, onClick = null)
                    Text(m.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.padding(vertical = 12.dp))
                }
            }
            HorizontalDivider()
            Text("Reader follows the app theme.", style = MaterialTheme.typography.bodyMedium)
            Row(
                Modifier.fillMaxWidth().selectable(
                    selected = renderedTextSelectionEnabled,
                    role = Role.Switch,
                    onClick = { vm.setRenderedTextSelectionEnabled(!renderedTextSelectionEnabled) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text("Select and copy rendered text")
                    Text(
                        "Allow long-press selection in the rendered reader.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = renderedTextSelectionEnabled,
                    onCheckedChange = null,
                )
            }
            HorizontalDivider()
            Text("Reader font size: ${fontSp.toInt()} sp", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = fontSp, onValueChange = { vm.setFont(it) },
                valueRange = SettingsStore.MIN_FONT..SettingsStore.MAX_FONT, steps = 7,
            )
            HorizontalDivider()
            Text("GitHub access", style = MaterialTheme.typography.titleMedium)
            Text(
                "Public repositories work without signing in. For private repositories (and higher rate limits) paste a " +
                    "fine-grained personal access token with read-only \"Contents\" and \"Metadata\" permission, limited to the repositories you need. " +
                    "It is stored encrypted with a key held in the Android Keystore.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (auth.signedIn) {
                OutlinedButton({ confirmSignOut = true }) { Text("Sign out") }
            } else {
                OutlinedTextField(
                    token, { token = it }, singleLine = true, label = { Text("Personal access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button({ vm.signIn(token); token = "" }, enabled = token.isNotBlank() && !auth.busy) { Text("Sign in") }
            }
            auth.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
            Text("Repositories and cache", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onRepos) { Text("Manage saved repositories") }
            HorizontalDivider()
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Export or restore your theme, font size and bookmarks. Tokens and cached documents are not included. " +
                    "Save repositories on this device before importing their bookmarks.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { backupLauncher.launch("gitread-me-backup.json") }) {
                Text("Export settings and bookmarks")
            }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/json")) }) {
                Text("Import settings and bookmarks")
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("The token is deleted from this device and cached private repositories are removed.") },
            confirmButton = { TextButton({ vm.signOut(); confirmSignOut = false }) { Text("Sign out") } },
            dismissButton = { TextButton({ confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}
