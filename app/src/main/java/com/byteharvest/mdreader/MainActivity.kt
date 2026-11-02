package com.byteharvest.mdreader

import android.content.Intent
import android.net.Uri
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
                AppNav(externalDocumentUri = markdownUriFromIntent(intent))
            }
        }
    }

    private fun markdownUriFromIntent(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data ?: intent.clipData?.getItemAt(0)?.uri
        Intent.ACTION_SEND -> {
            val sharedUri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            sharedUri ?: intent.clipData?.getItemAt(0)?.uri
        }
        else -> null
    }
}
