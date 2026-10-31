package com.byteharvest.mdreader

import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import com.byteharvest.mdreader.data.local.ThemeMode
import com.byteharvest.mdreader.ui.AppNav
import com.byteharvest.mdreader.ui.theme.GitreadMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as App).container.settings
        setContent {
            val mode by settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            GitreadMeTheme(mode) {
                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    window.setBackgroundDrawable(ColorDrawable(backgroundColor))
                }
                AppNav()
            }
        }
    }
}
