package com.netctrl.app

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Клиент панели NetCtrl (openwrt-netctrl, server/server.js).
 *
 * Контракт: POST /api/auth/login {password} -> {ok, token},
 * дальше токен едет в заголовке x-auth-token.
 */
object ServerApi {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Нормализует то, что ввёл пользователь, в базовый URL без слэша на конце. */
    fun normalizeUrl(input: String): String? {
        val raw = input.trim().trimEnd('/')
        if (raw.isEmpty()) return null
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        return runCatching { withScheme.toHttpUrlOrNull() }.getOrNull()?.toString()?.trimEnd('/')
    }

    sealed interface LoginResult {
        data class Ok(val token: String) : LoginResult
        data class BadPassword(val message: String) : LoginResult
        data class Error(val message: String) : LoginResult
    }

    fun login(baseUrl: String, password: String): LoginResult {
        return try {
            val body = JSONObject().put("password", password).toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("$baseUrl/api/auth/login")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> LoginResult.BadPassword("Неверный пароль")
                    !response.isSuccessful -> LoginResult.Error("Сервер ответил ${response.code}")
                    else -> {
                        val token = runCatching { JSONObject(text).optString("token") }.getOrDefault("")
                        if (token.isNotEmpty()) LoginResult.Ok(token)
                        else LoginResult.Error("Сервер не вернул токен")
                    }
                }
            }
        } catch (e: IOException) {
            LoginResult.Error("Сервер недоступен: ${e.message ?: "проверь адрес"}")
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Ошибка подключения")
        }
    }
}