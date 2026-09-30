#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 SnapKitty Collective
#
# Provision cloudflared on the server that runs Ollama:
#   1. installs cloudflared
#   2. creates a tunnel and routes DNS for the API + SSH hostnames
#   3. renders /etc/cloudflared/config.yml from config.yml
#   4. installs + starts a systemd unit (outbound-only: no inbound firewall ports)
#
# Usage:
#   sudo DOMAIN=yourdomain.com TUNNEL_NAME=command-center ./setup.sh
#
# DOMAIN is required and must be YOUR domain. Nothing here phones home;
# cloudflared only ever opens outbound connections to Cloudflare's edge.
set -euo pipefail

DOMAIN="${DOMAIN:-}"
TUNNEL_NAME="${TUNNEL_NAME:-command-center}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "$DOMAIN" ]]; then
  echo "error: set DOMAIN to your real domain, e.g. DOMAIN=example.com $0" >&2
  exit 1
fi
if [[ "$EUID" -ne 0 ]]; then
  echo "error: run as root (needs apt + /etc/cloudflared)" >&2
  exit 1
fi

echo "==> Installing cloudflared"
if ! command -v cloudflared >/dev/null 2>&1; then
  mkdir -p /usr/share/keyrings
  curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg \
    | tee /usr/share/keyrings/cloudflare-main.gpg >/dev/null
  echo 'deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared any main' \
    | tee /etc/apt/sources.list.d/cloudflared.list >/dev/null
  apt-get update -qq && apt-get install -y -qq cloudflared
fi
cloudflared --version

echo "==> Authenticating (opens a browser URL; on a headless box copy ~/.cloudflared/cert.pem from a machine where you ran 'cloudflared tunnel login')"
cloudflared tunnel login

echo "==> Creating tunnel '$TUNNEL_NAME'"
if cloudflared tunnel list 2>/dev/null | grep -q "$TUNNEL_NAME"; then
  echo "    tunnel already exists, reusing"
else
  cloudflared tunnel create "$TUNNEL_NAME"
fi
TUNNEL_ID="$(cloudflared tunnel list --output json 2>/dev/null \
  | python3 -c "import json,sys; print([t['id'] for t in json.load(sys.stdin) if t['name']=='$TUNNEL_NAME'][0])")"
echo "    tunnel id: $TUNNEL_ID"

echo "==> Routing DNS"
cloudflared tunnel route dns "$TUNNEL_ID" "ollama-internal.$DOMAIN" || true
cloudflared tunnel route dns "$TUNNEL_ID" "ssh.$DOMAIN" || true

echo "==> Rendering /etc/cloudflared/config.yml"
mkdir -p /etc/cloudflared
sed -e "s/TUNNEL_UUID/$TUNNEL_ID/g" \
    -e "s/yourdomain\.com/$DOMAIN/g" \
    "$SCRIPT_DIR/config.yml" > /etc/cloudflared/config.yml
chmod 600 /etc/cloudflared/config.yml
echo "    wrote /etc/cloudflared/config.yml"

echo "==> Installing systemd unit"
cat > /etc/systemd/system/cloudflared.service <<'UNIT'
[Unit]
Description=cloudflared tunnel for Kotlin Command Center
After=network-online.target
Wants=network-online.target

[Service]
Type=notify
ExecStart=/usr/bin/cloudflared tunnel --config /etc/cloudflared/config.yml run
Restart=on-failure
RestartSec=5s
# Harden: no new privileges, private /tmp
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now cloudflared
systemctl --no-pager status cloudflared | head -8

echo
echo "==> Done. Next: follow ACCESS_SETUP.md to lock both hostnames behind"
echo "    Cloudflare Access with a service token, then deploy worker/."
