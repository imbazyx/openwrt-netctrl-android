package com.netctrl.app

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private val Context.agentLocalStore by preferencesDataStore("agent_local_v2")

data class AgentLocalSettings(
    val ip: String = "",
    val sshPort: Int = 22,
    val luciPort: Int = 80,
    val physicalAddress: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val heartbeatInterval: Int = 30
)

class AgentLocalPrefs(private val context: Context) {
    private val gson = Gson()

    private fun key(agentId: String) = stringPreferencesKey("agent_$agentId")

    suspend fun load(agentId: String): AgentLocalSettings {
        val prefs = context.agentLocalStore.data.first()
        val raw = prefs[key(agentId)] ?: return AgentLocalSettings()
        return try { gson.fromJson(raw, AgentLocalSettings::class.java) } catch (_: Exception) { AgentLocalSettings() }
    }

    suspend fun save(agentId: String, settings: AgentLocalSettings) {
        context.agentLocalStore.edit { prefs ->
            prefs[key(agentId)] = gson.toJson(settings)
        }
    }

    suspend fun loadAll(agentIds: List<String>): Map<String, AgentLocalSettings> {
        val prefs = context.agentLocalStore.data.first()
        return agentIds.associateWith { id ->
            val raw = prefs[key(id)] ?: return@associateWith AgentLocalSettings()
            try { gson.fromJson(raw, AgentLocalSettings::class.java) } catch (_: Exception) { AgentLocalSettings() }
        }
    }
}
