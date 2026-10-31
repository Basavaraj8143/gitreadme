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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserReposState(
    val repos: List<RepoDto>? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class OfflineTaskState(val repoKey: String? = null, val completed: Int = 0, val total: Int = 0)

class OpenGitHubViewModel(private val c: AppContainer) : ViewModel() {
    val recent: StateFlow<List<RecentDoc>?> =
        c.repository.recentDocs(10).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val repos: StateFlow<List<RepositoryEntity>?> =
        c.repository.repositories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _userRepos = MutableStateFlow(UserReposState())
    val userRepos = _userRepos.asStateFlow()
    private var userReposJob: Job? = null

    private val _open = MutableStateFlow(OpenState())
    val open = _open.asStateFlow()
    private val _offlineTask = MutableStateFlow(OfflineTaskState())
    val offlineTask = _offlineTask.asStateFlow()
    val messages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

    val auth = c.repository.authState

    init {
        if (auth.value.signedIn) {
            viewModelScope.launch { c.repository.restoreAuthUsername() }
        }
    }

    fun clearError() { _open.value = OpenState() }

    fun fetchUserRepos() {
        userReposJob?.cancel()
        _userRepos.value = UserReposState(loading = true)
        userReposJob = viewModelScope.launch {
            c.repository.getUserRepos().fold(
                onSuccess = { _userRepos.value = UserReposState(repos = it) },
                onFailure = {
                    _userRepos.value = UserReposState(error = it.message ?: "Could not load repositories.")
                }
            )
        }
    }

    fun clearUserRepos() {
        userReposJob?.cancel()
        _userRepos.value = UserReposState()
    }

    fun open(raw: String, onDone: (String, String?, Boolean) -> Unit) {
        when (val p = RepoInputParser.parse(raw)) {
            is ParseResult.Error -> _open.value = OpenState(error = p.message)
            is ParseResult.Ok -> viewModelScope.launch {
                _open.value = OpenState(loading = true)
                c.repository.openRepository(p.input.owner, p.input.name, p.input.branch).fold(
                    onSuccess = {
                        _open.value = OpenState()
                        onDone(it, p.input.path, p.input.isFile)
                    },
                    onFailure = { _open.value = OpenState(error = it.message ?: "Could not open repository.") },
                )
            }
        }
    }

    fun makeAvailableOffline(owner: String, name: String, branch: String, savedRepoId: String? = null) {
        if (_offlineTask.value.repoKey != null) return
        val repoKey = "$owner/$name@$branch"
        viewModelScope.launch {
            _offlineTask.value = OfflineTaskState(repoKey)
            try {
                val id = savedRepoId ?: c.repository.openRepository(owner, name, branch).getOrElse { throw it }
                val report = c.repository.downloadForOffline(id) { done, total ->
                    _offlineTask.value = OfflineTaskState(repoKey, done, total)
                }
                messages.tryEmit(
                    "Offline download complete: ${report.downloaded} downloaded, " +
                        "${report.alreadyCached} already saved, ${report.failed} unavailable."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.tryEmit(e.message ?: "Could not make this repository available offline.")
            } finally {
                _offlineTask.value = OfflineTaskState()
            }
        }
    }

    fun signIn(token: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            c.repository.signIn(token).fold(
                {
                    onSuccess()
                },
                {},
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenGitHubScreen(
    onBack: () -> Unit,
    onOpenRepo: (String, String) -> Unit,
    onOpenDoc: (String, String) -> Unit,
) {
    val vm = appViewModel { c, _ -> OpenGitHubViewModel(c) }
    val open by vm.open.collectAsStateWithLifecycle()
    val auth by vm.auth.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val repos by vm.repos.collectAsStateWithLifecycle()
    val userRepos by vm.userRepos.collectAsStateWithLifecycle()
    val offlineTask by vm.offlineTask.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuRepoKey by remember { mutableStateOf<String?>(null) }

    var urlInput by rememberSaveable { mutableStateOf("") }
    var showSignInSheet by rememberSaveable { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(auth.signedIn, userRepos.loading, userRepos.repos, userRepos.error) {
        if (auth.signedIn) {
            if (userRepos.repos == null && !userRepos.loading && userRepos.error == null) {
                vm.fetchUserRepos()
            }
        } else {
            vm.clearUserRepos()
        }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val openTarget: (String, String?, Boolean) -> Unit = { repoId, path, isFile ->
        if (isFile && path != null) onOpenDoc(repoId, path)
        else onOpenRepo(repoId, path.orEmpty())
    }
    val submit = {
        vm.open(urlInput, openTarget)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                                Text("@${auth.username ?: "user"}", style = MaterialTheme.typography.labelMedium)
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
            val onlineRepos = userRepos.repos
            val reposError = userRepos.error
            val savedRepos = repos
            auth.message?.takeIf { !auth.signedIn && it.isNotBlank() }?.let { message ->
                item { Text(message, style = MaterialTheme.typography.bodyMedium) }
            }
            if (auth.signedIn) {
                item {
                    Text("Your repos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                if (userRepos.loading) {
                    item { CircularProgressIndicator(Modifier.size(24.dp)) }
                } else if (reposError != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(reposError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = vm::fetchUserRepos) { Text("Retry") }
                        }
                    }
                } else if (onlineRepos.isNullOrEmpty()) {
                    item {
                        Text("No repositories found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    }
                } else {
                    items(onlineRepos, key = { "${it.owner.login}/${it.name}" }) { r ->
                        val repoKey = "${r.owner.login}/${r.name}@${r.defaultBranch}"
                        val isDownloading = offlineTask.repoKey == repoKey
                        DocListItem(
                            title = "${r.owner.login}/${r.name}",
                            subtitle = buildString {
                                append(r.description ?: "Default branch: ${r.defaultBranch}")
                                if (isDownloading) append("\nDownloading for offline · ${offlineTask.completed}/${offlineTask.total}")
                            },
                            icon = if (r.isPrivate) Icons.Filled.Lock else Icons.Filled.Folder,
                            onClick = { vm.open("${r.owner.login}/${r.name}", openTarget) },
                            trailingContent = {
                                if (isDownloading) {
                                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                                } else {
                                    androidx.compose.material3.IconButton(
                                        onClick = { menuRepoKey = repoKey },
                                        enabled = offlineTask.repoKey == null,
                                    ) {
                                        Icon(Icons.Filled.MoreVert, "Actions for ${r.name}")
                                    }
                                    DropdownMenu(
                                        expanded = menuRepoKey == repoKey,
                                        onDismissRequest = { menuRepoKey = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Make available offline") },
                                            leadingIcon = { Icon(Icons.Filled.DownloadForOffline, contentDescription = null) },
                                            onClick = {
                                                menuRepoKey = null
                                                vm.makeAvailableOffline(r.owner.login, r.name, r.defaultBranch)
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            } else if (!savedRepos.isNullOrEmpty()) {
                item {
                    Text("Your repos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(savedRepos, key = { it.id }) { r ->
                    val repoKey = "${r.owner}/${r.name}@${r.branch}"
                    val isDownloading = offlineTask.repoKey == repoKey
                    DocListItem(
                        title = "${r.owner}/${r.name}",
                        subtitle = buildString {
                            append(r.description ?: "Branch ${r.branch}")
                            if (isDownloading) append("\nDownloading for offline · ${offlineTask.completed}/${offlineTask.total}")
                        },
                        icon = if (r.isPrivate) Icons.Filled.Lock else Icons.Filled.Folder,
                        onClick = { onOpenRepo(r.id, "") },
                        trailingContent = {
                            if (isDownloading) {
                                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                            } else {
                                androidx.compose.material3.IconButton(
                                    onClick = { menuRepoKey = repoKey },
                                    enabled = offlineTask.repoKey == null,
                                ) {
                                    Icon(Icons.Filled.MoreVert, "Actions for ${r.name}")
                                }
                                DropdownMenu(
                                    expanded = menuRepoKey == repoKey,
                                    onDismissRequest = { menuRepoKey = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Make available offline") },
                                        leadingIcon = { Icon(Icons.Filled.DownloadForOffline, contentDescription = null) },
                                        onClick = {
                                            menuRepoKey = null
                                            vm.makeAvailableOffline(r.owner, r.name, r.branch, r.id)
                                        },
                                    )
                                }
                            }
                        },
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
