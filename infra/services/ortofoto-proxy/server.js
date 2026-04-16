'use strict';

const http = require('node:http');
const { URL } = require('node:url');

const DEFAULT_PORT = 8080;
const DEFAULT_UPSTREAM_URL = 'https://api.dataforsyningen.dk/orto_foraar_webm_DAF';
const MIN_ZOOM = 0;
const MAX_ZOOM = 20;
const BOUNDS = [2.47842, 53.015, 17.5578, 58.6403];
const ATTRIBUTION = 'Klimadatastyrelsen / GeoDanmark Ortofoto, CC BY 4.0';

function createServer(options = {}) {
  const token = options.token ?? process.env.DMAP_ORTHOFOTO_TOKEN ?? '';
  const upstreamUrl = options.upstreamUrl ?? process.env.DMAP_ORTHOFOTO_UPSTREAM_URL ?? DEFAULT_UPSTREAM_URL;
  const fetchImpl = options.fetchImpl ?? globalThis.fetch;

  return http.createServer(async (req, res) => {
    try {
      if (req.method !== 'GET') {
        sendJson(res, 405, { error: 'method_not_allowed' });
        return;
      }

      const requestUrl = new URL(req.url, requestBaseUrl(req));
      if (requestUrl.pathname === '/healthz') {
        sendJson(res, 200, {
          status: 'ok',
          service: 'ortofoto-proxy',
          tokenConfigured: token.trim().length > 0,
        });
        return;
      }

      if (requestUrl.pathname === '/ortofoto/tilejson.json') {
        sendJson(res, 200, tileJson(requestUrl.origin));
        return;
      }

      const tile = parseTilePath(requestUrl.pathname);
      if (!tile.ok) {
        sendJson(res, tile.status, { error: tile.error });
        return;
      }

      if (token.trim().length === 0) {
        sendJson(res, 503, { error: 'orthofoto_token_missing' });
        return;
      }

      const upstream = buildUpstreamTileUrl(upstreamUrl, token, tile);
      const upstreamResponse = await fetchImpl(upstream);
      if (!upstreamResponse.ok) {
        sendJson(res, upstreamResponse.status, { error: 'upstream_tile_error' });
        return;
      }

      const body = Buffer.from(await upstreamResponse.arrayBuffer());
      res.writeHead(200, {
        'Content-Type': upstreamResponse.headers.get('content-type') || 'image/jpeg',
        'Cache-Control': 'public, max-age=86400',
      });
      res.end(body);
    } catch (error) {
      console.error('ortofoto proxy request failed', error.message);
      sendJson(res, 500, { error: 'proxy_error' });
    }
  });
}

function requestBaseUrl(req) {
  const protocol = req.headers['x-forwarded-proto'] || 'http';
  const host = req.headers.host || `localhost:${DEFAULT_PORT}`;
  return `${protocol}://${host}`;
}

function tileJson(origin) {
  return {
    tilejson: '2.2.0',
    name: 'GeoDanmark Ortofoto Foraar Web Mercator',
    attribution: ATTRIBUTION,
    minzoom: MIN_ZOOM,
    maxzoom: MAX_ZOOM,
    bounds: BOUNDS,
    tiles: [`${origin}/ortofoto/tiles/{z}/{x}/{y}.jpg`],
  };
}

function parseTilePath(pathname) {
  const match = pathname.match(/^\/ortofoto\/tiles\/([^/]+)\/([^/]+)\/([^/]+)\.jpg$/);
  if (!match) {
    return { ok: false, status: 404, error: 'not_found' };
  }

  const [, rawZ, rawX, rawY] = match;
  if (!isIntegerString(rawZ) || !isIntegerString(rawX) || !isIntegerString(rawY)) {
    return { ok: false, status: 400, error: 'invalid_tile_coordinate' };
  }

  const z = Number(rawZ);
  const x = Number(rawX);
  const y = Number(rawY);
  if (!Number.isSafeInteger(z) || !Number.isSafeInteger(x) || !Number.isSafeInteger(y)) {
    return { ok: false, status: 400, error: 'invalid_tile_coordinate' };
  }

  if (z < MIN_ZOOM || z > MAX_ZOOM) {
    return { ok: false, status: 400, error: 'zoom_out_of_range' };
  }

  const maxCoordinate = Math.pow(2, z) - 1;
  if (x < 0 || x > maxCoordinate || y < 0 || y > maxCoordinate) {
    return { ok: false, status: 400, error: 'tile_coordinate_out_of_range' };
  }

  return { ok: true, z, x, y };
}

function isIntegerString(value) {
  return /^(0|[1-9][0-9]*)$/.test(value);
}

function buildUpstreamTileUrl(upstreamUrl, token, tile) {
  const url = new URL(upstreamUrl);
  url.searchParams.set('SERVICE', 'WMTS');
  url.searchParams.set('REQUEST', 'GetTile');
  url.searchParams.set('VERSION', '1.0.0');
  url.searchParams.set('LAYER', 'orto_foraar_webm');
  url.searchParams.set('STYLE', 'default');
  url.searchParams.set('FORMAT', 'image/jpeg');
  url.searchParams.set('tileMatrixSet', 'DFD_GoogleMapsCompatible');
  url.searchParams.set('tileMatrix', String(tile.z));
  url.searchParams.set('tileRow', String(tile.y));
  url.searchParams.set('tileCol', String(tile.x));
  url.searchParams.set('token', token);
  return url;
}

function sendJson(res, status, body) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  res.end(JSON.stringify(body));
}

if (require.main === module) {
  const port = Number(process.env.PORT || DEFAULT_PORT);
  createServer().listen(port, '0.0.0.0', () => {
    console.log(`ortofoto proxy listening on ${port}`);
  });
}

module.exports = {
  buildUpstreamTileUrl,
  createServer,
  parseTilePath,
  tileJson,
};
