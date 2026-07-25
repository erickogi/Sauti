import { createServer as createHttpsServer, type Server } from 'node:https';
import { resolve } from 'node:path';
import { build } from 'esbuild';
import { createSautiServer } from '@sauti/server';
import { createRedisPort } from './redis-port';
import { decodeToken } from './token';
import { generateCert } from './cert';

export interface Harness {
  url: string;
  wsUrl: string;
  port: number;
  close(): Promise<void>;
}

async function bundleClient(): Promise<string> {
  const result = await build({
    entryPoints: [resolve(process.cwd(), 'src/client.ts')],
    bundle: true,
    format: 'esm',
    platform: 'browser',
    target: 'es2022',
    write: false
  });
  const output = result.outputFiles[0];
  if (!output) throw new Error('client bundle produced no output');
  return output.text;
}

const PAGE = [
  '<!doctype html>',
  '<html>',
  '<head><meta charset="utf-8"><title>sauti e2e</title></head>',
  '<body><script type="module" src="/client.js"></script></body>',
  '</html>'
].join('');

export async function startHarness(): Promise<Harness> {
  const namespace = `sauti-e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const clientBundle = await bundleClient();
  const redis = createRedisPort();
  const certificate = generateCert();

  const sauti = createSautiServer({
    redis: redis.port,
    turn: {
      urls: ['stun:stun.l.google.com:19302'],
      secret: 'sauti-e2e-secret',
      ttlSeconds: 3600
    },
    authorize: async (token) => decodeToken(token),
    namespace,
    requireOrigin: false
  });

  const httpServer: Server = createHttpsServer(
    { cert: certificate.cert, key: certificate.key },
    (req, res) => {
      if (req.url === '/client.js') {
        res.writeHead(200, { 'content-type': 'text/javascript' });
        res.end(clientBundle);
        return;
      }
      res.writeHead(200, { 'content-type': 'text/html' });
      res.end(PAGE);
    }
  );

  sauti.attach(httpServer, { path: '/ws' });

  await new Promise<void>((r) => httpServer.listen(0, '127.0.0.1', r));
  const address = httpServer.address();
  const port = typeof address === 'object' && address ? address.port : 0;

  return {
    url: `https://localhost:${port}/`,
    wsUrl: `wss://localhost:${port}/ws`,
    port,
    async close() {
      await sauti.close();
      await new Promise<void>((r) => httpServer.close(() => r()));
      await redis.close();
    }
  };
}
