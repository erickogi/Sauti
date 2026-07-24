import { spawn, spawnSync, type ChildProcess } from 'node:child_process';
import { createConnection, createServer, type Socket } from 'node:net';
import type { AddressInfo } from 'node:net';

export function hasRedisServer(): boolean {
  const probe = spawnSync('redis-server', ['--version']);
  return probe.status === 0;
}

async function freePort(): Promise<number> {
  return new Promise<number>((resolve, reject) => {
    const srv = createServer();
    srv.on('error', reject);
    srv.listen(0, '127.0.0.1', () => {
      const port = (srv.address() as AddressInfo).port;
      srv.close(() => resolve(port));
    });
  });
}

type Reply = string | number | null | Reply[];

function parseReply(buf: Buffer, off: number): { value: Reply; next: number } | null {
  if (off >= buf.length) return null;
  const type = buf[off];
  const lineEnd = buf.indexOf('\r\n', off + 1, 'binary');
  if (lineEnd === -1) return null;
  const head = buf.toString('binary', off + 1, lineEnd);
  const afterHead = lineEnd + 2;
  if (type === 0x2b) return { value: head, next: afterHead };
  if (type === 0x2d) return { value: `ERR:${head}`, next: afterHead };
  if (type === 0x3a) return { value: Number(head), next: afterHead };
  if (type === 0x24) {
    const len = Number(head);
    if (len === -1) return { value: null, next: afterHead };
    const dataEnd = afterHead + len;
    if (dataEnd + 2 > buf.length) return null;
    return { value: buf.toString('binary', afterHead, dataEnd), next: dataEnd + 2 };
  }
  if (type === 0x2a) {
    const count = Number(head);
    if (count === -1) return { value: null, next: afterHead };
    const items: Reply[] = [];
    let cursor = afterHead;
    for (let i = 0; i < count; i += 1) {
      const parsed = parseReply(buf, cursor);
      if (!parsed) return null;
      items.push(parsed.value);
      cursor = parsed.next;
    }
    return { value: items, next: cursor };
  }
  return { value: head, next: afterHead };
}

export class RealRedis {
  private proc: ChildProcess | undefined;
  private sock: Socket | undefined;
  private buf = Buffer.alloc(0);
  private readonly pending: Array<{
    resolve: (r: Reply) => void;
    reject: (e: Error) => void;
  }> = [];

  async start(): Promise<void> {
    const port = await freePort();
    this.proc = spawn(
      'redis-server',
      ['--port', String(port), '--save', '', '--appendonly', 'no'],
      { stdio: 'ignore' }
    );
    await this.connectWithRetry(port);
  }

  private async connectWithRetry(port: number): Promise<void> {
    for (let attempt = 0; attempt < 100; attempt += 1) {
      try {
        await this.connectOnce(port);
        const pong = await this.command('PING');
        if (pong === 'PONG') return;
      } catch {
        await new Promise((r) => setTimeout(r, 50));
      }
    }
    throw new Error('realRedis: server did not become ready');
  }

  private connectOnce(port: number): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const sock = createConnection({ port, host: '127.0.0.1' });
      sock.once('connect', () => {
        this.sock = sock;
        sock.on('data', (chunk) => this.onData(chunk));
        sock.on('error', () => {});
        resolve();
      });
      sock.once('error', reject);
    });
  }

  private onData(chunk: Buffer): void {
    this.buf = Buffer.concat([this.buf, chunk]);
    for (;;) {
      const parsed = parseReply(this.buf, 0);
      if (!parsed) break;
      this.buf = this.buf.subarray(parsed.next);
      const waiter = this.pending.shift();
      if (waiter) waiter.resolve(parsed.value);
    }
  }

  command(...args: string[]): Promise<Reply> {
    const parts = [Buffer.from(`*${args.length}\r\n`, 'binary')];
    for (const arg of args) {
      const body = Buffer.from(arg, 'binary');
      parts.push(Buffer.from(`$${body.length}\r\n`, 'binary'));
      parts.push(body);
      parts.push(Buffer.from('\r\n', 'binary'));
    }
    return new Promise<Reply>((resolve, reject) => {
      this.pending.push({ resolve, reject });
      this.sock!.write(Buffer.concat(parts));
    });
  }

  async evalScript(script: string, keys: string[], argv: string[]): Promise<Reply> {
    return this.command(
      'EVAL',
      script,
      String(keys.length),
      ...keys,
      ...argv
    );
  }

  async flush(): Promise<void> {
    await this.command('FLUSHALL');
  }

  async stop(): Promise<void> {
    this.sock?.destroy();
    if (this.proc) {
      this.proc.kill('SIGKILL');
      this.proc = undefined;
    }
  }
}
