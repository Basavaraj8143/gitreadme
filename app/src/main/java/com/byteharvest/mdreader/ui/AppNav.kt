package com.byteharvest.mdreader.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.byteharvest.mdreader.core.importLocalMarkdown
import com.byteharvest.mdreader.data.local.LOCAL_REPOSITORY_ID
import com.byteharvest.mdreader.ui.bookmarks.BookmarksScreen
import com.byteharvest.mdreader.ui.browser.BrowserScreen
import com.byteharvest.mdreader.ui.home.HomeScreen
import com.byteharvest.mdreader.ui.opengithub.OpenGitHubScreen
import com.byteharvest.mdreader.ui.reader.ReaderScreen
import com.byteharvest.mdreader.ui.repositories.RepositoriesScreen
import com.byteharvest.mdreader.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val BOOKMARKS = "bookmarks"
    const val SETTINGS = "settings"
    const val REPOS = "repositories"
    const val OPEN_GITHUB = "open_github"
    const val BROWSER = "browser/{repoId}?dir={dir}"
    const val READER = "reader/{repoId}/{path}"
    fun browser(repoId: String, dir: String = "") = "browser/${Uri.encode(repoId)}?dir=${Uri.encode(dir)}"
    fun reader(repoId: String, path: String) = "reader/${Uri.encode(repoId)}/${Uri.encode(path)}"
    fun openGitHub() = OPEN_GITHUB
}

@Composable
fun AppNav(externalDocumentUri: Uri? = null) {
    val nav = rememberNavController()
    val context = LocalContext.current

    LaunchedEffect(externalDocumentUri) {
        externalDocumentUri?.let { uri ->
            try {
                val file = importLocalMarkdown(context, uri)
                nav.navigate(Routes.reader(LOCAL_REPOSITORY_ID, file.absolutePath))
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Could not open Markdown file: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val strArg = { name: String -> navArgument(name) { type = NavType.StringType } }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(animationSpec = tween(180)) },
        exitTransition = { fadeOut(animationSpec = tween(140)) },
        popEnterTransition = { fadeIn(animationSpec = tween(180)) },
        popExitTransition = { fadeOut(animationSpec = tween(140)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenGitHub = { nav.navigate(Routes.openGitHub()) },
                onOpenDoc = { id, p -> nav.navigate(Routes.reader(id, p)) },
                onBookmarks = { nav.navigate(Routes.BOOKMARKS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.OPEN_GITHUB) {
            OpenGitHubScreen(
                onBack = { nav.popBackStack() },
                onOpenRepo = { id, dir -> nav.navigate(Routes.browser(id, dir)) },
                onOpenDoc = { id, p -> nav.navigate(Routes.reader(id, p)) },
            )
        }
        composable(
            Routes.BROWSER,
            arguments = listOf(strArg("repoId"), navArgument("dir") { type = NavType.StringType; defaultValue = "" }),
        ) {
            BrowserScreen(
                onBack = { nav.popBackStack() },
                onOpenDir = { id, dir -> nav.navigate(Routes.browser(id, dir)) },
                onOpenDoc = { id, p -> nav.navigate(Routes.reader(id, p)) },
                onOpenRepo = { nav.navigate(Routes.browser(it)) },
            )
        }
        composable(Routes.READER, arguments = listOf(strArg("repoId"), strArg("path"))) {
            ReaderScreen(onBack = { nav.popBackStack() }, onOpenDoc = { id, p -> nav.navigate(Routes.reader(id, p)) })
        }
        composable(Routes.REPOS) {
            RepositoriesScreen(onBack = { nav.popBackStack() }, onOpenRepo = { nav.navigate(Routes.browser(it)) })
        }
        composable(Routes.BOOKMARKS) {
            BookmarksScreen(
                onBack = { nav.popBackStack() },
                onOpenDoc = { id, p -> nav.navigate(Routes.reader(id, p)) },
                onOpenRepo = { nav.navigate(Routes.browser(it)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onRepos = { nav.navigate(Routes.REPOS) })
        }
    }
}
