/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.core

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpHeaders
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.util.Properties
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * A fake [ClientWebSocketSession] that echoes every binary/text frame back to
 * the client, standing in for the cloudflared ssh-over-websocket tunnel.
 * Records all bytes it received so tests can assert on them.
 */
private class FakeEchoWebSocketSession : ClientWebSocketSession {
    val received = ByteArrayOutputStream()
    private val incomingChannel = Channel<Frame>(Channel.UNLIMITED)

    override var masking: Boolean = true
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val incoming: ReceiveChannel<Frame> = incomingChannel
    override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
    override val call: HttpClientCall
        get() = throw UnsupportedOperationException("fake session has no call")

    override suspend fun send(frame: Frame) {
        when (frame) {
            is Frame.Binary, is Frame.Text -> {
                received.write(frame.data)
                // Echo, exactly like the tunnel would return the SSH server's reply.
                incomingChannel.send(Frame.Binary(true, frame.data))
            }
            is Frame.Close -> incomingChannel.close()
            else -> Unit
        }
    }

    override suspend fun flush() = Unit

    override fun terminate() {
        incomingChannel.close()
    }
}

/** A client whose session opener can only be the injected fake. */
private fun dummyClient(): HttpClient =
    HttpClient(MockEngine { error("no real HTTP in bridge tests") })

/**
 * Proves the WebSocket↔stream bridge without any TCP: the fake session echoes
 * frames, and the test drives the socket end the way JSch would. Covers the
 * 256 KiB corruption-free round trip, the handshake URL/headers the factory
 * passes to the session opener, and a real JSch instance emitting its
 * SSH-2.0 banner through the bridge.
 */
class WebSocketBridgeTest {

    @Test
    fun `factory passes url and access headers to the session opener`() {
        var seenUrl = ""
        var seenHeaders: Map<String, String> = emptyMap()
        val factory = WebSocketSocketFactory(
            webSocketUrl = "wss://ssh.example/ssh",
            handshakeHeaders = mapOf(
                HttpHeaders.Authorization to "Bearer test-secret",
                "CF-Access-Client-Id" to "test-client-id",
                "CF-Access-Client-Secret" to "test-client-secret",
            ),
            httpClient = dummyClient(),
            sessionOpener = { _, url, headers ->
                seenUrl = url
                seenHeaders = headers
                FakeEchoWebSocketSession()
            },
        )
        val socket = factory.createSocket("ignored", 0)
        socket.close()
        assertEquals("wss://ssh.example/ssh", seenUrl)
        assertEquals("test-client-id", seenHeaders["CF-Access-Client-Id"])
        assertEquals("test-client-secret", seenHeaders["CF-Access-Client-Secret"])
        assertEquals("Bearer test-secret", seenHeaders[HttpHeaders.Authorization])
    }

    @Test
    fun `socket round-trips 256 KiB through the bridge without corruption`() {
        val factory = WebSocketSocketFactory(
            webSocketUrl = "wss://ssh.example/ssh",
            httpClient = dummyClient(),
            sessionOpener = { _, _, _ -> FakeEchoWebSocketSession() },
        )
        val socket = factory.createSocket("ignored", 0)
        try {
            val payload = Random(42).nextBytes(256 * 1024)
            socket.getOutputStream().write(payload)
            socket.getOutputStream().flush()
            val echoed = socket.getInputStream().readNBytes(payload.size)
            assertContentEquals(payload, echoed)
        } finally {
            socket.close()
        }
    }

    @Test
    fun `real JSch emits its SSH banner through the bridge`() {
        val sessions = mutableListOf<FakeEchoWebSocketSession>()
        val factory = WebSocketSocketFactory(
            webSocketUrl = "wss://ssh.example/ssh",
            httpClient = dummyClient(),
            sessionOpener = { _, _, _ ->
                FakeEchoWebSocketSession().also { sessions += it }
            },
        )
        val socket = factory.createSocket("ignored", 0)
        try {
            val jsch = JSch()
            val session = jsch.getSession("user", "ignored-host", 0)
            session.setConfig(Properties().apply { setProperty("StrictHostKeyChecking", "no") })
            session.setSocketFactory(object : com.jcraft.jsch.SocketFactory {
                override fun createSocket(host: String, port: Int): Socket = socket
                override fun getInputStream(s: Socket) = s.getInputStream()
                override fun getOutputStream(s: Socket) = s.getOutputStream()
            })
            session.setTimeout(10_000)
            // The fake is not an SSH server: the handshake MUST fail — but only
            // after JSch has pushed its real "SSH-2.0-..." banner through the
            // bridge, which is what this test proves.
            assertFailsWith<JSchException> { session.connect(10_000) }
            val sent = sessions.single().received.toByteArray().toString(Charsets.UTF_8)
            assertTrue(sent.startsWith("SSH-2.0-"), "no SSH banner seen, got: ${sent.take(40)}")
        } finally {
            socket.close()
        }
    }
}
