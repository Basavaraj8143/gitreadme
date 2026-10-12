package com.byteharvest.mdreader.ui.opengithub

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteharvest.mdreader.AppContainer
import com.byteharvest.mdreader.core.ParseResult
import com.byteharvest.mdreader.core.RepoInputParser
import com.byteharvest.mdreader.data.local.RecentDoc
import com.byteharvest.mdreader.data.local.RepositoryEntity
import com.byteharvest.mdreader.data.remote.RepoDto
import com.byteharvest.mdreader.ui.appViewModel
import com.byteharvest.mdreader.ui.components.DocListItem
import com.byteharvest.mdreader.ui.home.OpenState
import com.byteharvest.mdreader.ui.settings.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OpenGitHubViewModel(private val c: AppContainer) : ViewModel() {
    val recent: StateFlow<List<RecentDoc>?> =
        c.repository.recentDocs(10).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val repos: StateFlow<List<RepositoryEntity>?> =
        c.repository.repositories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _userRepos = MutableStateFlow<List<RepoDto>?>(null)
    val userRepos = _userRepos.asStateFlow()

    private val _open = MutableStateFlow(OpenState())
    val open = _open.asStateFlow()

    private val _auth = MutableStateFlow(AuthState(c.repository.hasToken()))
    val auth = _auth.asStateFlow()

    var username by mutableStateOf<String?>(null)

    init {
        if (c.repository.hasToken()) {
            fetchUserRepos()
        }
    }

    fun clearError() { _open.value = OpenState() }

    fun fetchUserRepos() {
        viewModelScope.launch {
            c.repository.getUserRepos().fold(
                onSuccess = { _userRepos.value = it },
                onFailure = { _userRepos.value = emptyList() }
            )
        }
    }

    fun open(raw: String, onDone: (String) -> Unit) {
        when (val p = RepoInputParser.parse(raw)) {
            is ParseResult.Error -> _open.value = OpenState(error = p.message)
            is ParseResult.Ok -> viewModelScope.launch {
                _open.value = OpenState(loading = true)
                c.repository.openRepository(p.input.owner, p.input.name, p.input.branch).fold(
                    onSuccess = { _open.value = OpenState(); onDone(it) },
                    onFailure = { _open.value = OpenState(error = it.message ?: "Could not open repository.") },
                )
            }
        }
    }

    fun signIn(token: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _auth.value = AuthState(c.repository.hasToken(), busy = true)
            c.repository.signIn(token).fold(
                {
                    username = it
                    _auth.value = AuthState(true, message = "Signed in as $it.")
                    fetchUserRepos()
                    onSuccess()
                },
                { _auth.value = AuthState(c.repository.hasToken(), message = it.message) },
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            c.repository.signOut()
            username = null
            _userRepos.value = null
            _auth.value = AuthState(false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenGitHubScreen(
    onBack: () -> Unit,
    onOpenRepo: (String) -> Unit,
    onOpenDoc: (String, String) -> Unit,
) {
    val vm = appViewModel { c, _ -> OpenGitHubViewModel(c) }
    val open by vm.open.collectAsStateWithLifecycle()
    val auth by vm.auth.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val repos by vm.repos.collectAsStateWithLifecycle()
    val userRepos by vm.userRepos.collectAsStateWithLifecycle()

    var urlInput by rememberSaveable { mutableStateOf("") }
    var showSignInSheet by rememberSaveable { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    val submit = {
        vm.open(urlInput) { repoId -> onOpenRepo(repoId) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open from GitHub") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (auth.signedIn) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 16.dp).clickable { showSignInSheet = true },
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("@${vm.username ?: "user"}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        TextButton(onClick = { showSignInSheet = true }) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sign in")
                        }
                    }
                }
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Code,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text("Open from GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Paste a repo URL or owner/repo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it; vm.clearError() },
                        singleLine = true,
                        placeholder = { Text("owner/repo or full URL") },
                        isError = open.error != null,
                        supportingText = { open.error?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                        keyboardActions = KeyboardActions(onGo = { if (!open.loading) submit() }),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )

                    Button(
                        onClick = submit,
                        enabled = !open.loading && urlInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (open.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Open")
                        }
                    }
                }
            }

            // Recent Section
            val list = recent
            if (!list.isNullOrEmpty()) {
                item {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(list, key = { "${it.repoId}|${it.path}" }) { d ->
                    DocListItem(
                        title = "${d.owner}/${d.repoName}",
                        subtitle = "${d.path}${if (d.title.isNotBlank()) " · ${d.title}" else ""}",
                        icon = Icons.Filled.Description,
                        onClick = { onOpenDoc(d.repoId, d.path) },
                    )
                }
            }

            // Your repos Section
            val onlineRepos = userRepos
            val savedRepos = repos
            if (auth.signedIn && onlineRepos != null) {
                item {
                    Text("Your repos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                if (onlineRepos.isEmpty()) {
                    item {
                        Text("No repositories found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    }
                } else {
                    items(onlineRepos, key = { "${it.owner.login}/${it.name}" }) { r ->
                        DocListItem(
                            title = "${r.owner.login}/${r.name}",
                            subtitle = r.description ?: "Default branch: ${r.defaultBranch}",
                            icon = if (r.isPrivate) Icons.Filled.Lock else Icons.Filled.Folder,
                            onClick = { vm.open("${r.owner.login}/${r.name}", onOpenRepo) },
                        )
                    }
                }
            } else if (!savedRepos.isNullOrEmpty()) {
                item {
                    Text("Your repos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(savedRepos, key = { it.id }) { r ->
                    DocListItem(
                        title = "${r.owner}/${r.name}",
                        subtitle = r.description ?: "Branch ${r.branch}",
                        icon = if (r.isPrivate) Icons.Filled.Lock else Icons.Filled.Folder,
                        onClick = { onOpenRepo(r.id) },
                    )
                }
            }
        }
    }

    if (showSignInSheet) {
        ModalBottomSheet(onDismissRequest = { showSignInSheet = false }) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sign in to GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showSignInSheet = false }) { Icon(Icons.Filled.Close, "Close") }
                }

                Text(
                    "Paste a Personal Access Token to browse and open private repositories. Tokens are stored on this device only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/settings/tokens?type=beta".toUri()))
                        } catch (_: Exception) {}
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Create a token on GitHub", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    singleLine = true,
                    label = { Text("Personal access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = auth.message != null && !auth.signedIn,
                    supportingText = { auth.message?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showSignInSheet = false }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            vm.signIn(tokenInput) {
                                showSignInSheet = false
                                tokenInput = ""
                            }
                        },
                        enabled = tokenInput.isNotBlank() && !auth.busy,
                    ) {
                        if (auth.busy) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Sign in")
                    }
                }
            }
        }
    }
}
