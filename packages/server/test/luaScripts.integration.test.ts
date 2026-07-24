import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { CLAIM_SLOT, RECLAIM_SLOT, SWEEP_SLOT, UPDATE_SLOT } from '../src/scripts.js';
import { encodeSlot, decodeSlot, type SlotRecord } from '../src/slot.js';
import { RealRedis, hasRedisServer } from './realRedis.js';

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

const runIf = hasRedisServer() ? describe : describe.skip;

runIf('shipped Lua against a real redis engine', () => {
  const redis = new RealRedis();

  beforeAll(async () => {
    await redis.start();
  }, 30000);

  afterAll(async () => {
    await redis.stop();
  });

  beforeEach(async () => {
    await redis.flush();
  });

  async function seedUnreachable(): Promise<void> {
    await redis.evalScript(CLAIM_SLOT, [key], ['A', '3', encodeSlot(record({}))]);
    const rec = record({ connectionState: 'unreachable', dropEpoch: 1 });
    await redis.evalScript(UPDATE_SLOT, [key], ['A', encodeSlot(rec)]);
  }

  it('reads back an encoded slot through real HSET storage byte-for-byte', async () => {
    const rec = record({
      metadata: { seat: 'front', nested: { k: [1, 2, 3] } },
      state: { muted: true, onHold: true },
      dropEpoch: 4
    });
    await redis.evalScript(CLAIM_SLOT, [key], ['A', '3', encodeSlot(rec)]);
    const stored = (await redis.command('HGET', key, 'A')) as string;
    expect(decodeSlot(stored)).toEqual(rec);
  });

  it('lets the real sweep win and blocks the resume when it runs first', async () => {
    await seedUnreachable();
    const resumed = encodeSlot(record({ connectionState: 'connected', dropEpoch: 1 }));
    expect(await redis.evalScript(SWEEP_SLOT, [key], ['A', 'g1', '1'])).toBe('swept');
    expect(await redis.evalScript(RECLAIM_SLOT, [key], ['A', 'g1', resumed])).toBe(
      'gone'
    );
    expect(await redis.command('HGET', key, 'A')).toBeNull();
  });

  it('lets the real resume win and keeps the slot when it runs first', async () => {
    await seedUnreachable();
    const resumed = encodeSlot(record({ connectionState: 'connected', dropEpoch: 1 }));
    expect(await redis.evalScript(RECLAIM_SLOT, [key], ['A', 'g1', resumed])).toBe(
      'ok'
    );
    expect(await redis.evalScript(SWEEP_SLOT, [key], ['A', 'g1', '1'])).toBe('kept');
    const stored = (await redis.command('HGET', key, 'A')) as string;
    expect(decodeSlot(stored).connectionState).toBe('connected');
  });

  it('keeps the slot when the real sweep parses a stale drop epoch offset', async () => {
    await seedUnreachable();
    expect(await redis.evalScript(SWEEP_SLOT, [key], ['A', 'g1', '0'])).toBe('kept');
    expect(await redis.command('HGET', key, 'A')).not.toBeNull();
  });

  it('slices the generation prefix and rejects a wrong-generation reclaim', async () => {
    await seedUnreachable();
    expect(await redis.evalScript(RECLAIM_SLOT, [key], ['A', 'wrong', 'x'])).toBe(
      'mismatch'
    );
  });

  it('reports gone for a sweep or reclaim of a missing slot', async () => {
    expect(await redis.evalScript(SWEEP_SLOT, [key], ['A', 'g1', '1'])).toBe('gone');
    expect(await redis.evalScript(RECLAIM_SLOT, [key], ['A', 'g1', 'x'])).toBe(
      'gone'
    );
  });

  it('enforces capacity and duplicate detection in the real claim', async () => {
    await redis.evalScript(CLAIM_SLOT, [key], ['A', '1', encodeSlot(record({}))]);
    expect(
      await redis.evalScript(CLAIM_SLOT, [key], [
        'B',
        '1',
        encodeSlot(record({ participantId: 'B' }))
      ])
    ).toBe('full');
    expect(
      await redis.evalScript(CLAIM_SLOT, [key], ['A', '3', encodeSlot(record({}))])
    ).toBe('exists');
  });
});
