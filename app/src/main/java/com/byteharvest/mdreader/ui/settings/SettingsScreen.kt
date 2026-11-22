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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.data.local.LOCAL_REPOSITORY_ID
import com.byteharvest.mdreader.data.local.ReaderFontMode
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
    val readerFontMode: StateFlow<ReaderFontMode> =
        c.settings.readerFontMode.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsStore.DEFAULT_READER_FONT_MODE,
        )
    val renderedTextSelectionEnabled: StateFlow<Boolean> =
        c.settings.renderedTextSelectionEnabled.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsStore.DEFAULT_RENDERED_TEXT_SELECTION,
        )
    val auth = c.repository.authState

    fun setTheme(m: ThemeMode) { viewModelScope.launch { c.settings.setTheme(m) } }
    fun setFont(sp: Float) { viewModelScope.launch { c.settings.setFontSize(sp) } }
    fun setReaderFontMode(mode: ReaderFontMode) {
        viewModelScope.launch { c.settings.setReaderFontMode(mode) }
    }
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
                        .put("readerFontMode", settings.readerFontMode.name)
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
            val readerFontMode = if (settingData.has("readerFontMode")) {
                runCatching { ReaderFontMode.valueOf(settingData.getString("readerFontMode")) }
                    .getOrDefault(SettingsStore.DEFAULT_READER_FONT_MODE)
            } else {
                SettingsStore.DEFAULT_READER_FONT_MODE
            }
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
            c.settings.setReaderFontMode(readerFontMode)
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
    val readerFontMode by vm.readerFontMode.collectAsStateWithLifecycle()
    val renderedTextSelectionEnabled by vm.renderedTextSelectionEnabled.collectAsStateWithLifecycle()
    val auth by vm.auth.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                val result = vm.exportBackup(uri)
                isExporting = false
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val result = vm.importBackup(uri)
                isImporting = false
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Appearance Section
            item {
                SettingsCard(
                    icon = Icons.Filled.Palette,
                    title = "Appearance",
                    description = "Choose how Gitread.me looks across the app and reader.",
                ) {
                    SettingsSectionContent(
                        title = "Theme",
                        description = "Select your preferred color scheme",
                    ) {
                        ThemeSelector(
                            selected = theme,
                            onSelect = { vm.setTheme(it) },
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingsSectionContent(
                        title = "Reader Font",
                        description = "Choose the font family for reading",
                    ) {
                        FontModeSelector(
                            selected = readerFontMode,
                            onSelect = { vm.setReaderFontMode(it) },
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingsSectionContent(
                        title = "Reader Font Size",
                        description = "Adjust the text size for comfortable reading",
                        trailing = "${fontSp.toInt()} sp",
                    ) {
                        FontSizeSlider(
                            value = fontSp,
                            onValueChange = { vm.setFont(it) },
                        )
                    }
                }
            }

            // Reader Section
            item {
                SettingsCard(
                    icon = Icons.Filled.MenuBook,
                    title = "Reader",
                    description = "Adjust reading size and text interaction.",
                ) {
                    SettingsToggleItem(
                        title = "Select and copy rendered text",
                        description = "Allow long-press selection in the rendered reader.",
                        checked = renderedTextSelectionEnabled,
                        onCheckedChange = { vm.setRenderedTextSelectionEnabled(it) },
                    )
                }
            }

            // GitHub Access Section
            item {
                SettingsCard(
                    icon = Icons.Filled.Code,
                    title = "GitHub Access",
                    description = "Public repositories work without signing in. A read-only token enables private repositories and higher rate limits. Tokens are stored encrypted in the Android Keystore.",
                ) {
                    if (auth.signedIn) {
                        SettingsSectionContent(
                            title = "Signed in",
                            description = auth.message ?: "Your GitHub token is active.",
                        ) {
                            OutlinedButton(
                                onClick = { confirmSignOut = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Sign out")
                            }
                        }
                    } else {
                        SettingsSectionContent(
                            title = "Sign in with GitHub",
                            description = "Enter your personal access token to access private repositories",
                        ) {
                            OutlinedTextField(
                                value = token,
                                onValueChange = { token = it },
                                singleLine = true,
                                label = { Text("Personal access token") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { vm.signIn(token); token = "" },
                                enabled = token.isNotBlank() && !auth.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (auth.busy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(end = 8.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                                Text(if (auth.busy) "Signing in..." else "Sign in")
                            }
                        }
                    }
                    auth.message?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            // Repositories Section
            item {
                SettingsCard(
                    icon = Icons.Filled.Folder,
                    title = "Repositories & Storage",
                    description = "Manage saved repositories, offline syncing, and app-managed storage.",
                ) {
                    OutlinedButton(
                        onClick = onRepos,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open repositories & storage")
                    }
                }
            }

            // Backup Section
            item {
                SettingsCard(
                    icon = Icons.Filled.Backup,
                    title = "Backup & Restore",
                    description = "Export or restore your theme, font size, and bookmarks. Tokens and cached documents are not included. Save repositories on this device before importing their bookmarks.",
                ) {
                    SettingsSectionContent(
                        title = "Export",
                        description = "Save your settings and bookmarks to a file",
                    ) {
                        Button(
                            onClick = { backupLauncher.launch("gitread-me-backup.json") },
                            enabled = !isExporting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text("Exporting...")
                            } else {
                                Text("Export settings and bookmarks")
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingsSectionContent(
                        title = "Import",
                        description = "Restore settings and bookmarks from a backup file",
                    ) {
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("application/json", "text/json")) },
                            enabled = !isImporting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text("Importing...")
                            } else {
                                Text("Import settings and bookmarks")
                            }
                        }
                    }
                }
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

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(4.dp),
                )
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // Content
            content()
        }
    }
}

@Composable
private fun SettingsSectionContent(
    title: String,
    description: String,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        content()
    }
}

@Composable
private fun ThemeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ThemeMode.values().forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ThemeMode.values().size,
                ),
            ) {
                Text(
                    when (mode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                )
            }
        }
    }
}

@Composable
private fun FontSizeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = SettingsStore.MIN_FONT..SettingsStore.MAX_FONT,
            steps = 7,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Small",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Large",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FontModeSelector(
    selected: ReaderFontMode,
    onSelect: (ReaderFontMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ReaderFontMode.values().forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ReaderFontMode.values().size,
                ),
            ) {
                Text(
                    when (mode) {
                        ReaderFontMode.SYSTEM -> "System"
                        ReaderFontMode.SANS_SERIF -> "Sans"
                        ReaderFontMode.SERIF -> "Serif"
                        ReaderFontMode.MONOSPACE -> "Mono"
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
