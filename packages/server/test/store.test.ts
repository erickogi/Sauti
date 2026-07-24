import { describe, it, expect } from 'vitest';
import { FakeRedisPort } from './fakeRedis.js';
import { CLAIM_SLOT, RECLAIM_SLOT, SWEEP_SLOT } from '../src/scripts.js';
import { encodeSlot, decodeSlot, type SlotRecord } from '../src/slot.js';

const key = 'sauti:room:r';

function record(overrides: Partial<SlotRecord>): SlotRecord {
  return {
    participantId: 'A',
    joinedAt: 1,
    metadata: {},
    connectionState: 'connected',
    state: { muted: false, onHold: false },
    generation: 'g1',
    dropEpoch: 0,
    ...overrides
  };
}

async function seedUnreachable(redis: FakeRedisPort): Promise<void> {
  await redis.eval(
    CLAIM_SLOT,
    [key],
    ['A', '3', encodeSlot(record({}))]
  );
  const raw = await redis.hget(key, 'A');
  const rec = decodeSlot(raw!);
  rec.connectionState = 'unreachable';
  rec.dropEpoch = 1;
  await redis.eval(
    `local op = 'update'\nredis.call('HSET', KEYS[1], ARGV[1], ARGV[2])\nreturn 'ok'`,
    [key],
    ['A', encodeSlot(rec)]
  );
}

describe('atomic slot reclaim under a sweep-versus-resume race', () => {
  it('lets the sweep win deterministically when it runs first', async () => {
    const redis = new FakeRedisPort();
    await seedUnreachable(redis);
    const resumed = encodeSlot(record({ connectionState: 'connected', dropEpoch: 1 }));
    const sweepResult = await redis.eval(SWEEP_SLOT, [key], ['A', 'g1', '1']);
    const resumeResult = await redis.eval(RECLAIM_SLOT, [key], ['A', 'g1', resumed]);
    expect(sweepResult).toBe('swept');
    expect(resumeResult).toBe('gone');
    expect(await redis.hget(key, 'A')).toBeNull();
  });

  it('lets the resume win deterministically when it runs first', async () => {
    const redis = new FakeRedisPort();
    await seedUnreachable(redis);
    const resumed = encodeSlot(record({ connectionState: 'connected', dropEpoch: 1 }));
    const resumeResult = await redis.eval(RECLAIM_SLOT, [key], ['A', 'g1', resumed]);
    const sweepResult = await redis.eval(SWEEP_SLOT, [key], ['A', 'g1', '1']);
    expect(resumeResult).toBe('ok');
    expect(sweepResult).toBe('kept');
    const raw = await redis.hget(key, 'A');
    expect(decodeSlot(raw!).connectionState).toBe('connected');
  });

  it('never lets both a sweep and a resume mutate the slot across every interleaving', async () => {
    for (const sweepFirst of [true, false]) {
      const redis = new FakeRedisPort();
      await seedUnreachable(redis);
      const resumed = encodeSlot(
        record({ connectionState: 'connected', dropEpoch: 1 })
      );
      const ops = sweepFirst
        ? [
            redis.eval(SWEEP_SLOT, [key], ['A', 'g1', '1']),
            redis.eval(RECLAIM_SLOT, [key], ['A', 'g1', resumed])
          ]
        : [
            redis.eval(RECLAIM_SLOT, [key], ['A', 'g1', resumed]),
            redis.eval(SWEEP_SLOT, [key], ['A', 'g1', '1'])
          ];
      const results = await Promise.all(ops);
      const winners = results.filter((r) => r === 'swept' || r === 'ok');
      expect(winners.length).toBe(1);
    }
  });

  it('keeps the slot when the sweep sees a stale drop epoch', async () => {
    const redis = new FakeRedisPort();
    await seedUnreachable(redis);
    const result = await redis.eval(SWEEP_SLOT, [key], ['A', 'g1', '0']);
    expect(result).toBe('kept');
    expect(await redis.hget(key, 'A')).not.toBeNull();
  });

  it('reports gone when sweeping or reclaiming a missing slot', async () => {
    const redis = new FakeRedisPort();
    expect(await redis.eval(SWEEP_SLOT, [key], ['A', 'g1', '1'])).toBe('gone');
    expect(
      await redis.eval(RECLAIM_SLOT, [key], ['A', 'g1', 'x'])
    ).toBe('gone');
  });

  it('reports mismatch when reclaiming with the wrong generation', async () => {
    const redis = new FakeRedisPort();
    await seedUnreachable(redis);
    const result = await redis.eval(RECLAIM_SLOT, [key], ['A', 'wrong', 'x']);
    expect(result).toBe('mismatch');
  });

  it('reports full when claiming past capacity and exists on a duplicate', async () => {
    const redis = new FakeRedisPort();
    await redis.eval(CLAIM_SLOT, [key], ['A', '1', encodeSlot(record({}))]);
    expect(
      await redis.eval(CLAIM_SLOT, [key], ['B', '1', encodeSlot(record({ participantId: 'B' }))])
    ).toBe('full');
    expect(
      await redis.eval(CLAIM_SLOT, [key], ['A', '3', encodeSlot(record({}))])
    ).toBe('exists');
  });

  it('rejects an unknown script op', async () => {
    const redis = new FakeRedisPort();
    await expect(
      redis.eval(`local op = 'bogus'\nreturn 'x'`, [key], [])
    ).rejects.toThrow();
  });
});

describe('slot codec', () => {
  it('round-trips a record through encode and decode preserving every field', () => {
    const rec = record({
      metadata: { seat: 'front', nested: { k: [1, 2, 3] } },
      state: { muted: true, onHold: true },
      dropEpoch: 4
    });
    const decoded = decodeSlot(encodeSlot(rec));
    expect(decoded).toEqual(rec);
  });
});
