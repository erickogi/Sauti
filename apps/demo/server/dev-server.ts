import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { randomUUID } from 'node:crypto';
import { createSautiServer } from '@sauti/server';
import { createMemoryRedis } from './redis.js';
import { decodeToken, mintToken } from './token.js';

const PORT = Number(process.env.SAUTI_DEMO_PORT ?? 8787);
const ICE_URL = process.env.SAUTI_DEMO_STUN ?? 'stun:stun.l.google.com:19302';

const sauti = createSautiServer({
  redis: createMemoryRedis(),
  turn: { urls: [ICE_URL], secret: 'sauti-demo-secret', ttlSeconds: 3600 },
  authorize: async (token) => decodeToken(token),
  namespace: `sauti-demo-${Date.now()}`,
  requireOrigin: false
});

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > 4096) req.destroy();
    });
    req.on('end', () => resolve(body));
    req.on('error', () => resolve(''));
  });
}

function sendJson(res: ServerResponse, status: number, payload: unknown): void {
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(JSON.stringify(payload));
}

async function handleToken(req: IncomingMessage, res: ServerResponse): Promise<void> {
  const raw = await readBody(req);
  let roomId = '';
  let name: string | undefined;
  try {
    const parsed = JSON.parse(raw || '{}') as { roomId?: unknown; name?: unknown };
    roomId = typeof parsed.roomId === 'string' ? parsed.roomId.trim().toLowerCase() : '';
    if (typeof parsed.name === 'string' && parsed.name.trim().length > 0) {
      name = parsed.name.trim();
    }
  } catch {
    roomId = '';
  }
  if (!roomId) {
    sendJson(res, 400, { error: 'roomId is required' });
    return;
  }
  const participantId = `u-${randomUUID().slice(0, 8)}`;
  const callToken = mintToken({
    roomId,
    participantId,
    metadata: name ? { name } : undefined
  });
  sendJson(res, 200, { callToken, participantId, roomId });
}

const http = createServer((req, res) => {
  if (req.method === 'POST' && req.url === '/token') {
    void handleToken(req, res);
    return;
  }
  sendJson(res, 404, { error: 'not found' });
});

sauti.attach(http, { path: '/ws' });

http.listen(PORT, '127.0.0.1', () => {
  process.stdout.write(`sauti demo server listening on 127.0.0.1:${PORT}, ws path /ws\n`);
});
