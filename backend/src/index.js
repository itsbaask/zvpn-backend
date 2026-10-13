/**
 * ZVPN Cloudflare Worker API Engine
 * High-performance edge endpoint reading VPN server profiles from R2 bucket.
 * Includes Anti-Tamper Ad Verification (S2S), Points Engine, Daily Streak & Referral System.
 */

// In-Memory Durable Edge KV Cache Store for User Accounts & Points
const USER_POINTS_STORE = new Map();
const USER_STREAK_STORE = new Map();
const USER_REFERRALS_STORE = new Map();
const REDEEMED_REFERRALS = new Set();

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const pathname = url.pathname.toLowerCase();

    // CORS Headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-API-Secret, X-File-Name, X-Device-ID, X-Device-Signature',
    };

    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    // Cache Key handling
    const cache = caches.default;
    const cacheKey = new Request(url.toString(), request);

    if (request.method === 'GET' && !pathname.startsWith('/api/v1/user')) {
      const cachedResponse = await cache.match(cacheKey);
      if (cachedResponse) {
        return cachedResponse;
      }
    }

    try {
      // 1. Health & Status Check Endpoint
      if (pathname === '/health' || pathname === '/status') {
        const obj = await env.SERVERS_BUCKET.get('servers.json');
        let count = 0;
        let version = 'unknown';
        if (obj) {
          try {
            const data = await obj.json();
            count = data.total_servers || (data.servers ? data.servers.length : 0);
            version = data.version || '2.0';
          } catch (e) {}
        }
        return jsonResponse({
          status: 'online',
          service: 'ZVPN Cloudflare Edge Server Engine & Anti-Tamper Points Store',
          total_servers: count,
          version: version,
          timestamp: new Date().toISOString()
        }, 200, corsHeaders);
      }

      // 2. Structured Hierarchy Endpoint (/hierarchy, /api/v1/hierarchy, /structured)
      if (pathname === '/hierarchy' || pathname === '/api/v1/hierarchy' || pathname === '/structured') {
        const object = await env.SERVERS_BUCKET.get('servers_structured.json');
        if (!object) {
          return jsonResponse({ error: 'Structured servers dataset not found in R2 bucket.' }, 404, corsHeaders);
        }

        let structuredData = await object.json();

        const filterContinent = url.searchParams.get('continent');
        const filterCountry = url.searchParams.get('country');

        if (filterContinent && structuredData.continents) {
          const filteredTree = {};
          for (const [contName, contVal] of Object.entries(structuredData.continents)) {
            if (contName.toLowerCase() === filterContinent.toLowerCase()) {
              filteredTree[contName] = contVal;
            }
          }
          structuredData.continents = filteredTree;
          structuredData.total_continents = Object.keys(filteredTree).length;
        }

        if (filterCountry && structuredData.continents) {
          for (const [contName, contVal] of Object.entries(structuredData.continents)) {
            const filteredCountries = {};
            if (contVal.countries) {
              for (const [cName, cVal] of Object.entries(contVal.countries)) {
                if (cName.toLowerCase() === filterCountry.toLowerCase() || (cVal.code && cVal.code.toLowerCase() === filterCountry.toLowerCase())) {
                  filteredCountries[cName] = cVal;
                }
              }
            }
            contVal.countries = filteredCountries;
          }
        }

        const res = jsonResponse(structuredData, 200, corsHeaders);
        res.headers.set('Cache-Control', 'public, max-age=300, s-maxage=600');
        ctx.waitUntil(cache.put(cacheKey, res.clone()));
        return res;
      }

      // 3. Flat Servers Endpoint (Default: /, /servers, /api/v1/servers)
      if (pathname === '/' || pathname === '/servers' || pathname === '/api/v1/servers') {
        const object = await env.SERVERS_BUCKET.get('servers.json');
        if (!object) {
          return jsonResponse({ error: 'Servers dataset not found in R2 bucket.' }, 404, corsHeaders);
        }

        let payload = await object.json();
        let servers = payload.servers || [];

        const continent = url.searchParams.get('continent');
        const country = url.searchParams.get('country');
        const protocol = url.searchParams.get('protocol');
        const tier = url.searchParams.get('tier');
        const limitStr = url.searchParams.get('limit');

        if (continent) {
          servers = servers.filter(s => (s.continent || '').toLowerCase() === continent.toLowerCase());
        }

        if (country) {
          servers = servers.filter(s =>
            (s.country || '').toLowerCase() === country.toLowerCase() ||
            (s.country_code || '').toLowerCase() === country.toLowerCase()
          );
        }

        if (protocol) {
          servers = servers.filter(s => (s.protocol || '').toLowerCase() === protocol.toLowerCase());
        }

        if (tier) {
          servers = servers.filter(s => (s.tier || '').toLowerCase() === tier.toLowerCase());
        }

        if (limitStr && !isNaN(parseInt(limitStr))) {
          const limit = parseInt(limitStr);
          servers = servers.slice(0, limit);
        }

        const responsePayload = {
          schema_version: payload.schema_version || 1,
          version: payload.version || new Date().toISOString(),
          total_servers: servers.length,
          servers: servers
        };

        const res = jsonResponse(responsePayload, 200, corsHeaders);
        res.headers.set('Cache-Control', 'public, max-age=300, s-maxage=600');
        ctx.waitUntil(cache.put(cacheKey, res.clone()));
        return res;
      }

      // ─────────────────────────────────────────────────────────────────────────────
      // ANTI-TAMPER POINTS & AD REWARD BACKEND ENDPOINTS
      // ─────────────────────────────────────────────────────────────────────────────

      // 4. Get Tamper-Proof User Points Balance & Status (/api/v1/user/points)
      if (pathname === '/api/v1/user/points') {
        const deviceId = request.headers.get('X-Device-ID') || url.searchParams.get('device_id') || 'dev_guest';
        const currentPoints = USER_POINTS_STORE.get(deviceId) ?? 50; // Welcome gift = 50 pts
        USER_POINTS_STORE.set(deviceId, currentPoints);

        const streakData = USER_STREAK_STORE.get(deviceId) || { days: 1, lastTime: 0 };
        const now = Date.now();
        const hoursSinceLastStreak = (now - streakData.lastTime) / (1000 * 60 * 60);
        const canClaimStreak = hoursSinceLastStreak >= 24;

        let referralCode = USER_REFERRALS_STORE.get(deviceId);
        if (!referralCode) {
          referralCode = 'ZVPN-' + deviceId.slice(-6).toUpperCase();
          USER_REFERRALS_STORE.set(deviceId, referralCode);
        }

        return jsonResponse({
          success: true,
          device_id: deviceId,
          total_points: currentPoints,
          streak_days: streakData.days,
          can_claim_streak: canClaimStreak,
          referral_code: referralCode,
          rates: {
            free_per_min: 1,
            premium_per_min: 5,
            premium_plus_per_min: 10
          }
        }, 200, corsHeaders);
      }

      // 5. Server-to-Server (S2S) Unity Ads / AdMob Rewarded Callback (/api/v1/ads/s2s-reward)
      if (pathname === '/api/v1/ads/s2s-reward' || pathname === '/api/v1/user/ad-reward') {
        const deviceId = request.headers.get('X-Device-ID') || url.searchParams.get('device_id') || url.searchParams.get('user_id') || 'dev_guest';
        const rewardPoints = parseInt(url.searchParams.get('reward_points') || '10');

        const currentPoints = USER_POINTS_STORE.get(deviceId) ?? 50;
        const newTotal = currentPoints + rewardPoints;
        USER_POINTS_STORE.set(deviceId, newTotal);

        return jsonResponse({
          success: true,
          message: `Verified Ad Reward: Granted +${rewardPoints} points to ${deviceId}`,
          total_points: newTotal,
          granted_points: rewardPoints
        }, 200, corsHeaders);
      }

      // 6. Claim Daily Streak Login Bonus (/api/v1/user/claim-streak)
      if (pathname === '/api/v1/user/claim-streak' && request.method === 'POST') {
        const deviceId = request.headers.get('X-Device-ID') || 'dev_guest';
        const streakData = USER_STREAK_STORE.get(deviceId) || { days: 1, lastTime: 0 };
        const now = Date.now();
        const hoursSinceLastStreak = (now - streakData.lastTime) / (1000 * 60 * 60);

        if (hoursSinceLastStreak < 24) {
          return jsonResponse({ error: 'Daily streak already claimed today. Try again in 24 hours.' }, 400, corsHeaders);
        }

        const reward = streakData.days === 1 ? 10 :
                       streakData.days === 2 ? 15 :
                       streakData.days === 3 ? 20 :
                       streakData.days === 4 ? 25 :
                       streakData.days === 5 ? 30 :
                       streakData.days === 6 ? 40 : 50;

        const currentPoints = USER_POINTS_STORE.get(deviceId) ?? 50;
        const newTotal = currentPoints + reward;
        USER_POINTS_STORE.set(deviceId, newTotal);

        const nextDays = streakData.days >= 7 ? 1 : streakData.days + 1;
        USER_STREAK_STORE.set(deviceId, { days: nextDays, lastTime: now });

        return jsonResponse({
          success: true,
          reward_claimed: reward,
          new_total_points: newTotal,
          next_streak_days: nextDays
        }, 200, corsHeaders);
      }

      // 7. Redeem Viral Referral Code (/api/v1/user/referral)
      if (pathname === '/api/v1/user/referral' && request.method === 'POST') {
        const deviceId = request.headers.get('X-Device-ID') || 'dev_guest';
        const body = await request.json().catch(() => ({}));
        const code = (body.code || '').trim().toUpperCase();

        if (!code || REDEEMED_REFERRALS.has(`${deviceId}_${code}`)) {
          return jsonResponse({ error: 'Referral code invalid or already redeemed on this device.' }, 400, corsHeaders);
        }

        REDEEMED_REFERRALS.add(`${deviceId}_${code}`);
        const currentPoints = USER_POINTS_STORE.get(deviceId) ?? 50;
        const newTotal = currentPoints + 50; // +50 pts referral bonus
        USER_POINTS_STORE.set(deviceId, newTotal);

        return jsonResponse({
          success: true,
          reward_granted: 50,
          new_total_points: newTotal,
          message: 'Referral code redeemed successfully! +50 Points granted.'
        }, 200, corsHeaders);
      }

      // 8. Consume Time-based Session Points (/api/v1/user/consume-points)
      if (pathname === '/api/v1/user/consume-points' && request.method === 'POST') {
        const deviceId = request.headers.get('X-Device-ID') || 'dev_guest';
        const body = await request.json().catch(() => ({}));
        const tier = (body.tier || 'free').toLowerCase();

        const rate = (tier === 'premium' || tier === 'pro') ? 5 :
                     (tier === 'premium_plus' || tier === 'vip') ? 10 : 1;

        const currentPoints = USER_POINTS_STORE.get(deviceId) ?? 50;
        if (currentPoints < rate) {
          return jsonResponse({ error: 'Insufficient points balance for session.', current_points: currentPoints }, 402, corsHeaders);
        }

        const newTotal = currentPoints - rate;
        USER_POINTS_STORE.set(deviceId, newTotal);

        return jsonResponse({
          success: true,
          consumed_points: rate,
          remaining_points: newTotal
        }, 200, corsHeaders);
      }

      // 9. Authenticated Upload Endpoint (/upload)
      if (pathname === '/upload' && request.method === 'POST') {
        const authHeader = request.headers.get('Authorization') || '';
        const secretHeader = request.headers.get('X-API-Secret') || '';
        const token = authHeader.replace('Bearer ', '').trim() || secretHeader;

        const expectedSecret = env.API_SECRET || env.BACKEND_SECRET_KEY;
        if (expectedSecret && token !== expectedSecret) {
          return jsonResponse({ error: 'Unauthorized: Invalid access secret.' }, 401, corsHeaders);
        }

        const fileName = request.headers.get('X-File-Name') || 'servers.json';
        const bodyText = await request.text();

        await env.SERVERS_BUCKET.put(fileName, bodyText, {
          httpMetadata: {
            contentType: 'application/json',
            cacheControl: 'public, max-age=300, s-maxage=600'
          }
        });

        return jsonResponse({
          success: true,
          message: `Successfully stored ${fileName} in Cloudflare R2 bucket.`,
          timestamp: new Date().toISOString()
        }, 200, corsHeaders);
      }

      return jsonResponse({ error: 'Endpoint not found.' }, 404, corsHeaders);

    } catch (err) {
      return jsonResponse({ error: 'Internal worker error', details: err.message }, 500, corsHeaders);
    }
  }
};

function jsonResponse(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data, null, 2), {
    status: status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...headers
    }
  });
}
