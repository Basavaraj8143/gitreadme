package com.byteharvest.mdreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.byteharvest.mdreader.data.local.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FBF), onPrimary = Color.White,
    secondary = Color(0xFF4F6070), background = Color(0xFFFAFBFD), surface = Color(0xFFFAFBFD),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DC3FF), onPrimary = Color(0xFF002F65),
    secondary = Color(0xFFB7C8DA), background = Color(0xFF101418), surface = Color(0xFF101418),
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
}

@Composable
fun MdReaderTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
