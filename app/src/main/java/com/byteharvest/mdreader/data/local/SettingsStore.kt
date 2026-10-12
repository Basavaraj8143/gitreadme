package com.byteharvest.mdreader.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val fontKey = floatPreferencesKey("font_sp")

    val theme: Flow<ThemeMode> = context.settingsDataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[themeKey] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }
    val fontSizeSp: Flow<Float> = context.settingsDataStore.data.map { it[fontKey] ?: DEFAULT_FONT }

    suspend fun setTheme(mode: ThemeMode) { context.settingsDataStore.edit { it[themeKey] = mode.name } }
    suspend fun setFontSize(sp: Float) { context.settingsDataStore.edit { it[fontKey] = sp.coerceIn(MIN_FONT, MAX_FONT) } }

    companion object {
        const val DEFAULT_FONT = 16f
        const val MIN_FONT = 12f
        const val MAX_FONT = 28f
    }
}
