package com.netctrl.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Login, Panel }

data class UiState(
    val screen: Screen = Screen.Login,
    val loading: Boolean = false,
    val error: String? = null,
    val token: String = "",
    val serverUrl: String = "",
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val (token, url) = prefs.session.first { it.first != null && it.second != null }
            if (!token.isNullOrBlank() && !url.isNullOrBlank()) {
                _ui.update { it.copy(screen = Screen.Panel, token = token, serverUrl = url) }
            }
        }
    }

    fun login(urlInput: String, password: String) {
        val baseUrl = ServerApi.normalizeUrl(urlInput)
        if (baseUrl == null) {
            _ui.update { it.copy(error = "Некорректный адрес сервера") }
            return
        }
        if (password.isBlank()) {
            _ui.update { it.copy(error = "Введи пароль") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            // OkHttp блокирующий — сеть только с IO-диспетчера
            val result = withContext(Dispatchers.IO) { ServerApi.login(baseUrl, password) }
            when (result) {
                is ServerApi.LoginResult.Ok -> {
                    prefs.save(result.token, baseUrl)
                    _ui.update {
                        it.copy(loading = false, screen = Screen.Panel, token = result.token, serverUrl = baseUrl)
                    }
                }
                is ServerApi.LoginResult.BadPassword ->
                    _ui.update { it.copy(loading = false, error = result.message) }
                is ServerApi.LoginResult.Error ->
                    _ui.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefs.clear()
            _ui.value = UiState()
        }
    }

    /** Токен сменили из панели (смена пароля) — сохраняем новый. */
    fun onTokenChanged(token: String) {
        val current = _ui.value
        if (token.isBlank() || token == current.token) return
        _ui.update { it.copy(token = token) }
        viewModelScope.launch { prefs.save(token, current.serverUrl) }
    }
}