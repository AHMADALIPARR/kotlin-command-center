# Kotlin Command Center

Talk to your Ollama agents and administer your server from your phone —
without opening a single inbound firewall port.

```
 ┌──────────┐   Bearer APP_SECRET   ┌──────────────────┐  Access svc token  ┌────────────────┐
 │  Kotlin  │ ───────────────────▶ │  Cloudflare      │ ──────────────────▶ │  cloudflared   │──▶ Ollama :11434
 │  app     │   https://api.       │  Worker gateway  │  https://ollama-    │  (outbound     │
 │ (:core / │   yourdomain.com     │  worker/         │   internal.         │   only, no     │
 │ :android │                      │                  │   yourdomain.com    │   inbound      │──▶ sshd :22
 │ :desktop)│ ───────────────────▶ │                  │ ──────────────────▶ │   ports)       │
 └──────────┘   wss://ssh.         └──────────────────┘  Access svc token  └────────────────┘
                yourdomain.com        (WS handshake)       on both hostnames
                + CF-Access headers
```

How the pieces fit:

- **Server** (`server/`): `cloudflared` opens *outbound-only* tunnels for
  Ollama (`ollama-internal.yourdomain.com → localhost:11434`) and SSH
  (`ssh.yourdomain.com → ssh://localhost:22`). No inbound ports, ever.
- **Worker gateway** (`worker/`): the only thing the phone calls for
  inference. Verifies `Authorization: Bearer <APP_SECRET>`, rate-limits per
  IP, then forwards to the tunnel with the Cloudflare Access service token
  attached. `/health` is unauthenticated; unknown routes 404; non-POST API
  calls 405.
- **Kotlin `:core`** (`app/core/`): Ktor CIO client for the Worker
  (`AgentClient`), plus a real JSch `SocketFactory` that bridges WebSocket
  frames to stream-oriented SSH over piped streams (`WebSocketBridge.kt`),
  with `StrictHostKeyChecking=yes` against a pinned `known_hosts`
  (`SshClient`).
- **`:desktop`** (`app/desktop/`): terminal CLI (`health`, `prompt`, `chat`,
  `ssh`) for testing the whole stack from a laptop.
- **`:android`** (`app/android/`): Compose UI (agent chat + server admin
  tabs), source-complete.

## Setup order

1. **Server** — on the box that runs Ollama:
   `sudo DOMAIN=yourdomain.com ./server/setup.sh`
   (installs cloudflared, creates the tunnel, routes DNS, installs the
   systemd unit). Then follow `server/ACCESS_SETUP.md` to put both hostnames
   behind Cloudflare Access with a **service token** (Allow policy).
2. **Worker** — `cd worker && wrangler secret put APP_SECRET` (+
   `CF_CLIENT_ID`, `CF_CLIENT_SECRET`), set `TUNNEL_BASE` in
   `wrangler.toml`, then `wrangler deploy`.
3. **App** — fill the `CC_*` env vars (see `:desktop` usage), or the
   Android settings screen once it exists:
   `CC_WORKER_URL`, `CC_APP_SECRET`, `CC_SSH_WS_URL`,
   `CC_CF_CLIENT_ID`, `CC_CF_CLIENT_SECRET`,
   `CC_SSH_USER`, `CC_SSH_HOST`, `CC_SSH_PORT`, `CC_SSH_KEY`,
   `CC_KNOWN_HOSTS` (generate with
   `ssh-keyscan -t rsa ssh.yourdomain.com`, verify the fingerprint
   out-of-band first).
4. Build: `./gradlew :core:test :desktop:build` (needs the Android SDK only
   for `:android`; it is excluded from the build until then).

## Security model

- The phone never reaches Ollama directly; the Worker is the single choke
  point and it demands the app secret on every API call.
- Both tunnel hostnames require the Cloudflare Access service token at the
  edge — the open internet gets a 403 before touching your server.
- SSH host keys are **pinned**: `StrictHostKeyChecking=yes` with a
  `known_hosts` you generated. Unknown or changed keys abort the connection.
  (The sketch this replaced used `no` — that was fixed, not inherited.)
- Secrets live in environment / Keystore-backed storage, never in code,
  flags, shell history, or the repo. Nothing in this tree is a real secret:
  domains, UUIDs, and tokens here are placeholders.

## Rotation

- **Service token** (phone lost / token leaked): Access → Service Tokens →
  delete `kotlin-command-center`, create a new one, `wrangler secret put`
  the new `CF_CLIENT_ID`/`CF_CLIENT_SECRET`, update the app. The old token
  dies immediately.
- **APP_SECRET**: `openssl rand -hex 32`, then `wrangler secret put
  APP_SECRET` + update the app config.
- **SSH key**: rotate like any SSH key; re-run `ssh-keyscan` only if the
  *host* key legitimately changed (and verify the new fingerprint).

## Verified here vs. needs your hardware

Verified on this build machine (2026-09-30):

- `:core` main + test sources compile clean (kotlinc 2.0.21).
- **8/8 unit tests pass**: Worker health/prompt/chat/auth-failure via
  Ktor MockEngine; 256 KiB corruption-free round trip through the
  WebSocket↔stream bridge; handshake URL/headers; a real JSch instance
  emitting its `SSH-2.0-` banner through the bridge.
- `:desktop` CLI compiles and runs (usage/help path exercised).
- `node --check worker/worker.js` passes.

Not verifiable here (no such infrastructure on this box):

- Real Cloudflare account, tunnel, DNS, and Access policies.
- Real Ollama behind the tunnel; real sshd behind the SSH hostname.
- `:android` compilation — no Android SDK here. The module is excluded
  from `settings.gradle.kts`; add `include(":android")` on a machine with
  the SDK. Android secrets must move to EncryptedSharedPreferences before
  shipping (see `app/android/README.md`).
- `./gradlew` itself: this sandbox intercepts JVM TCP loopback (Gradle
  daemon handshake fails with the sandbox's policy banner), so the wrapper
  could not be generated here. The Gradle scripts are standard; run
  `./gradlew :core:test :desktop:build` on your own machine.

## License

AGPLv3 — see `LICENSE`. Every source file carries an SPDX header; the
gate is: `grep -rL "SPDX-License-Identifier: AGPL-3.0-or-later" <sources>`.
