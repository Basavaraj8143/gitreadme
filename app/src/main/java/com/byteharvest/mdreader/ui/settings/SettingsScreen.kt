package com.byteharvest.mdreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.data.local.SettingsStore
import com.byteharvest.mdreader.data.local.ThemeMode
import com.byteharvest.mdreader.ui.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthState(val signedIn: Boolean, val busy: Boolean = false, val message: String? = null)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val theme: StateFlow<ThemeMode> = c.settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val fontSp: StateFlow<Float> = c.settings.fontSizeSp.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_FONT)
    private val _auth = MutableStateFlow(AuthState(c.repository.hasToken()))
    val auth = _auth.asStateFlow()

    fun setTheme(m: ThemeMode) { viewModelScope.launch { c.settings.setTheme(m) } }
    fun setFont(sp: Float) { viewModelScope.launch { c.settings.setFontSize(sp) } }

    fun signIn(token: String) {
        viewModelScope.launch {
            _auth.value = AuthState(_auth.value.signedIn, busy = true)
            c.repository.signIn(token).fold(
                { _auth.value = AuthState(true, message = "Signed in as $it.") },
                { _auth.value = AuthState(c.repository.hasToken(), message = it.message) },
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            c.repository.signOut()
            _auth.value = AuthState(false, message = "Signed out. Cached private repositories were removed.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onRepos: () -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val theme by vm.theme.collectAsStateWithLifecycle()
    val fontSp by vm.fontSp.collectAsStateWithLifecycle()
    val auth by vm.auth.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }

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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = theme == m, onClick = { vm.setTheme(m) })
                    Text(m.name.lowercase().replaceFirstChar { it.uppercase() })
                }
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
