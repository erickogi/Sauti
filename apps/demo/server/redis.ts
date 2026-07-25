import type { RedisPort } from '@sauti/server';

const SEP = '\u0001';

export function createMemoryRedis(): RedisPort {
  const hashes = new Map<string, Map<string, string>>();
  const subs = new Map<string, Set<(message: string) => void>>();

  const hash = (key: string, create: boolean): Map<string, string> | undefined => {
    let h = hashes.get(key);
    if (!h && create) {
      h = new Map();
      hashes.set(key, h);
    }
    return h;
  };

  return {
    async hsetnx(key, field, value) {
      const h = hash(key, true)!;
      if (h.has(field)) return 0;
      h.set(field, value);
      return 1;
    },
    async hget(key, field) {
      return hash(key, false)?.get(field) ?? null;
    },
    async hgetall(key) {
      const h = hash(key, false);
      if (!h) return {};
      return Object.fromEntries(h.entries());
    },
    async hdel(key, field) {
      hash(key, false)?.delete(field);
    },
    async pexpire() {
      return undefined;
    },
    async eval(script, keys, args) {
      const op = /local op = '(\w+)'/.exec(script)?.[1];
      const key = keys[0]!;
      if (op === 'claim') {
        const [participantId, maxStr, encoded] = args as [string, string, string];
        const h = hash(key, false);
        if (h && h.has(participantId)) return 'exists';
        const count = h ? h.size : 0;
        if (count >= Number(maxStr)) return 'full';
        hash(key, true)!.set(participantId, encoded);
        return 'ok';
      }
      if (op === 'reclaim') {
        const [participantId, expectedGen, encoded] = args as [string, string, string];
        const cur = hash(key, false)?.get(participantId);
        if (cur === undefined) return 'gone';
        const gen = cur.slice(0, cur.indexOf(SEP));
        if (gen !== expectedGen) return 'mismatch';
        hash(key, true)!.set(participantId, encoded);
        return 'ok';
      }
      if (op === 'sweep') {
        const [participantId, gen, epoch] = args as [string, string, string];
        const h = hash(key, false);
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
        hash(key, true)!.set(participantId, encoded);
        return 'ok';
      }
      throw new Error(`memory redis: unknown script op ${String(op)}`);
    },
    async publish(channel, message) {
      const handlers = subs.get(channel);
      if (!handlers) return;
      for (const handler of [...handlers]) handler(message);
    },
    async subscribe(channel, handler) {
      let set = subs.get(channel);
      if (!set) {
        set = new Set();
        subs.set(channel, set);
      }
      set.add(handler);
    },
    async unsubscribe(channel, handler) {
      const set = subs.get(channel);
      if (!set) return;
      set.delete(handler);
      if (set.size === 0) subs.delete(channel);
    }
  };
}
