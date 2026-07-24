import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { WebSocket, type ClientOptions } from 'ws';
import { createSautiServer } from '../src/server.js';
import type { CreateServerDeps, SautiServer } from '../src/types.js';

export interface RunningServer {
  http: Server;
  server: SautiServer;
  url: string;
}

export async function startServer(
  deps: CreateServerDeps,
  opts?: { path?: string }
): Promise<RunningServer> {
  const http = createServer();
  const server = createSautiServer(deps);
  server.attach(http, opts);
  await new Promise<void>((resolve) => http.listen(0, '127.0.0.1', resolve));
  const port = (http.address() as AddressInfo).port;
  return { http, server, url: `ws://127.0.0.1:${port}` };
}

export async function stopServer(running: RunningServer): Promise<void> {
  await running.server.close();
  await new Promise<void>((resolve) => running.http.close(() => resolve()));
}

type Frame = Record<string, unknown>;

export class TestClient {
  readonly ws: WebSocket;
  private readonly frames: Frame[] = [];
  private readonly waiters: Array<{
    match: (f: Frame) => boolean;
    resolve: (f: Frame) => void;
  }> = [];

  closed = false;
  closeCode: number | null = null;

  constructor(url: string, options?: ClientOptions) {
    this.ws = new WebSocket(url, options ?? { origin: 'http://localhost' });
    this.ws.on('message', (data) => {
      const frame = JSON.parse(data.toString()) as Frame;
      const idx = this.waiters.findIndex((w) => w.match(frame));
      if (idx >= 0) {
        const [waiter] = this.waiters.splice(idx, 1);
        waiter!.resolve(frame);
      } else {
        this.frames.push(frame);
      }
    });
    this.ws.on('close', (code) => {
      this.closed = true;
      this.closeCode = code;
    });
  }

  connect(): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      this.ws.once('open', () => resolve());
      this.ws.once('unexpected-response', (_req, res) => {
        reject(new Error(`unexpected-response ${res.statusCode}`));
      });
      this.ws.once('error', (err) => reject(err));
    });
  }

  send(frame: Frame): void {
    this.ws.send(JSON.stringify(frame));
  }

  waitFor(match: (f: Frame) => boolean, timeoutMs = 1000): Promise<Frame> {
    const idx = this.frames.findIndex(match);
    if (idx >= 0) {
      const [frame] = this.frames.splice(idx, 1);
      return Promise.resolve(frame!);
    }
    return new Promise<Frame>((resolve, reject) => {
      const timer = setTimeout(() => {
        const i = this.waiters.findIndex((w) => w.resolve === wrapped);
        if (i >= 0) this.waiters.splice(i, 1);
        reject(new Error('waitFor timed out'));
      }, timeoutMs);
      const wrapped = (f: Frame): void => {
        clearTimeout(timer);
        resolve(f);
      };
      this.waiters.push({ match, resolve: wrapped });
    });
  }

  waitForType(type: string, timeoutMs = 1000): Promise<Frame> {
    return this.waitFor((f) => f.type === type, timeoutMs);
  }

  received(type: string): boolean {
    return this.frames.some((f) => f.type === type);
  }

  async join(token: string): Promise<Frame> {
    await this.connect();
    this.send({ v: 1, type: 'join', token });
    return this.waitFor((f) => f.type === 'ready' || f.type === 'error');
  }

  close(): void {
    this.ws.close();
  }

  waitClosed(timeoutMs = 1000): Promise<void> {
    if (this.closed) return Promise.resolve();
    return new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('waitClosed timed out')), timeoutMs);
      this.ws.once('close', () => {
        clearTimeout(timer);
        resolve();
      });
    });
  }
}

export async function delay(ms: number): Promise<void> {
  await new Promise<void>((resolve) => setTimeout(resolve, ms));
}
