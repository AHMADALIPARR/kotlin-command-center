/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Shared Ktor client: JSON content negotiation + WebSocket support. */
fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(WebSockets)
}

/**
 * Talks to Ollama through the Cloudflare Worker gateway.
 *
 * Every API call carries `Authorization: Bearer <APP_SECRET>`; the Worker
 * verifies it before forwarding to the tunnel. `/health` is the only
 * unauthenticated route (used by uptime checks).
 */
class AgentClient(
    private val config: CommandCenterConfig,
    private val http: HttpClient = defaultHttpClient(),
) {
    /** Unauthenticated liveness probe of the Worker gateway. */
    suspend fun health(): Boolean = try {
        val resp: HealthResponse = http.get("${config.workerUrl}/health").body()
        resp.ok
    } catch (_: Exception) {
        false
    }

    /** Single completion via Ollama /api/generate (non-streaming). */
    suspend fun prompt(model: String, prompt: String): GenerateResponse {
        val resp = http.post("${config.workerUrl}/api/generate") {
            header(HttpHeaders.Authorization, "Bearer ${config.appSecret}")
            contentType(ContentType.Application.Json)
            setBody(GenerateRequest(model, prompt, stream = false))
        }
        check(resp.status.isSuccess()) { "gateway returned ${resp.status}" }
        return resp.body()
    }

    /** Multi-turn chat via Ollama /api/chat (non-streaming). */
    suspend fun chat(model: String, messages: List<ChatMessage>): ChatResponse {
        val resp = http.post("${config.workerUrl}/api/chat") {
            header(HttpHeaders.Authorization, "Bearer ${config.appSecret}")
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(model, messages, stream = false))
        }
        check(resp.status.isSuccess()) { "gateway returned ${resp.status}" }
        return resp.body()
    }
}
