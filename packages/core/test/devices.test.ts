import { describe, it, expect } from 'vitest';
import { connect, makeCall, readyFrame } from './harness.js';
import { flush, makeRuntime, participant, FakeStream, FakeTrack } from './fakes.js';

describe('device selection', () => {
  it('enumerates input devices only after permission [DEV-01]', async () => {
    const { call } = makeCall();
    expect(await call.enumerateDevices()).toEqual([]);
    await call.acquireMic();
    const devices = await call.enumerateDevices();
    expect(devices.length).toBeGreaterThan(0);
    expect(devices.every((d) => d.label.length > 0)).toBe(true);
  });

  it('updates the device list on devicechange [DEV-02]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    const seen: number[] = [];
    conn.call.on('devices-changed', (e) => seen.push(e.devices.length));
    conn.h.setDevices([{ deviceId: 'mic-9', kind: 'audioinput', label: 'New Mic' }]);
    conn.h.fire('devicechange');
    await flush();
    expect(seen).toContain(1);
  });

  it('switches input via replaceTrack without renegotiation [DEV-03]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B'), participant('C')] })
    );
    const newTrack = new FakeTrack('switched');
    conn.h.setGetUserMedia(async () => new FakeStream([newTrack]));
    await conn.call.selectInputDevice('mic-2');
    for (const pc of conn.h.pcs) {
      expect(pc.senders[0]!.replaced).toContain(newTrack);
      expect(pc.offersCreated).toHaveLength(0);
    }
    expect(conn.socket.frames().some((f) => f.type === 'offer')).toBe(false);
  });

  it('a newly selected track inherits the current mute state [DEV-04]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    conn.call.setMuted(true);
    const newTrack = new FakeTrack('switched');
    conn.h.setGetUserMedia(async () => new FakeStream([newTrack]));
    await conn.call.selectInputDevice('mic-2');
    expect(newTrack.enabled).toBe(false);
  });
});
