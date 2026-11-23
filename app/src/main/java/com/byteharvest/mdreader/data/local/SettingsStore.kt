package com.byteharvest.mdreader.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ReaderFontMode { SYSTEM, SANS_SERIF, SERIF, MONOSPACE }

data class ReaderPosition(val index: Int = 0, val offset: Int = 0)
data class SettingsSnapshot(
    val theme: ThemeMode,
    val fontSizeSp: Float,
    val readerFontMode: ReaderFontMode,
    val renderedTextSelectionEnabled: Boolean,
)

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val fontKey = floatPreferencesKey("font_sp")
    private val readerFontModeKey = stringPreferencesKey("reader_font_mode")
    private val renderedTextSelectionKey = booleanPreferencesKey("rendered_text_selection")
    private val autoSyncRepositoriesKey = stringSetPreferencesKey("auto_sync_repositories")

    val theme: Flow<ThemeMode> = context.settingsDataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[themeKey] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }
    val fontSizeSp: Flow<Float> = context.settingsDataStore.data.map { it[fontKey] ?: DEFAULT_FONT }
    val readerFontMode: Flow<ReaderFontMode> = context.settingsDataStore.data.map { p ->
        runCatching {
            ReaderFontMode.valueOf(p[readerFontModeKey] ?: DEFAULT_READER_FONT_MODE.name)
        }.getOrDefault(DEFAULT_READER_FONT_MODE)
    }
    val renderedTextSelectionEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[renderedTextSelectionKey] ?: DEFAULT_RENDERED_TEXT_SELECTION }
    val autoSyncRepositoryIds: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[autoSyncRepositoriesKey] ?: emptySet() }

    suspend fun setTheme(mode: ThemeMode) { context.settingsDataStore.edit { it[themeKey] = mode.name } }
    suspend fun setFontSize(sp: Float) { context.settingsDataStore.edit { it[fontKey] = sp.coerceIn(MIN_FONT, MAX_FONT) } }
    suspend fun setReaderFontMode(mode: ReaderFontMode) {
        context.settingsDataStore.edit { it[readerFontModeKey] = mode.name }
    }
    suspend fun setRenderedTextSelectionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[renderedTextSelectionKey] = enabled }
    }

    suspend fun getTheme(): ThemeMode {
        val prefs = context.settingsDataStore.data.first()
        return runCatching { ThemeMode.valueOf(prefs[themeKey] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setAutoSyncEnabled(repoId: String, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            val repoIds = preferences[autoSyncRepositoriesKey].orEmpty().toMutableSet()
            if (enabled) repoIds.add(repoId) else repoIds.remove(repoId)
            preferences[autoSyncRepositoriesKey] = repoIds
        }
    }

    suspend fun readerPosition(repoId: String, path: String): ReaderPosition {
        val value = context.settingsDataStore.data.first()[positionKey(repoId, path)] ?: return ReaderPosition()
        val parts = value.split(':', limit = 2)
        return ReaderPosition(
            index = parts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            offset = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        )
    }

    suspend fun saveReaderPosition(repoId: String, path: String, index: Int, offset: Int) {
        context.settingsDataStore.edit {
            it[positionKey(repoId, path)] = "${index.coerceAtLeast(0)}:${offset.coerceAtLeast(0)}"
        }
    }

    suspend fun snapshot(): SettingsSnapshot {
        val prefs = context.settingsDataStore.data.first()
        return SettingsSnapshot(
            theme = runCatching { ThemeMode.valueOf(prefs[themeKey] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            fontSizeSp = (prefs[fontKey] ?: DEFAULT_FONT).coerceIn(MIN_FONT, MAX_FONT),
            readerFontMode = runCatching {
                ReaderFontMode.valueOf(prefs[readerFontModeKey] ?: DEFAULT_READER_FONT_MODE.name)
            }.getOrDefault(DEFAULT_READER_FONT_MODE),
            renderedTextSelectionEnabled =
                prefs[renderedTextSelectionKey] ?: DEFAULT_RENDERED_TEXT_SELECTION,
        )
    }

    private fun positionKey(repoId: String, path: String) = stringPreferencesKey(
        "reading_position_${sha256("$repoId\u0000$path")}"
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val DEFAULT_FONT = 16f
        const val MIN_FONT = 12f
        const val MAX_FONT = 28f
        val DEFAULT_READER_FONT_MODE = ReaderFontMode.SERIF
        const val DEFAULT_RENDERED_TEXT_SELECTION = true
    }
}
