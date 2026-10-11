/**
 * ZVPN Cloudflare Worker API Engine
 * High-performance edge endpoint reading VPN server profiles from R2 bucket.
 * Designed to handle millions of requests with sub-millisecond latency.
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const pathname = url.pathname.toLowerCase();

    // CORS Headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-API-Secret, X-File-Name',
    };

    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    // Cache Key handling
    const cache = caches.default;
    const cacheKey = new Request(url.toString(), request);

    if (request.method === 'GET') {
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
          service: 'ZVPN Cloudflare Edge Server Engine',
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

        // Optional filtering by Continent or Country
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
        // Cache response at Cloudflare Edge for 5 minutes
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

        // Query parameters filtering
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

      // 4. Authenticated Upload Endpoint (/upload)
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
