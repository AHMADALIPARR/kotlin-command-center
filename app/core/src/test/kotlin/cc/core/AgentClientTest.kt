/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private const val TEST_SECRET = "test-secret"

private fun mockClient(): HttpClient {
    val engine = MockEngine {
        val path = it.url.encodedPath
        val authorized = it.headers[HttpHeaders.Authorization] == "Bearer $TEST_SECRET"
        when {
            path == "/health" ->
                respond(
                    """{"ok":true}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            !authorized ->
                respond(
                    """{"error":"unauthorized"}""",
                    HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            path == "/api/generate" ->
                respond(
                    """{"model":"llama3","response":"echo:hello","done":true}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            path == "/api/chat" ->
                respond(
                    """{"model":"llama3","message":{"role":"assistant","content":"msgs:2"},"done":true}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            else ->
                respond(
                    """{"error":"not found"}""",
                    HttpStatusCode.NotFound,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
        }
    }
    return HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
}

private fun testConfig(workerUrl: String = "https://worker.example") =
    CommandCenterConfig(
        workerUrl = workerUrl,
        appSecret = TEST_SECRET,
        sshWsUrl = "wss://ssh.example",
        cfClientId = "id",
        cfClientSecret = "secret",
        sshUser = "u",
        sshHost = "h",
        sshKeyPath = "/nonexistent",
        knownHostsPath = "/nonexistent",
    )

/**
 * Proves AgentClient without any TCP: Ktor's MockEngine stands in for the
 * Worker gateway. Verifies the Authorization header, JSON request/response
 * mapping, the unauthenticated /health route, and 401 handling.
 *
 * (The CIO engine's socket layer is Ktor's own tested code; what this
 * project owns — URLs, headers, payloads — is all covered here.)
 */
class AgentClientTest {

    @Test
    fun `health is true without auth`() = runBlocking {
        val client = AgentClient(testConfig(), mockClient())
        assertTrue(client.health())
    }

    @Test
    fun `health is false when the gateway is down`() = runBlocking {
        val dead = HttpClient(
            MockEngine { throw java.io.IOException("connection refused") }
        )
        val client = AgentClient(testConfig(), dead)
        assertFalse(client.health())
    }

    @Test
    fun `prompt sends bearer auth and parses the response`() = runBlocking {
        val client = AgentClient(testConfig(), mockClient())
        val resp = client.prompt("llama3", "hello")
        assertEquals("llama3", resp.model)
        assertEquals("echo:hello", resp.response)
        assertTrue(resp.done)
    }

    @Test
    fun `chat posts the message history`() = runBlocking {
        var sawBody = ""
        val engine = MockEngine { request ->
            sawBody = (request.body as? TextContent)?.text ?: ""
            respond(
                """{"model":"llama3","message":{"role":"assistant","content":"msgs:2"},"done":true}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = AgentClient(testConfig(), http)
        val resp = client.chat(
            "llama3",
            listOf(ChatMessage("user", "one"), ChatMessage("user", "two")),
        )
        assertEquals("assistant", resp.message.role)
        assertEquals("msgs:2", resp.message.content)
        // The request body really carried both messages as JSON.
        assertTrue(sawBody.contains("\"one\"") && sawBody.contains("\"two\""), sawBody)
    }

    @Test
    fun `wrong app secret is rejected`() {
        runBlocking {
            val bad = AgentClient(
                testConfig().copy(appSecret = "wrong-secret"),
                mockClient(),
            )
            assertFailsWith<IllegalStateException> { bad.prompt("llama3", "hello") }
        }
    }
}
