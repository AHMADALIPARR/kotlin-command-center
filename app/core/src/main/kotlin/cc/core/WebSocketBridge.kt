/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.core

import com.jcraft.jsch.SocketFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * A [SocketFactory] for JSch that tunnels SSH over a WebSocket.
 *
 * Cloudflare Access can route `ssh://` origins, but Android cannot easily run
 * the `cloudflared` binary — so the app opens `wss://ssh.yourdomain.com`
 * itself (with the Access service-token headers on the WS handshake) and
 * hands JSch a [Socket] whose byte streams are bridged to WebSocket frames:
 *
 *   JSch --bytes--> pipe --> WS binary frames --> cloudflared --> sshd
 *   JSch <--bytes-- pipe <-- WS binary frames <-- cloudflared <-- sshd
 *
 * WebSocket is message-oriented and SSH is stream-oriented; the bridge
 * concatenates incoming binary frames into one continuous stream and emits
 * one binary frame per writer flush batch. Ordering is preserved.
 *
 * JSch assumes a TCP socket (it calls `setTcpNoDelay` and `setSoTimeout` in
 * `Session.connect`), so [WebSocketBackedSocket] overrides those as safe
 * no-ops / stored values instead of throwing "Socket is not connected".
 *
 * @param sessionOpener how the WebSocket session is established; the default
 *   opens a real Ktor `webSocketSession` with [handshakeHeaders]. Tests inject
 *   a fake to avoid needing a live server.
 */
class WebSocketSocketFactory(
    private val webSocketUrl: String,
    private val handshakeHeaders: Map<String, String> = emptyMap(),
    private val httpClient: HttpClient = defaultHttpClient(),
    /** If true, [shutdown] also closes [httpClient]. */
    private val ownsClient: Boolean = false,
    private val sessionOpener: suspend (client: HttpClient, url: String, headers: Map<String, String>) -> ClientWebSocketSession =
        { client, url, headers ->
            client.webSocketSession(url) {
                headers.forEach { (name, value) -> header(name, value) }
            }
        },
) : SocketFactory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Opens the WebSocket (blocking — JSch calls this from its connecting
     * thread, which is already blocking) and returns the bridged socket.
     */
    override fun createSocket(host: String, port: Int): Socket {
        val session = runBlocking {
            sessionOpener(httpClient, webSocketUrl, handshakeHeaders)
        }
        return WebSocketBackedSocket(session, scope)
    }

    override fun getInputStream(socket: Socket): InputStream =
        (socket as WebSocketBackedSocket).bridgedInput

    override fun getOutputStream(socket: Socket): OutputStream =
        (socket as WebSocketBackedSocket).bridgedOutput

    /** Releases the factory's coroutines (and the HTTP client if owned). */
    fun shutdown() {
        scope.cancel("WebSocketSocketFactory.shutdown")
        if (ownsClient) httpClient.close()
    }
}

/**
 * A [Socket] whose streams are piped to/from a WebSocket session.
 *
 * Not a real TCP socket — it only exists so JSch has something to call
 * `getInputStream()`/`getOutputStream()`/`setTcpNoDelay()` on.
 */
class WebSocketBackedSocket internal constructor(
    private val session: ClientWebSocketSession,
    scope: CoroutineScope,
) : Socket() {

    companion object {
        /** Pipe buffer; large enough that SSH window traffic rarely blocks. */
        const val PIPE_SIZE = 65536
    }

    // Inbound: WS reader coroutine -> JSch.
    private val inboundSink = PipedOutputStream()
    val bridgedInput: InputStream = PipedInputStream(inboundSink, PIPE_SIZE)

    // Outbound: JSch -> WS writer coroutine.
    private val outboundSource = PipedInputStream(PIPE_SIZE)
    val bridgedOutput: OutputStream = PipedOutputStream(outboundSource)

    @Volatile private var closed = false
    private var storedSoTimeout: Int = 0

    private val readerJob = scope.launch {
        try {
            for (frame in session.incoming) {
                if (!isActive) break
                val bytes = when (frame) {
                    is Frame.Binary -> frame.readBytes()
                    is Frame.Text -> frame.readText().toByteArray(Charsets.UTF_8)
                    else -> null
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    inboundSink.write(bytes)
                    inboundSink.flush()
                }
                if (frame is Frame.Close) break
            }
        } catch (_: Exception) {
            // WebSocket dropped: fall through and signal EOF to JSch.
        } finally {
            runCatching { inboundSink.close() }
        }
    }

    private val writerJob = scope.launch {
        val buf = ByteArray(32768)
        try {
            while (isActive) {
                // Blocking read on Dispatchers.IO; returns -1 once JSch's
                // write end is closed during socket teardown.
                val n = outboundSource.read(buf)
                if (n < 0) break
                if (n > 0) session.send(buf.copyOf(n))
            }
        } catch (_: Exception) {
            // JSch went away mid-write.
        } finally {
            runCatching { session.close() }
        }
    }

    // -- Socket overrides: JSch's TCP assumptions, made safe -------------

    override fun getInputStream(): InputStream = bridgedInput

    override fun getOutputStream(): OutputStream = bridgedOutput

    /** No Nagle algorithm on a WebSocket; Ktor frames promptly. No-op. */
    override fun setTcpNoDelay(on: Boolean) { /* intentionally no-op */ }

    /** Stored, not applied — there is no underlying TCP socket to tune. */
    override fun setSoTimeout(timeout: Int) {
        storedSoTimeout = timeout
    }

    override fun getSoTimeout(): Int = storedSoTimeout

    /** True once the WS handshake completed and until [close]. */
    override fun isConnected(): Boolean = !closed

    override fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return
        closed = true
        // Unblock the writer (read -> -1) and the reader (channel closes),
        // then stop the coroutines and close the WebSocket.
        runCatching { bridgedOutput.close() }
        runCatching { inboundSink.close() }
        readerJob.cancel()
        writerJob.cancel()
        runCatching { runBlocking { session.close() } }
        runCatching { super.close() }
    }
}
