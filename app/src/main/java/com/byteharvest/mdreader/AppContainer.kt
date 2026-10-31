package com.byteharvest.mdreader

import android.content.Context
import com.byteharvest.mdreader.core.security.TokenStore
import com.byteharvest.mdreader.data.local.AppDatabase
import com.byteharvest.mdreader.data.local.SettingsStore
import com.byteharvest.mdreader.data.remote.GitHubApi
import com.byteharvest.mdreader.data.repository.AppRepository

/** Manual dependency injection. */
class AppContainer(val appContext: Context) {
    val db: AppDatabase = AppDatabase.build(appContext)
    val settings = SettingsStore(appContext)
    val tokenStore = TokenStore(appContext)
    val api = GitHubApi(tokenProvider = { tokenStore.load() })
    val repository = AppRepository(db, api, tokenStore)
}
