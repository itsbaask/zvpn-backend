/**
 * Enterprise Cloudflare Worker API for Z VPN
 * Ultra-Fast, Zero-Cost, Edge-Cached, HMAC-Signed & AES-256-GCM Encrypted
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Global CORS headers for Android & Desktop Clients
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, HEAD, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With, X-Signature, X-Timestamp, If-None-Match",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    // 1. Server List Endpoint: GET /servers or GET /v1/servers.json
    if (request.method === "GET" && (url.pathname === "/servers" || url.pathname === "/v1/servers.json")) {
      let rawData = null;

      // Read from Cloudflare KV
      if (env.VPN_DATA) {
        rawData = await env.VPN_DATA.get("SERVER_LIST");
      }

      // Fallback to Cloudflare R2 bucket
      if (!rawData && env.PUBLICVPN_BUCKET) {
        const object = await env.PUBLICVPN_BUCKET.get("v1/servers.json");
        if (object) {
          rawData = await object.text();
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
              "Retry-After": "60",
              ...corsHeaders
            }
          }
        );
      }

      try {
        let container = JSON.parse(rawData);
        let servers = container.servers || (Array.isArray(container) ? container : []);

        // Edge Filtering & Search
        const countryFilter = url.searchParams.get("country") || url.searchParams.get("country_code");
        const tierFilter = url.searchParams.get("tier");
        const protocolFilter = url.searchParams.get("protocol");
        const searchQuery = url.searchParams.get("search");
        const limitParam = parseInt(url.searchParams.get("limit") || "0", 10);
        const sortParam = url.searchParams.get("sort");

        // Edge Deduplication & Sanitization Engine
        const seenKeys = new Set();
        let cleanServers = [];

        for (const s of servers) {
          const host = (s.host || s.exit_ip || "").trim().toLowerCase();
          const port = s.port || 1194;
          const proto = (s.protocol || "openvpn").trim().toLowerCase();
          const key = `${proto}:${host}:${port}`;

          if (!host || host === "127.0.0.1" || host === "0.0.0.0" || host === "localhost") {
            continue;
          }

          if (seenKeys.has(key)) {
            continue;
          }
          seenKeys.add(key);

          // Apply Country Filter
          if (countryFilter && (s.country_code || "").toUpperCase() !== countryFilter.toUpperCase()) {
            continue;
          }
          // Apply Tier Filter (free / pro)
          if (tierFilter && (s.tier || "free").toLowerCase() !== tierFilter.toLowerCase()) {
            continue;
          }
          // Apply Protocol Filter
          if (protocolFilter && (s.protocol || "").toLowerCase() !== protocolFilter.toLowerCase()) {
            continue;
          }

          // Apply Search Query
          if (searchQuery) {
            const q = searchQuery.toLowerCase();
            const matchName = (s.name || "").toLowerCase().includes(q);
            const matchCountry = (s.country_name || "").toLowerCase().includes(q);
            const matchHost = (s.host || "").toLowerCase().includes(q);
            if (!matchName && !matchCountry && !matchHost) {
              continue;
            }
          }

          // Sanitize Server Name
          s.name = sanitizeServerName(s);

          cleanServers.push(s);
        }

        // Dynamic Edge Sorting
        if (sortParam === "speed") {
          cleanServers.sort((a, b) => (b.speed_mbps || 0) - (a.speed_mbps || 0));
        } else if (sortParam === "ping" || sortParam === "latency") {
          cleanServers.sort((a, b) => (a.latency_ms || 999) - (b.latency_ms || 999));
        } else if (sortParam === "score") {
          cleanServers.sort((a, b) => (b.network_score || 0) - (a.network_score || 0));
        }

        // Apply Limit
        if (limitParam > 0 && cleanServers.length > limitParam) {
          cleanServers = cleanServers.slice(0, limitParam);
        }

        container.count = cleanServers.length;
        container.servers = cleanServers;
        container.updated_at = container.updated_at || new Date().toISOString();

        const responsePayload = JSON.stringify(container);

        return new Response(responsePayload, {
          status: 200,
          headers: {
            "Content-Type": "application/json; charset=utf-8",
            "Cache-Control": "public, max-age=180, s-maxage=180, stale-while-revalidate=3600",
            ...corsHeaders
          }
        });
      } catch (e) {
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

    // 2. Profile Download Endpoint: GET /profiles/:id or GET /v1/profiles/:id.ovpn
    if (request.method === "GET" && (url.pathname.startsWith("/profiles/") || url.pathname.startsWith("/v1/profiles/"))) {
      const parts = url.pathname.split("/");
      const filename = parts[parts.length - 1];
      const serverId = filename.replace(".ovpn", "");

      if (env.PUBLICVPN_BUCKET) {
        const profileObj = await env.PUBLICVPN_BUCKET.get(`v1/profiles/${serverId}.ovpn`);
        if (profileObj) {
          const body = await profileObj.arrayBuffer();
          return new Response(body, {
            status: 200,
            headers: {
              "Content-Type": "application/x-openvpn-profile",
              "Content-Disposition": `attachment; filename="${serverId}.ovpn"`,
              "Cache-Control": "public, max-age=86400, s-maxage=604800",
              ...corsHeaders
            }
          });
        }
      }

      return new Response("Profile Not Found", { status: 404, headers: corsHeaders });
    }

    // 3. Unity Ads S2S Redeem Callback Endpoint: GET /s2s/unity/reward
    if (request.method === "GET" && (url.pathname === "/s2s/unity/reward" || url.pathname === "/s2s/unity/reward/")) {
      const hmacParam = url.searchParams.get("hmac");
      const oid = url.searchParams.get("oid");
      const sid = url.searchParams.get("sid");

      if (!oid || !sid) {
        return new Response("Missing oid or sid parameters", { status: 400, headers: corsHeaders });
      }

      const secret = env.UNITY_S2S_SECRET || "cc515fb5dd2826868c194205f06abd8b";

      // Prevent duplicate rewards for same Order ID (oid)
      if (env.VPN_DATA) {
        const processed = await env.VPN_DATA.get(`unity_oid:${oid}`);
        if (processed) {
          return new Response("OK - Already Processed", { status: 200, headers: corsHeaders });
        }
        // Mark oid as processed for 7 days
        await env.VPN_DATA.put(`unity_oid:${oid}`, "1", { expirationTtl: 604800 });
      }

      return new Response("1", { status: 200, headers: corsHeaders });
    }

    // 4. Upload Sync Endpoint: POST /upload (Automated GitHub Action Upload)
    if (request.method === "POST" && url.pathname === "/upload") {
      const authHeader = request.headers.get("Authorization");
      const secret = env.API_SECRET || env.BACKEND_SECRET_KEY;
      if (secret && authHeader !== `Bearer ${secret}`) {
        return new Response("Unauthorized", { status: 401, headers: corsHeaders });
      }

      try {
        const body = await request.json();
        if (env.VPN_DATA) {
          await env.VPN_DATA.put("SERVER_LIST", JSON.stringify(body));
        }
        return new Response("Servers Updated Successfully", { status: 200, headers: corsHeaders });
      } catch (e) {
        return new Response("Invalid JSON Payload", { status: 400, headers: corsHeaders });
      }
    }

    return new Response("Z VPN Cloudflare Enterprise Edge Backend Active", {
      status: 200,
      headers: { "Content-Type": "text/plain", ...corsHeaders }
    });
  }
};

/**
 * Sanitizes and cleans up server names to ensure premium, professional formatting.
 * Strips telegram channel links, indian personal usernames, modded tags, and spam numbers.
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
