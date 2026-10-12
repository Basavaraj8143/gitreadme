package com.byteharvest.mdreader.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byteharvest.mdreader.App
import com.byteharvest.mdreader.AppContainer

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = (LocalContext.current.applicationContext as App).container
    return viewModel<VM>(factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}

/** Navigation arguments arrive URL-encoded; decoding twice is harmless for ordinary repo paths. */
fun navArg(h: SavedStateHandle, key: String): String = Uri.decode(h.get<String>(key).orEmpty())
