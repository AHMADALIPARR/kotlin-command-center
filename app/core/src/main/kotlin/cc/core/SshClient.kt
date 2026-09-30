/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.core

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SocketFactory
import java.io.ByteArrayOutputStream

/**
 * Runs commands on the server behind the Cloudflare Tunnel, over
 * SSH carried inside a WebSocket (see [WebSocketSocketFactory]).
 *
 * ## Host-key verification (read this)
 *
 * The sketch this was built from used `StrictHostKeyChecking=no`, which
 * silently accepts ANY server key — a textbook man-in-the-middle hole, made
 * worse here because the connection traverses a third-party edge.
 *
 * This client instead requires a `known_hosts` file and sets
 * `StrictHostKeyChecking=yes`:
 * - server key in known_hosts and matching → connect
 * - anything else → [com.jcraft.jsch.JSchException], fail closed
 *
 * Generate the entry once, over a trusted path:
 * `ssh-keyscan -t rsa ssh.yourdomain.com >> ~/.ssh/command_center_known_hosts`
 * (verify the fingerprint out-of-band before trusting it).
 */
class SshClient(
    private val config: CommandCenterConfig,
    private val socketFactory: SocketFactory = WebSocketSocketFactory(
        webSocketUrl = config.sshWsUrl,
        handshakeHeaders = mapOf(
            "CF-Access-Client-Id" to config.cfClientId,
            "CF-Access-Client-Secret" to config.cfClientSecret,
        ),
        ownsClient = true,
    ),
) {
    /**
     * Executes [command] on the remote host and captures its output.
     * Throws on connect/auth/host-key failure or when the command
     * does not finish within [timeoutMs].
     */
    fun exec(command: String, timeoutMs: Long = 30_000): SshResult {
        val jsch = JSch()
        // Throws if the file is missing or malformed — fail closed.
        jsch.setKnownHosts(config.knownHostsPath)
        jsch.addIdentity(config.sshKeyPath)

        val session = jsch.getSession(config.sshUser, config.sshHost, config.sshPort)
        // "yes": unknown/changed host key aborts instead of prompting.
        // Never "no", never "ask" (no UI to answer the prompt on a phone).
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setSocketFactory(socketFactory)
        try {
            session.connect(timeoutMs.toInt())

            val channel = session.openChannel("exec") as ChannelExec
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            channel.outputStream = stdout
            channel.setErrStream(stderr)
            channel.setCommand(command)
            channel.connect(timeoutMs.toInt())
            try {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50)
                }
                check(channel.isClosed) { "remote command did not finish within ${timeoutMs}ms" }
                return SshResult(
                    exitCode = channel.exitStatus,
                    stdout = stdout.toString(Charsets.UTF_8),
                    stderr = stderr.toString(Charsets.UTF_8),
                )
            } finally {
                channel.disconnect()
            }
        } finally {
            session.disconnect()
        }
    }
}
