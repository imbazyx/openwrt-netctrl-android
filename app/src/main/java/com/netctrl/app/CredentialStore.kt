package com.netctrl.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val prefs = createEncryptedPrefs(context)

    private fun createEncryptedPrefs(context: Context): android.content.SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "netctrl_creds_v1",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Файл повреждён (смена APK подписи) — удаляем и пересоздаём
            android.util.Log.w("CredentialStore",
                "Encrypted prefs corrupted, resetting: ${e.message}")
            context.deleteSharedPreferences("netctrl_creds_v1")
            // Рекурсивный вызов — теперь создаст чистый файл
            createEncryptedPrefs(context)
        }
    }

    fun saveSsh(agentId: String, login: String, password: String) {
        prefs.edit()
            .putString("${agentId}_ssh_l", login)
            .putString("${agentId}_ssh_p", password)
            .apply()
    }

    fun getSshLogin(agentId: String): String = prefs.getString("${agentId}_ssh_l", "") ?: ""
    fun getSshPass(agentId: String): String = prefs.getString("${agentId}_ssh_p", "") ?: ""

    fun saveLuci(agentId: String, login: String, password: String) {
        prefs.edit()
            .putString("${agentId}_luci_l", login)
            .putString("${agentId}_luci_p", password)
            .apply()
    }

    fun getLuciLogin(agentId: String): String = prefs.getString("${agentId}_luci_l", "") ?: ""
    fun getLuciPass(agentId: String): String = prefs.getString("${agentId}_luci_p", "") ?: ""
}
