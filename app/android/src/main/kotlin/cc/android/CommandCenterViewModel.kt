/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.android.ui.UiChatMessage
import cc.core.AgentClient
import cc.core.ChatMessage
import cc.core.CommandCenterConfig
import cc.core.SshClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CommandCenterUiState(
    val gatewayOk: Boolean? = null,
    val chat: List<UiChatMessage> = emptyList(),
    val chatBusy: Boolean = false,
    val sshOutput: String = "",
    val sshBusy: Boolean = false,
    val error: String? = null,
)

/**
 * Owns the [AgentClient] (Ollama via Worker) and [SshClient] (SSH over
 * WebSocket) and exposes them as Compose state. Secrets live in
 * [CommandCenterConfig] — never in the UI state, never logged.
 */
class CommandCenterViewModel(
    private val config: CommandCenterConfig,
    private val model: String = "llama3",
) : ViewModel() {

    private val agent = AgentClient(config)
    private val ssh = SshClient(config)

    private val _state = MutableStateFlow(CommandCenterUiState())
    val state: StateFlow<CommandCenterUiState> = _state.asStateFlow()

    fun checkHealth() {
        viewModelScope.launch {
            _state.update { it.copy(gatewayOk = agent.health(), error = null) }
        }
    }

    fun sendChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.chatBusy) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    chat = it.chat + UiChatMessage("user", trimmed),
                    chatBusy = true,
                    error = null,
                )
            }
            try {
                val history = _state.value.chat.map { m -> ChatMessage(m.role, m.content) }
                val resp = agent.chat(model, history)
                _state.update {
                    it.copy(
                        chat = it.chat + UiChatMessage("assistant", resp.message.content),
                        chatBusy = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(chatBusy = false, error = "agent: ${e.message}") }
            }
        }
    }

    /** Runs a remote command; SSH I/O stays off the main thread. */
    fun runSshCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || _state.value.sshBusy) return
        viewModelScope.launch {
            _state.update { it.copy(sshBusy = true, error = null, sshOutput = "") }
            try {
                val result = withContext(Dispatchers.IO) { ssh.exec(trimmed) }
                val out = buildString {
                    append(result.stdout)
                    if (result.stderr.isNotBlank()) append("\n[stderr]\n").append(result.stderr)
                    append("\n[exit ${result.exitCode}]")
                }
                _state.update { it.copy(sshBusy = false, sshOutput = out) }
            } catch (e: Exception) {
                _state.update { it.copy(sshBusy = false, error = "ssh: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
