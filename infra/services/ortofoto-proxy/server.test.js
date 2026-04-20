'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { afterEach, test } = require('node:test');
const {
  buildUpstreamTileUrl,
  createServer,
  parseTilePath,
  readToken,
} = require('./server');

const servers = [];
const envSnapshot = { ...process.env };

afterEach(async () => {
  await Promise.all(servers.splice(0).map((server) => closeServer(server)));
  for (const key of Object.keys(process.env)) {
    delete process.env[key];
  }
  Object.assign(process.env, envSnapshot);
});

test('healthz returns token status without leaking token', async () => {
  const baseUrl = await listen(createServer({ token: 'secret-token' }));
  const response = await fetch(`${baseUrl}/healthz`);
  const text = await response.text();

  assert.equal(response.status, 200);
  assert.match(text, /"tokenConfigured":true/);
  assert.doesNotMatch(text, /secret-token/);
});

test('missing token returns 503 for tile requests', async () => {
  const baseUrl = await listen(createServer({ token: '' }));
  const response = await fetch(`${baseUrl}/ortofoto/tiles/10/547/322.jpg`);

  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: 'orthofoto_token_missing' });
});

test('token file takes precedence over environment token', async () => {
  const tempDir = await fs.mkdtemp(path.join(os.tmpdir(), 'dmap-token-'));
  const tokenFile = path.join(tempDir, 'token');
  await fs.writeFile(tokenFile, ' file-token \n', 'utf8');
  process.env.DMAP_ORTHOFOTO_TOKEN = 'env-token';
  process.env.DMAP_ORTHOFOTO_TOKEN_FILE = tokenFile;

  assert.equal(readToken(), 'file-token');

  await fs.rm(tempDir, { recursive: true, force: true });
});

test('environment token is used when token file is absent', () => {
  process.env.DMAP_ORTHOFOTO_TOKEN = 'env-token';
  delete process.env.DMAP_ORTHOFOTO_TOKEN_FILE;

  assert.equal(readToken(), 'env-token');
});

test('invalid and out of range tile paths are rejected', () => {
  assert.deepEqual(parseTilePath('/ortofoto/tiles/a/547/322.jpg'), {
    ok: false,
    status: 400,
    error: 'invalid_tile_coordinate',
  });
  assert.deepEqual(parseTilePath('/ortofoto/tiles/21/547/322.jpg'), {
    ok: false,
    status: 400,
    error: 'zoom_out_of_range',
  });
  assert.deepEqual(parseTilePath('/ortofoto/tiles/10/1024/322.jpg'), {
    ok: false,
    status: 400,
    error: 'tile_coordinate_out_of_range',
  });
});

test('valid tile request forwards expected WMTS parameters', async () => {
  let upstreamRequest;
  const fetchImpl = async (url) => {
    upstreamRequest = url;
    return new Response(Buffer.from([1, 2, 3]), {
      status: 200,
      headers: { 'content-type': 'image/jpeg' },
    });
  };
  const baseUrl = await listen(
    createServer({
      token: 'secret-token',
      upstreamUrl: 'https://example.com/orto',
      fetchImpl,
    }),
  );

  const response = await fetch(`${baseUrl}/ortofoto/tiles/10/547/322.jpg`);
  const bytes = Buffer.from(await response.arrayBuffer());
  const upstreamUrl = new URL(upstreamRequest);

  assert.equal(response.status, 200);
  assert.equal(response.headers.get('content-type'), 'image/jpeg');
  assert.equal(response.headers.get('cache-control'), 'public, max-age=86400');
  assert.deepEqual([...bytes], [1, 2, 3]);
  assert.equal(upstreamUrl.origin + upstreamUrl.pathname, 'https://example.com/orto');
  assert.equal(upstreamUrl.searchParams.get('SERVICE'), 'WMTS');
  assert.equal(upstreamUrl.searchParams.get('REQUEST'), 'GetTile');
  assert.equal(upstreamUrl.searchParams.get('VERSION'), '1.0.0');
  assert.equal(upstreamUrl.searchParams.get('LAYER'), 'orto_foraar_webm');
  assert.equal(upstreamUrl.searchParams.get('STYLE'), 'default');
  assert.equal(upstreamUrl.searchParams.get('FORMAT'), 'image/jpeg');
  assert.equal(upstreamUrl.searchParams.get('tileMatrixSet'), 'DFD_GoogleMapsCompatible');
  assert.equal(upstreamUrl.searchParams.get('tileMatrix'), '10');
  assert.equal(upstreamUrl.searchParams.get('tileRow'), '322');
  assert.equal(upstreamUrl.searchParams.get('tileCol'), '547');
  assert.equal(upstreamUrl.searchParams.get('token'), 'secret-token');
});

test('upstream timeout returns 504', async () => {
  const fetchImpl = async (_url, init) => {
    await new Promise((resolve, reject) => {
      init.signal.addEventListener('abort', () => {
        const error = new Error('aborted');
        error.name = 'AbortError';
        reject(error);
      });
      setTimeout(resolve, 1000);
    });
    return new Response(Buffer.from([1]), {
      status: 200,
      headers: { 'content-type': 'image/jpeg' },
    });
  };
  const baseUrl = await listen(
    createServer({
      token: 'secret-token',
      upstreamUrl: 'https://example.com/orto',
      timeoutMs: 10,
      fetchImpl,
    }),
  );

  const response = await fetch(`${baseUrl}/ortofoto/tiles/10/547/322.jpg`);

  assert.equal(response.status, 504);
  assert.deepEqual(await response.json(), { error: 'upstream_tile_timeout' });
});

test('invalid upstream content type returns 502', async () => {
  const fetchImpl = async () => new Response(JSON.stringify({ error: 'nope' }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
  const baseUrl = await listen(
    createServer({
      token: 'secret-token',
      upstreamUrl: 'https://example.com/orto',
      fetchImpl,
    }),
  );

  const response = await fetch(`${baseUrl}/ortofoto/tiles/10/547/322.jpg`);

  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: 'invalid_upstream_tile' });
});

test('buildUpstreamTileUrl preserves existing upstream query params', () => {
  const url = buildUpstreamTileUrl(
    'https://example.com/orto?existing=1',
    'secret-token',
    { z: 10, x: 547, y: 322 },
  );

  assert.equal(url.searchParams.get('existing'), '1');
  assert.equal(url.searchParams.get('token'), 'secret-token');
});

async function listen(server) {
  servers.push(server);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  return `http://127.0.0.1:${address.port}`;
}

function closeServer(server) {
  return new Promise((resolve, reject) => {
    server.close((error) => {
      if (error) {
        reject(error);
      } else {
        resolve();
      }
    });
  });
}
