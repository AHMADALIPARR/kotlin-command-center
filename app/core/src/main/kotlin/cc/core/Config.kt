/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.core

/**
 * All secrets and endpoints for the command center.
 *
 * Secrets come from the environment (or an env map you supply, e.g. loaded
 * from a `local.properties`-style file on Android). They are NEVER hardcoded
 * and NEVER logged.
 */
data class CommandCenterConfig(
    /** Public Worker gateway URL, e.g. https://api.yourdomain.com */
    val workerUrl: String,
    /** Shared secret the phone presents to the Worker (Bearer token). */
    val appSecret: String,
    /** SSH-over-WebSocket endpoint, e.g. wss://ssh.yourdomain.com */
    val sshWsUrl: String,
    /** Cloudflare Access service token, used on the SSH WS handshake. */
    val cfClientId: String,
    val cfClientSecret: String,
    /** SSH login details for the server behind the tunnel. */
    val sshUser: String,
    val sshHost: String,
    val sshPort: Int = 22,
    /** Path to the SSH private key (OpenSSH format). */
    val sshKeyPath: String,
    /** Path to known_hosts. Host keys are PINNED — see SshClient. */
    val knownHostsPath: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): CommandCenterConfig {
            fun req(name: String): String =
                env[name]?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("missing required config: $name")

            return CommandCenterConfig(
                workerUrl = req("CC_WORKER_URL").trimEnd('/'),
                appSecret = req("CC_APP_SECRET"),
                sshWsUrl = req("CC_SSH_WS_URL"),
                cfClientId = req("CC_CF_CLIENT_ID"),
                cfClientSecret = req("CC_CF_CLIENT_SECRET"),
                sshUser = req("CC_SSH_USER"),
                sshHost = req("CC_SSH_HOST"),
                sshPort = env["CC_SSH_PORT"]?.toIntOrNull() ?: 22,
                sshKeyPath = req("CC_SSH_KEY"),
                knownHostsPath = req("CC_KNOWN_HOSTS"),
            )
        }
    }
}
