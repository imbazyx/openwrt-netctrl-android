package com.netctrl.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "netctrl_prefs")

object PrefKeys {
    val TOKEN = stringPreferencesKey("token")
    val SERVER_URL = stringPreferencesKey("server_url")
    val USERNAME = stringPreferencesKey("username")
}

class Prefs(private val ctx: Context) {
    val token: Flow<String?> = ctx.dataStore.data.map { it[PrefKeys.TOKEN] }
    val serverUrl: Flow<String?> = ctx.dataStore.data.map { it[PrefKeys.SERVER_URL] }
    val username: Flow<String?> = ctx.dataStore.data.map { it[PrefKeys.USERNAME] }

    suspend fun save(token: String, url: String, username: String) {
        ctx.dataStore.edit {
            it[PrefKeys.TOKEN] = token
            it[PrefKeys.SERVER_URL] = url
            it[PrefKeys.USERNAME] = username
        }
    }

    suspend fun clear() {
        ctx.dataStore.edit { it.clear() }
    }
}
