import { describe, it, expect } from 'vitest';
import { makeCall } from './harness.js';
import { FakeStream, FakeTrack } from './fakes.js';
import type { MediaConstraints } from '../src/types.js';

function audioOf(constraints: MediaConstraints): MediaConstraints['audio'] {
  return constraints.audio;
}

describe('microphone audio-processing wiring [AP]', () => {
  it('default acquireMic passes audio:true with no processing keys [AP-01]', async () => {
    const { h, call } = makeCall();
    await call.acquireMic();
    expect(h.getUserMediaCalls).toHaveLength(1);
    expect(audioOf(h.getUserMediaCalls[0]!)).toBe(true);
  });

  it('default acquireMic with a device passes only deviceId [AP-02]', async () => {
    const { h, call } = makeCall();
    await call.acquireMic('mic-1');
    const audio = audioOf(h.getUserMediaCalls[0]!) as Record<string, unknown>;
    expect(Object.keys(audio)).toEqual(['deviceId']);
    expect(audio.deviceId).toBe('mic-1');
  });

  it('default selectInputDevice carries no processing keys [AP-03]', async () => {
    const { h, call } = makeCall();
    await call.selectInputDevice('mic-2');
    const audio = audioOf(h.getUserMediaCalls[0]!) as Record<string, unknown>;
    expect(Object.keys(audio).sort()).toEqual(['deviceId']);
    expect('echoCancellation' in audio).toBe(false);
    expect('noiseSuppression' in audio).toBe(false);
    expect('autoGainControl' in audio).toBe(false);
  });

  it('an empty audioProcessing object writes no processing keys [AP-04]', async () => {
    const { h, call } = makeCall({ audioProcessing: {} });
    await call.acquireMic();
    const audio = audioOf(h.getUserMediaCalls[0]!) as Record<string, unknown>;
    expect(Object.keys(audio)).toEqual([]);
  });

  it('an empty audioProcessing object with a device writes only deviceId [AP-05]', async () => {
    const { h, call } = makeCall({ audioProcessing: {} });
    await call.acquireMic('mic-1');
    const audio = audioOf(h.getUserMediaCalls[0]!) as Record<string, unknown>;
    expect(Object.keys(audio)).toEqual(['deviceId']);
  });

  it('echoCancellation false is written as false [AP-06]', async () => {
    const { h, call } = makeCall({ audioProcessing: { echoCancellation: false } });
    await call.acquireMic();
    const audio = audioOf(h.getUserMediaCalls[0]!) as Record<string, unknown>;
    expect(audio).toEqual({ echoCancellation: false });
    expect('noiseSuppression' in audio).toBe(false);
    expect('autoGainControl' in audio).toBe(false);
  });

  it('echoCancellation true is written as true [AP-07]', async () => {
    const { h, call } = makeCall({ audioProcessing: { echoCancellation: true } });
    await call.acquireMic();
    const audio = audioOf(h.getUserMediaCalls[0]!) as Record<string, unknown>;
    expect(audio).toEqual({ echoCancellation: true });
  });

  it('a defined subset writes only its keys [AP-08]', async () => {
    const { h, call } = makeCall({
      audioProcessing: { noiseSuppression: true, autoGainControl: false }
    });
    await call.acquireMic();
    const audio = audioOf(h.getUserMediaCalls[0]!) as Record<string, unknown>;
    expect(audio).toEqual({ noiseSuppression: true, autoGainControl: false });
    expect('echoCancellation' in audio).toBe(false);
  });

  it('a subset merges alongside a selected deviceId [AP-09]', async () => {
    const { h, call } = makeCall({
      audioProcessing: { noiseSuppression: true, autoGainControl: false }
    });
    await call.selectInputDevice('mic-2');
    const audio = audioOf(h.getUserMediaCalls[0]!) as Record<string, unknown>;
    expect(audio).toEqual({
      deviceId: 'mic-2',
      noiseSuppression: true,
      autoGainControl: false
    });
    expect('echoCancellation' in audio).toBe(false);
  });

  it('all three keys are written when all are defined [AP-10]', async () => {
    const { h, call } = makeCall({
      audioProcessing: {
        echoCancellation: true,
        noiseSuppression: false,
        autoGainControl: true
      }
    });
    await call.acquireMic('mic-1');
    const audio = audioOf(h.getUserMediaCalls[0]!) as Record<string, unknown>;
    expect(audio).toEqual({
      deviceId: 'mic-1',
      echoCancellation: true,
      noiseSuppression: false,
      autoGainControl: true
    });
  });

  it('a device switch preserves the adopter processing choice [AP-11]', async () => {
    const { h, call } = makeCall({ audioProcessing: { echoCancellation: false } });
    await call.acquireMic();
    h.setGetUserMedia(async () => new FakeStream([new FakeTrack('switched')]));
    await call.selectInputDevice('mic-2');
    const audio = audioOf(h.getUserMediaCalls[1]!) as Record<string, unknown>;
    expect(audio).toEqual({ deviceId: 'mic-2', echoCancellation: false });
  });

  it('acquireMic default and a switch both stay processing-free without the option [AP-12]', async () => {
    const { h, call } = makeCall();
    await call.acquireMic();
    h.setGetUserMedia(async () => new FakeStream([new FakeTrack('switched')]));
    await call.selectInputDevice('mic-2');
    expect(audioOf(h.getUserMediaCalls[0]!)).toBe(true);
    const second = audioOf(h.getUserMediaCalls[1]!) as Record<string, unknown>;
    expect(Object.keys(second)).toEqual(['deviceId']);
  });
});
