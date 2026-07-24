import type { RedisPort } from '../src/types.js';

const SEP = '\u0001';

export class FakeRedisPort implements RedisPort {
  readonly hashes = new Map<string, Map<string, string>>();
  readonly subs = new Map<string, Set<(message: string) => void>>();
  readonly ttls = new Map<string, number>();

  private hash(key: string, create: boolean): Map<string, string> | undefined {
    let h = this.hashes.get(key);
    if (!h && create) {
      h = new Map();
      this.hashes.set(key, h);
    }
    return h;
  }

  async hsetnx(key: string, field: string, value: string): Promise<0 | 1> {
    const h = this.hash(key, true)!;
    if (h.has(field)) return 0;
    h.set(field, value);
    return 1;
  }

  async hget(key: string, field: string): Promise<string | null> {
    return this.hash(key, false)?.get(field) ?? null;
  }

  async hgetall(key: string): Promise<Record<string, string>> {
    const h = this.hash(key, false);
    if (!h) return {};
    return Object.fromEntries(h.entries());
  }

  async hdel(key: string, field: string): Promise<void> {
    this.hash(key, false)?.delete(field);
  }

  async pexpire(key: string, ms: number): Promise<void> {
    this.ttls.set(key, ms);
  }

  async eval(script: string, keys: string[], args: string[]): Promise<unknown> {
    const op = /local op = '(\w+)'/.exec(script)?.[1];
    const key = keys[0]!;
    if (op === 'claim') {
      const [participantId, maxStr, encoded] = args as [string, string, string];
      const h = this.hash(key, false);
      if (h && h.has(participantId)) return 'exists';
      const count = h ? h.size : 0;
      if (count >= Number(maxStr)) return 'full';
      this.hash(key, true)!.set(participantId, encoded);
      return 'ok';
    }
    if (op === 'reclaim') {
      const [participantId, expectedGen, encoded] = args as [string, string, string];
      const cur = this.hash(key, false)?.get(participantId);
      if (cur === undefined) return 'gone';
      const gen = cur.slice(0, cur.indexOf(SEP));
      if (gen !== expectedGen) return 'mismatch';
      this.hash(key, true)!.set(participantId, encoded);
      return 'ok';
    }
    if (op === 'sweep') {
      const [participantId, gen, epoch] = args as [string, string, string];
      const h = this.hash(key, false);
      const cur = h?.get(participantId);
      if (cur === undefined) return 'gone';
      const s1 = cur.indexOf(SEP);
      const s2 = cur.indexOf(SEP, s1 + 1);
      const s3 = cur.indexOf(SEP, s2 + 1);
      const curGen = cur.slice(0, s1);
      const conn = cur.slice(s1 + 1, s2);
      const curEpoch = cur.slice(s2 + 1, s3);
      if (curGen !== gen) return 'kept';
      if (curEpoch !== epoch) return 'kept';
      if (conn !== 'unreachable') return 'kept';
      h!.delete(participantId);
      return 'swept';
    }
    if (op === 'update') {
      const [participantId, encoded] = args as [string, string];
      this.hash(key, true)!.set(participantId, encoded);
      return 'ok';
    }
    throw new Error(`fake redis: unknown script op ${String(op)}`);
  }

  async publish(channel: string, message: string): Promise<void> {
    const handlers = this.subs.get(channel);
    if (!handlers) return;
    for (const handler of [...handlers]) handler(message);
  }

  async subscribe(
    channel: string,
    handler: (message: string) => void
  ): Promise<void> {
    let set = this.subs.get(channel);
    if (!set) {
      set = new Set();
      this.subs.set(channel, set);
    }
    set.add(handler);
  }

  async unsubscribe(
    channel: string,
    handler: (message: string) => void
  ): Promise<void> {
    const set = this.subs.get(channel);
    if (!set) return;
    set.delete(handler);
    if (set.size === 0) this.subs.delete(channel);
  }
}
