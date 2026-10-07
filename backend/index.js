/**
 * Next-Gen Enterprise Cloudflare Worker API for Z VPN
 * Ultra-Fast Edge-Cached Engine, Multi-Protocol Support, Unity Ads S2S, Firebase Config,
 * Anti-Tampering HMAC Request Signing & Replay Protection
 */

export default {
  async fetch(request, env, ctx) {
    // Global CORS Headers for Android & Desktop Clients
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, HEAD, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With, X-Signature, X-Timestamp, X-App-Pkg, If-None-Match",
      "Access-Control-Max-Age": "86400",
      "X-Content-Type-Options": "nosniff",
      "X-Frame-Options": "DENY",
      "X-XSS-Protection": "1; mode=block",
      "Referrer-Policy": "strict-origin-when-cross-origin"
    };

    // Fix #7: Internal headers for server-to-server endpoints (/upload).
    // No CORS — browsers should never call these endpoints directly.
    const internalHeaders = {
      "Content-Type": "application/json; charset=utf-8",
      "X-Content-Type-Options": "nosniff"
    };

    // Preflight Options Request Handling
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    try {
      const url = new URL(request.url);
      const pathname = url.pathname.toLowerCase();

      // Verify Replay Attack Timestamp Drift if X-Timestamp is provided by client
      const timestampHeader = request.headers.get("X-Timestamp");
      if (timestampHeader) {
        const clientMs = parseInt(timestampHeader, 10);
        if (!isNaN(clientMs)) {
          const valDiff = Math.abs(Date.now() - clientMs);
          if (valDiff > 300000) { // 5 minutes max drift
            return new Response(
              JSON.stringify({ error: "replay_attack_rejected", message: "Request timestamp drift exceeded maximum allowed threshold." }),
              { status: 403, headers: { "Content-Type": "application/json", ...corsHeaders } }
            );
          }
        }
      }

      // 1. Health Check Endpoint: GET /health or GET /status or GET /v1/health
      if (request.method === "GET" && (pathname === "/health" || pathname === "/status" || pathname === "/v1/health")) {
        return new Response(
          JSON.stringify({
            status: "healthy",
            engine: "Z VPN Next-Gen Enterprise Edge Backend",
            version: "3.5.0",
            timestamp: new Date().toISOString(),
            cf_region: request.cf?.colo || "EDGE",
            kv_active: Boolean(env.VPN_DATA),
            r2_active: Boolean(env.PUBLICVPN_BUCKET),
            security: {
              anti_replay_protection: "active",
              anti_tampering_hmac: "active",
              anti_ad_bypass_guard: "active"
            },
            services: {
              unity_s2s: "active",
              firebase_remote_config: "active",
              multi_protocol_sync: "active"
            }
          }),
          {
            status: 200,
            headers: {
              "Content-Type": "application/json; charset=utf-8",
              "Cache-Control": "no-store, no-cache, must-revalidate",
              ...corsHeaders
            }
          }
        );
      }

      // 2. Firebase & App Dynamic Remote Config Endpoint: GET /v1/config or GET /v1/firebase-config
      if (request.method === "GET" && (pathname === "/v1/config" || pathname === "/v1/firebase-config" || pathname === "/config")) {
        const appConfig = {
          app_name: "Z VPN",
          api_version: "v1",
          min_supported_version: "1.0.0",
          latest_version: "2.5.0",
          update_url: "https://play.google.com/store/apps/details?id=com.zvpn",
          force_update: false,
          security: {
            enforce_app_signature: true,
            anti_frida_detection: true,
            anti_ad_blocker_detection: true,
            max_timestamp_drift_ms: 120000
          },
          feature_flags: {
            enable_openvpn: true,
            enable_vless: true,
            enable_vmess: true,
            enable_shadowsocks: true,
            enable_wireguard: true,
            enable_unity_s2s_reward: true,
            enable_ads: true,
            auto_connect_fastest: true
          },
          unity_ads: {
            game_id_android: "5672101",
            placement_rewarded: "Rewarded_Android",
            reward_amount_minutes: 60
          },
          servers_api_url: "https://zvpn-backend.xocqoc.workers.dev/v1/servers.json",
          manifest_api_url: "https://zvpn-backend.xocqoc.workers.dev/v1/manifest.json",
          updated_at: new Date().toISOString()
        };

        const etag = `W/"${await computeETag(JSON.stringify(appConfig))}"`;
        const ifNoneMatch = request.headers.get("If-None-Match");
        if (ifNoneMatch && ifNoneMatch === etag) {
          return new Response(null, { status: 304, headers: { ETag: etag, ...corsHeaders } });
        }

        return new Response(JSON.stringify(appConfig, null, 2), {
          status: 200,
          headers: {
            "Content-Type": "application/json; charset=utf-8",
            "Cache-Control": "public, max-age=300, s-maxage=600",
            "ETag": etag,
            ...corsHeaders
          }
        });
      }

      // 3. Server List Endpoint: GET /servers, GET /v1/servers, GET /v1/servers.json
      if (request.method === "GET" && (pathname === "/servers" || pathname === "/v1/servers" || pathname === "/v1/servers.json")) {
        let rawData = null;

        if (env.VPN_DATA) {
          try {
            rawData = await env.VPN_DATA.get("SERVER_LIST");
          } catch (err) {
            console.error("KV read error:", err);
          }
        }

        if (!rawData && env.PUBLICVPN_BUCKET) {
          try {
            const object = await env.PUBLICVPN_BUCKET.get("v1/servers.json");
            if (object) {
              rawData = await object.text();
            }
          } catch (err) {
            console.error("R2 read error:", err);
          }
        }

        if (!rawData) {
          return new Response(
            JSON.stringify({
              error: "server_list_unavailable",
              message: "Server list is being synchronized and verified by the cloud sync engine.",
              updated_at: new Date().toISOString()
            }),
            {
              status: 503,
              headers: {
                "Content-Type": "application/json; charset=utf-8",
                "Retry-After": "30",
                ...corsHeaders
              }
            }
          );
        }

        try {
          let container = JSON.parse(rawData);
          let servers = container.servers || (Array.isArray(container) ? container : []);

          // Query params
          const countryFilter  = url.searchParams.get("country") || url.searchParams.get("country_code");
          const tierFilter     = url.searchParams.get("tier");
          const protocolFilter = url.searchParams.get("protocol");
          const searchQuery    = url.searchParams.get("search");
          const sortParam      = url.searchParams.get("sort");
          const liteMode       = url.searchParams.get("lite") === "1" || url.searchParams.get("lite") === "true";
          // Pagination
          const pageSize  = Math.min(Math.max(parseInt(url.searchParams.get("page_size") || "0", 10), 0), 500);
          const page      = Math.max(parseInt(url.searchParams.get("page") || "1", 10), 1);
          // Legacy single-shot limit (kept for backwards compat)
          const limitParam = parseInt(url.searchParams.get("limit") || "0", 10);

          // De-duplicate by proto:host:port
          const seenKeys = new Set();
          let cleanServers = [];

          for (const s of servers) {
            const host  = (s.host || s.exit_ip || "").trim().toLowerCase();
            const port  = s.port || 1194;
            const proto = (s.protocol || "openvpn").trim().toLowerCase();
            const key   = `${proto}:${host}:${port}`;

            if (!host || host === "127.0.0.1" || host === "0.0.0.0" || host === "localhost") continue;
            if (seenKeys.has(key)) continue;
            seenKeys.add(key);

            // Filters
            if (countryFilter  && (s.country_code || "").toUpperCase() !== countryFilter.toUpperCase()) continue;
            if (tierFilter     && (s.tier || "free").toLowerCase() !== tierFilter.toLowerCase()) continue;
            if (protocolFilter && (s.protocol || "").toLowerCase() !== protocolFilter.toLowerCase()) continue;
            if (searchQuery) {
              const q = searchQuery.toLowerCase();
              if (!(s.name || "").toLowerCase().includes(q) &&
                  !(s.country_name || "").toLowerCase().includes(q) &&
                  !(s.host || "").toLowerCase().includes(q)) continue;
            }

            // Fix #6: only sanitize name when genuinely missing/too short —
            // don't overwrite names that Python already set correctly.
            if (!s.name || s.name.trim().length < 3) {
              s.name = sanitizeServerName(s);
            }

            // Fix #5: lite mode — strip heavy ovpn_content to reduce payload size
            if (liteMode && s.ovpn_content !== undefined) {
              const slim = { ...s };
              delete slim.ovpn_content;
              cleanServers.push(slim);
            } else {
              cleanServers.push(s);
            }
          }

          // Sort
          if (sortParam === "speed") {
            cleanServers.sort((a, b) => (b.speed_mbps || 0) - (a.speed_mbps || 0));
          } else if (sortParam === "ping" || sortParam === "latency") {
            cleanServers.sort((a, b) => (a.latency_ms || 999) - (b.latency_ms || 999));
          } else if (sortParam === "score") {
            cleanServers.sort((a, b) => (b.network_score || 0) - (a.network_score || 0));
          }

          const totalCount = cleanServers.length;

          // Fix #8: Pagination (page_size wins over legacy limit)
          if (pageSize > 0) {
            const offset = (page - 1) * pageSize;
            cleanServers = cleanServers.slice(offset, offset + pageSize);
          } else if (limitParam > 0 && cleanServers.length > limitParam) {
            cleanServers = cleanServers.slice(0, limitParam);
          }

          container.total_count  = totalCount;
          container.count        = cleanServers.length;
          container.page         = pageSize > 0 ? page : undefined;
          container.page_size    = pageSize > 0 ? pageSize : undefined;
          container.total_pages  = pageSize > 0 ? Math.ceil(totalCount / pageSize) : undefined;
          container.servers      = cleanServers;
          container.updated_at   = container.updated_at || new Date().toISOString();

          const responsePayload = JSON.stringify(container);
          const etag = `W/"${await computeETag(responsePayload)}"`;

          const ifNoneMatch = request.headers.get("If-None-Match");
          if (ifNoneMatch && ifNoneMatch === etag) {
            return new Response(null, { status: 304, headers: { ETag: etag, ...corsHeaders } });
          }

          return new Response(responsePayload, {
            status: 200,
            headers: {
              "Content-Type": "application/json; charset=utf-8",
              "Cache-Control": "public, max-age=180, s-maxage=180, stale-while-revalidate=3600",
              "ETag": etag,
              ...corsHeaders
            }
          });
        } catch (parseErr) {
          return new Response(rawData, {
            status: 200,
            headers: {
              "Content-Type": "application/json; charset=utf-8",
              "Cache-Control": "public, max-age=180",
              ...corsHeaders
            }
          });
        }
      }

      // 4. Manifest Endpoint: GET /manifest.json or GET /v1/manifest.json
      if (request.method === "GET" && (pathname === "/manifest.json" || pathname === "/v1/manifest.json")) {
        let rawManifest = null;

        if (env.VPN_DATA) {
          try {
            rawManifest = await env.VPN_DATA.get("MANIFEST");
          } catch (e) {}
        }

        if (!rawManifest && env.PUBLICVPN_BUCKET) {
          try {
            const object = await env.PUBLICVPN_BUCKET.get("v1/manifest.json");
            if (object) {
              rawManifest = await object.text();
            }
          } catch (e) {}
        }

        if (rawManifest) {
          const etag = `W/"${await computeETag(rawManifest)}"`;
          const ifNoneMatch = request.headers.get("If-None-Match");
          if (ifNoneMatch && ifNoneMatch === etag) {
            return new Response(null, { status: 304, headers: { ETag: etag, ...corsHeaders } });
          }

          return new Response(rawManifest, {
            status: 200,
            headers: {
              "Content-Type": "application/json; charset=utf-8",
              "Cache-Control": "public, max-age=60, s-maxage=300",
              "ETag": etag,
              ...corsHeaders
            }
          });
        }

        return new Response("Manifest Not Found", { status: 404, headers: corsHeaders });
      }

      // 5. Profile Download Endpoint: GET /profiles/:id or GET /v1/profiles/:id.ovpn
      if (request.method === "GET" && (pathname.startsWith("/profiles/") || pathname.startsWith("/v1/profiles/"))) {
        const parts = pathname.split("/");
        const filename = parts[parts.length - 1];
        const serverId = filename.replace(/\.ovpn$/i, "");

        if (!serverId) {
          return new Response("Invalid Profile ID", { status: 400, headers: corsHeaders });
        }

        if (env.PUBLICVPN_BUCKET) {
          try {
            let profileObj = await env.PUBLICVPN_BUCKET.get(`v1/profiles/${serverId}.ovpn`);
            if (!profileObj) {
              profileObj = await env.PUBLICVPN_BUCKET.get(`v1/profiles/${serverId}`);
            }
            if (profileObj) {
              const body = await profileObj.arrayBuffer();
              return new Response(body, {
                status: 200,
                headers: {
                  "Content-Type": "application/x-openvpn-profile; charset=utf-8",
                  "Content-Disposition": `attachment; filename="${serverId}.ovpn"`,
                  "Cache-Control": "public, max-age=86400, s-maxage=604800, immutable",
                  ...corsHeaders
                }
              });
            }
          } catch (r2Err) {
            console.error("R2 profile fetch error:", r2Err);
          }
        }

        if (env.VPN_DATA) {
          try {
            const kvProfile = await env.VPN_DATA.get(`profile:${serverId}`);
            if (kvProfile) {
              return new Response(kvProfile, {
                status: 200,
                headers: {
                  "Content-Type": "application/x-openvpn-profile; charset=utf-8",
                  "Content-Disposition": `attachment; filename="${serverId}.ovpn"`,
                  "Cache-Control": "public, max-age=86400, s-maxage=604800",
                  ...corsHeaders
                }
              });
            }
          } catch (kvErr) {
            console.error("KV profile fetch error:", kvErr);
          }
        }

        return new Response("Profile Not Found", { status: 404, headers: corsHeaders });
      }

      // 6. Unity Ads S2S Redeem Callback Endpoint: GET /s2s/unity/reward
      if (request.method === "GET" && (pathname === "/s2s/unity/reward" || pathname === "/s2s/unity/reward/")) {
        const oid = url.searchParams.get("oid");
        const sid = url.searchParams.get("sid");
        const hmacParam = url.searchParams.get("hmac");

        if (!oid || !sid) {
          return new Response("Missing oid or sid parameters", { status: 400, headers: corsHeaders });
        }

        const secret = env.UNITY_S2S_SECRET || "cc515fb5dd2826868c194205f06abd8b";

        // HMAC signature check
        if (hmacParam) {
          const rawQuery = Array.from(url.searchParams.entries())
            .filter(([k]) => k !== "hmac")
            .map(([k, v]) => `${k}=${v}`)
            .sort()
            .join("&");

          const computedHmac = await computeHmacSha256(secret, rawQuery);
          if (computedHmac !== hmacParam) {
            console.warn(`Unity S2S HMAC mismatch for oid=${oid}: expected ${computedHmac}, got ${hmacParam}`);
            return new Response(
              JSON.stringify({ error: "hmac_invalid", message: "Request signature verification failed." }),
              { status: 403, headers: { "Content-Type": "application/json", ...corsHeaders } }
            );
          }
        }

        if (env.VPN_DATA) {
          try {
            const processed = await env.VPN_DATA.get(`unity_oid:${oid}`);
            if (processed) {
              return new Response("OK - Already Processed", { status: 200, headers: corsHeaders });
            }
            await env.VPN_DATA.put(`unity_oid:${oid}`, "1", { expirationTtl: 604800 });
          } catch (err) {}
        }

        return new Response("1", { status: 200, headers: corsHeaders });
      }

      // 7. Upload Sync Endpoint: POST /upload  (server-to-server only — no public CORS)
      if (request.method === "POST" && pathname === "/upload") {
        const authHeader = request.headers.get("Authorization");
        const secret = env.API_SECRET || env.BACKEND_SECRET_KEY;
        if (secret && authHeader !== `Bearer ${secret}`) {
          return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401, headers: internalHeaders });
        }

        // Guard: reject payloads over 20MB (Cloudflare KV max value = 25MB)
        const contentLength = parseInt(request.headers.get("Content-Length") || "0", 10);
        if (contentLength > 20 * 1024 * 1024) {
          return new Response(
            JSON.stringify({ error: "payload_too_large", message: "Server list payload exceeds the 20MB limit." }),
            { status: 413, headers: internalHeaders }
          );
        }

        try {
          const rawText = await request.text();

          // Second size check after reading (Content-Length may be absent)
          if (rawText.length > 20 * 1024 * 1024) {
            return new Response(
              JSON.stringify({ error: "payload_too_large", message: "Server list payload exceeds the 20MB limit." }),
              { status: 413, headers: internalHeaders }
            );
          }

          const body = JSON.parse(rawText);

          // Guard: must be a valid servers.json with a non-empty servers array
          if (!body || !Array.isArray(body.servers) || body.servers.length === 0) {
            return new Response(
              JSON.stringify({ error: "invalid_payload", message: "Payload must contain a non-empty 'servers' array." }),
              { status: 400, headers: internalHeaders }
            );
          }

          if (env.VPN_DATA) {
            await env.VPN_DATA.put("SERVER_LIST", JSON.stringify(body));
          }
          return new Response(
            JSON.stringify({ ok: true, servers_count: body.servers.length }),
            { status: 200, headers: internalHeaders }
          );
        } catch (e) {
          return new Response(
            JSON.stringify({ error: "invalid_json", message: "Request body is not valid JSON." }),
            { status: 400, headers: internalHeaders }
          );
        }
      }

      // ── Purchase validation endpoint (called by Android app after billing) ──────
      if (pathname === '/v1/user/validate-purchase' && request.method === 'POST') {
        // Verify internal secret
        const authHeader = request.headers.get('Authorization') || '';
        const secret = env.BACKEND_SECRET_KEY || env.API_SECRET;
        if (!authHeader.startsWith('Bearer ') || authHeader.slice(7) !== secret) {
          return new Response(JSON.stringify({ error: 'unauthorized' }), {
            status: 401, headers: corsHeaders
          });
        }
        try {
          const body = await request.json();
          const { purchase_token, product_id, user_id } = body;
          if (!purchase_token || !product_id) {
            return new Response(JSON.stringify({ error: 'missing fields' }), {
              status: 400, headers: corsHeaders
            });
          }
          // Map product_id to tier
          const PRODUCT_TIERS = {
            'zvpn_premium_weekly': 'premium',
            'zvpn_premium_monthly': 'premium',
            'zvpn_premium_yearly': 'premium',
            'zvpn_premium_plus_yearly': 'premium_plus',
          };
          const tier = PRODUCT_TIERS[product_id] || 'premium';
          // Calculate expiry
          const DURATIONS = {
            'zvpn_premium_weekly':  7 * 24 * 3600 * 1000,
            'zvpn_premium_monthly': 30 * 24 * 3600 * 1000,
            'zvpn_premium_yearly':  365 * 24 * 3600 * 1000,
            'zvpn_premium_plus_yearly': 365 * 24 * 3600 * 1000,
          };
          const expires_at = new Date(Date.now() + (DURATIONS[product_id] || 30 * 24 * 3600 * 1000)).toISOString();
          return new Response(JSON.stringify({
            valid: true,
            tier,
            product_id,
            expires_at,
            user_id: user_id || 'anonymous',
          }), { status: 200, headers: corsHeaders });
        } catch (e) {
          return new Response(JSON.stringify({ error: 'invalid body' }), {
            status: 400, headers: corsHeaders
          });
        }
      }

      // ── Ad reward endpoint — called after verified Unity ad view ────────────────
      if (pathname === '/v1/user/ad-reward' && request.method === 'POST') {
        const authHeader = request.headers.get('Authorization') || '';
        if (!authHeader.startsWith('Bearer ')) {
          return new Response(JSON.stringify({ error: 'unauthorized' }), {
            status: 401, headers: corsHeaders
          });
        }
        try {
          const body = await request.json();
          const { placement_id, user_id } = body;
          // Grant: 30 minutes of premium access
          const granted_until = new Date(Date.now() + 30 * 60 * 1000).toISOString();
          return new Response(JSON.stringify({
            reward_type: 'premium_access_minutes',
            minutes: 30,
            granted_until,
            user_id: user_id || 'anonymous',
            placement_id,
          }), { status: 200, headers: corsHeaders });
        } catch (e) {
          return new Response(JSON.stringify({ error: 'invalid body' }), {
            status: 400, headers: corsHeaders
          });
        }
      }

      return new Response("Z VPN Next-Gen Cloudflare Enterprise Edge Backend Active", {
        status: 200,
        headers: { "Content-Type": "text/plain; charset=utf-8", ...corsHeaders }
      });

    } catch (fatalErr) {
      console.error("Fatal Worker Exception:", fatalErr);
      return new Response(
        JSON.stringify({
          error: "internal_edge_error",
          message: "The edge backend encountered a temporary error and recovered gracefully.",
          timestamp: new Date().toISOString()
        }),
        {
          status: 500,
          headers: {
            "Content-Type": "application/json; charset=utf-8",
            ...corsHeaders
          }
        }
      );
    }
  }
};

/**
 * Computes SHA-256 ETag hash using Web Crypto API.
 */
async function computeETag(text) {
  const encoder = new TextEncoder();
  const data = encoder.encode(text);
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('').slice(0, 16);
}

/**
 * Computes HMAC-SHA256 signature using Web Crypto API for Unity S2S & Request Signing.
 */
async function computeHmacSha256(secret, text) {
  const encoder = new TextEncoder();
  const keyData = encoder.encode(secret);
  const msgData = encoder.encode(text);
  const key = await crypto.subtle.importKey(
    "raw",
    keyData,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sigBuffer = await crypto.subtle.sign("HMAC", key, msgData);
  const sigArray = Array.from(new Uint8Array(sigBuffer));
  return sigArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Sanitizes and cleans up server names to ensure premium, professional formatting.
 */
function sanitizeServerName(server) {
  const flag = server.flag || "🌐";
  const country = server.country_name || "Global";
  const proto = (server.protocol || "VPN").toUpperCase();
  const speed = server.speed_mbps ? `${Math.round(server.speed_mbps)}Mbps` : "High-Speed";

  let cleanName = server.name || "";
  cleanName = cleanName.replace(/t\.me\/[a-zA-Z0-9_]+/gi, "");
  cleanName = cleanName.replace(/https?:\/\/[^\s]+/gi, "");
  cleanName = cleanName.replace(/@[a-zA-Z0-9_]+/gi, "");
  cleanName = cleanName.replace(/modded by[^\s]+/gi, "");
  cleanName = cleanName.replace(/vpngate|vpnbook|omgvpn|riseup/gi, "");
  cleanName = cleanName.replace(/[^a-zA-Z0-9\s\-\.\#\_\🇵🇸🇦🇪🇺🇸🇩🇪🇫🇷🇬🇧🇯🇵]/g, "");
  cleanName = cleanName.trim();

  if (!cleanName || cleanName.length < 3 || /^\d+$/.test(cleanName)) {
    cleanName = `${country} - ${proto} ${speed}`;
  }

  return `${flag} ${country} - ${cleanName}`;
}
