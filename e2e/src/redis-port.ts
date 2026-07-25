import Redis from 'ioredis';
import type { RedisPort } from '@sauti/server';

export interface RedisResources {
  port: RedisPort;
  close(): Promise<void>;
}

export function createRedisPort(): RedisResources {
  const commander = new Redis(6379, '127.0.0.1');
  const subscriber = new Redis(6379, '127.0.0.1');
  const handlers = new Map<string, Set<(message: string) => void>>();

  subscriber.on('message', (channel: string, message: string) => {
    const set = handlers.get(channel);
    if (!set) return;
    for (const handler of set) handler(message);
  });

  const port: RedisPort = {
    async hsetnx(key, field, value) {
      return (await commander.hsetnx(key, field, value)) as 0 | 1;
    },
    async hget(key, field) {
      return commander.hget(key, field);
    },
    async hgetall(key) {
      return commander.hgetall(key);
    },
    async hdel(key, field) {
      await commander.hdel(key, field);
    },
    async pexpire(key, ms) {
      await commander.pexpire(key, ms);
    },
    async eval(script, keys, args) {
      return commander.eval(script, keys.length, ...keys, ...args);
    },
    async publish(channel, message) {
      await commander.publish(channel, message);
    },
    async subscribe(channel, handler) {
      let set = handlers.get(channel);
      if (!set) {
        set = new Set();
        handlers.set(channel, set);
        await subscriber.subscribe(channel);
      }
      set.add(handler);
    },
    async unsubscribe(channel, handler) {
      const set = handlers.get(channel);
      if (!set) return;
      set.delete(handler);
      if (set.size === 0) {
        handlers.delete(channel);
        await subscriber.unsubscribe(channel);
      }
    }
  };

  return {
    port,
    async close() {
      await Promise.all([commander.quit(), subscriber.quit()]);
    }
  };
}
