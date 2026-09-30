/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 *
 * Kotlin Command Center — Cloudflare Worker API gateway.
 *
 * The phone never talks to Ollama directly. It authenticates to this Worker
 * with `Authorization: Bearer <APP_SECRET>`; the Worker then forwards to the
 * internal tunnel hostname, attaching the Cloudflare Access service token
 * (CF-Access-Client-Id / CF-Access-Client-Secret) so the request passes the
 * Zero Trust screen without a browser login.
 *
 * Secrets: APP_SECRET, CF_CLIENT_ID, CF_CLIENT_SECRET.
 * Set with `wrangler secret put <NAME>` — nothing secret lives in this repo.
 * TUNNEL_BASE is a plain (non-secret) variable: the internal tunnel hostname,
 * which is useless without the Access service token.
 *
 * Rate limiting is an in-memory per-isolate sliding window. That is correct
 * for a single-phone command center; the production upgrade path is a
 * Durable Object keyed by client IP (see note below).
 */

const WINDOW_MS = 60_000; // sliding window
const MAX_REQUESTS = 60;  // per IP per window

// ip -> array of request timestamps (ms). Per-isolate memory only.
const hits = new Map();

function rateLimited(ip) {
  const now = Date.now();
  const recent = (hits.get(ip) || []).filter((t) => now - t < WINDOW_MS);
  recent.push(now);
  hits.set(ip, recent);
  // Occasionally prune dead entries so the map cannot grow unbounded.
  if (hits.size > 10000) {
    for (const [k, v] of hits) {
      if (v.length === 0 || now - v[v.length - 1] > WINDOW_MS) hits.delete(k);
    }
  }
  return recent.length > MAX_REQUESTS;
  // PRODUCTION: replace with a Durable Object (one per client IP) so the
  // limit is enforced globally across all isolates, not per isolate.
}

function jsonError(message, status) {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    // Liveness probe — no auth, so uptime monitors and the app's health
    // check work without secrets.
    if (path === "/health") {
      return new Response(JSON.stringify({ ok: true }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const OLLAMA_ROUTES = ["/api/generate", "/api/chat"];
    if (!OLLAMA_ROUTES.includes(path)) {
      return jsonError("not found", 404);
    }

    // Ollama's API is POST-only; reject anything else explicitly.
    if (request.method !== "POST") {
      return jsonError("method not allowed", 405);
    }

    // 1. App authentication.
    const auth = request.headers.get("Authorization");
    if (!env.APP_SECRET || auth !== `Bearer ${env.APP_SECRET}`) {
      return jsonError("unauthorized", 401);
    }

    // 2. Rate limit (after auth: only authenticated callers consume budget).
    const ip =
      request.headers.get("CF-Connecting-IP") ||
      request.headers.get("X-Forwarded-For") ||
      "unknown";
    if (rateLimited(ip)) {
      return jsonError("rate limited", 429);
    }

    // 3. Forward to the internal tunnel hostname, preserving path, query
    //    and the (possibly streaming) body. Attach the Access service token.
    if (!env.TUNNEL_BASE) {
      return jsonError("gateway misconfigured", 500);
    }
    const upstream = new Request(env.TUNNEL_BASE + path + url.search, {
      method: request.method,
      headers: {
        "Content-Type": "application/json",
        "CF-Access-Client-Id": env.CF_CLIENT_ID || "",
        "CF-Access-Client-Secret": env.CF_CLIENT_SECRET || "",
      },
      body: request.body,
      // Keep the body streaming end-to-end (Ollama stream:true responses
      // flow back to the phone without buffering here).
      duplex: "half",
    });

    const upstreamResp = await fetch(upstream);
    // Stream the upstream response straight back; copy status + headers.
    return new Response(upstreamResp.body, {
      status: upstreamResp.status,
      headers: upstreamResp.headers,
    });
  },
};
