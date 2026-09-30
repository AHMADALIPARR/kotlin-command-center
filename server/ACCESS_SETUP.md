# Cloudflare Access setup — service-token auth for the app

Both tunnel hostnames must sit behind Cloudflare Access so the open internet
cannot reach Ollama or SSH. The Kotlin app authenticates with a **service
token** (Client ID + Client Secret) — programmatic, no browser login on the
phone.

## 1. Create the Access applications

Cloudflare dashboard → **Zero Trust → Access → Applications → Add an application → Self-hosted**.

Create one for each hostname (replace `yourdomain.com` with your domain):

| Application name | Domain |
|---|---|
| command-center-ollama | `ollama-internal.yourdomain.com` |
| command-center-ssh | `ssh.yourdomain.com` |

Session duration: 24h is fine (service tokens are not interactive sessions).

## 2. Create the service token

**Zero Trust → Access → Service Tokens → Create Service Token.**

- Name: `kotlin-command-center`
- It shows a **Client ID** and **Client Secret** exactly once. Copy both now —
  the secret cannot be retrieved later (generate a new token if lost).

## 3. Attach the Allow policy to both applications

For each application → **Policies → Add a policy**:

- Policy name: `app-service-token`
- Action: **Allow**
- Configure rules → **Add require → Service Token** → select `kotlin-command-center`
- (Optional but recommended) Add an `AND` rule: **IP ranges** limited to the
  egress ranges you actually use, if they are stable.

Result: any request without a valid `CF-Access-Client-Id` /
`CF-Access-Client-Secret` pair is rejected at the edge before it ever touches
your tunnel.

## 4. Wire the secrets into the stack

| Secret | Where it goes |
|---|---|
| Client ID + Client Secret | `worker/` via `wrangler secret put CF_CLIENT_ID` / `CF_CLIENT_SECRET` (Worker attaches them when calling the tunnel) |
| Client ID + Client Secret | Kotlin app config (`CC_CF_CLIENT_ID`, `CC_CF_CLIENT_SECRET`) — used on the **SSH WebSocket handshake** (`wss://ssh.yourdomain.com`) |
| `APP_SECRET` (you invent) | `wrangler secret put APP_SECRET` and the app's `CC_APP_SECRET` — authenticates the phone to the Worker |

The phone never talks to `ollama-internal.yourdomain.com` directly; only the
Worker holds the service token for that hostname. The phone uses the service
token only for the SSH WebSocket handshake.

## 5. Rotation

- Service token compromised or phone lost: **Access → Service Tokens → delete**
  the token and create a new one, then `wrangler secret put` the new values and
  update the app config. The old token stops working immediately.
- `APP_SECRET`: pick a fresh 32+ byte random value
  (`openssl rand -hex 32`), rotate the same way.
