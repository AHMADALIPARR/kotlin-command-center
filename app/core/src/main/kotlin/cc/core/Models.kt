/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.core

import kotlinx.serialization.Serializable

// --- Ollama /api/generate ----------------------------------------------------

@Serializable
data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
)

@Serializable
data class GenerateResponse(
    val model: String = "",
    val response: String = "",
    val done: Boolean = false,
)

// --- Ollama /api/chat --------------------------------------------------------

@Serializable
data class ChatMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
)

@Serializable
data class ChatResponse(
    val model: String = "",
    val message: ChatMessage = ChatMessage("assistant", ""),
    val done: Boolean = false,
)

// --- Worker /health ----------------------------------------------------------

@Serializable
data class HealthResponse(val ok: Boolean = false)

// --- SSH exec result ---------------------------------------------------------

data class SshResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
