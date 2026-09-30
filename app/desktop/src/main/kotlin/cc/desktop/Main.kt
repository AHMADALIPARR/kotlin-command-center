/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.desktop

import cc.core.AgentClient
import cc.core.ChatMessage
import cc.core.CommandCenterConfig
import cc.core.SshClient
import kotlinx.coroutines.runBlocking

private val USAGE = """
Kotlin Command Center — terminal edition.

Configuration comes from the environment (never from flags, so secrets
stay out of shell history):
  CC_WORKER_URL      https://api.yourdomain.com        (Worker gateway)
  CC_APP_SECRET      shared secret for the Worker
  CC_SSH_WS_URL      wss://ssh.yourdomain.com          (SSH over WebSocket)
  CC_CF_CLIENT_ID / CC_CF_CLIENT_SECRET               (Access service token)
  CC_SSH_USER / CC_SSH_HOST / CC_SSH_PORT              (server login)
  CC_SSH_KEY         path to SSH private key
  CC_KNOWN_HOSTS     path to known_hosts (host key is pinned)

Commands:
  health                       Check the Worker gateway (no auth needed)
  prompt <model> <text...>     One completion via Ollama
  chat <model>                 Interactive chat (blank line quits)
  ssh <command...>             Run a remote command over SSH-over-WebSocket
""".trimIndent()

fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        println(USAGE)
        return@runBlocking
    }
    val config = try {
        CommandCenterConfig.fromEnv()
    } catch (e: IllegalStateException) {
        System.err.println("configuration error: ${e.message}")
        System.err.println(USAGE)
        return@runBlocking
    }

    when (args[0]) {
        "health" -> {
            val ok = AgentClient(config).health()
            println(if (ok) "gateway: OK" else "gateway: UNREACHABLE")
        }
        "prompt" -> {
            require(args.size >= 3) { "usage: prompt <model> <text...>" }
            val resp = AgentClient(config).prompt(args[1], args.drop(2).joinToString(" "))
            println(resp.response)
        }
        "chat" -> {
            require(args.size >= 2) { "usage: chat <model>" }
            val model = args[1]
            val client = AgentClient(config)
            val history = mutableListOf<ChatMessage>()
            println("chat with $model (blank line quits)")
            while (true) {
                print("you> ")
                val line = readlnOrNull()?.trim().orEmpty()
                if (line.isEmpty()) break
                history += ChatMessage("user", line)
                val resp = client.chat(model, history.toList())
                history += resp.message
                println("agent> ${resp.message.content}")
            }
        }
        "ssh" -> {
            require(args.size >= 2) { "usage: ssh <command...>" }
            val result = SshClient(config).exec(args.drop(1).joinToString(" "))
            if (result.stderr.isNotBlank()) System.err.print(result.stderr)
            print(result.stdout)
            if (result.exitCode != 0) {
                System.err.println("(exit ${result.exitCode})")
            }
        }
        else -> println(USAGE)
    }
}
