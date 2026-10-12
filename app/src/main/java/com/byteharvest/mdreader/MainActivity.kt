package com.byteharvest.mdreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byteharvest.mdreader.data.local.ThemeMode
import com.byteharvest.mdreader.ui.AppNav
import com.byteharvest.mdreader.ui.theme.MdReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as App).container.settings
        setContent {
            val mode by settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            MdReaderTheme(mode) { AppNav() }
        }
    }
}
