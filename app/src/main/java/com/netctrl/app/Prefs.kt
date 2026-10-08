package com.netctrl.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "netctrl_prefs")

private object Keys {
    val TOKEN = stringPreferencesKey("token")
    val SERVER_URL = stringPreferencesKey("server_url")
}

class Prefs(private val ctx: Context) {
    /** token to serverUrl */
    val session: Flow<Pair<String?, String?>> = ctx.dataStore.data.map {
        it[Keys.TOKEN] to it[Keys.SERVER_URL]
    }

    suspend fun save(token: String, url: String) {
        ctx.dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.SERVER_URL] = url
        }
    }

    suspend fun clear() {
        ctx.dataStore.edit { it.clear() }
    }
}