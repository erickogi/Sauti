import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useOutgoingRingback, type RingbackOptions } from '../src/hooks/useOutgoingRingback.js';
import type { RingbackPort } from '../src/audio/ringbackPort.js';
import { fakeBinding, type FakeBinding } from './fakeBinding.js';
import { participant } from './fakeCall.js';

const hoisted = vi.hoisted(() => {
  const makePort = () => ({ start: vi.fn(), stop: vi.fn(), close: vi.fn() });
  return { makePort, createRingbackPort: vi.fn(() => makePort()) };
});

vi.mock('../src/audio/ringbackPort.js', () => ({
  createRingbackPort: hoisted.createRingbackPort,
  DEFAULT_RINGBACK_TONE: {
    frequencies: [440, 480],
    cadence: { onMs: 2000, offMs: 4000 },
    gain: 0.08
  }
}));

interface FakePort extends RingbackPort {
  start: ReturnType<typeof vi.fn>;
  stop: ReturnType<typeof vi.fn>;
  close: ReturnType<typeof vi.fn>;
}

function fakePort(): FakePort {
  return { start: vi.fn(), stop: vi.fn(), close: vi.fn() };
}

interface HookProps {
  binding: FakeBinding;
  options: RingbackOptions;
}

function renderRingback(binding: FakeBinding, options: RingbackOptions) {
  return renderHook(({ binding, options }: HookProps) => useOutgoingRingback(binding, options), {
    initialProps: { binding, options }
  });
}

beforeEach(() => {
  hoisted.createRingbackPort.mockClear();
});

describe('useOutgoingRingback', () => {
  it('starts the port once when connecting', () => {
    const port = fakePort();
    const { result } = renderRingback(fakeBinding({ phase: 'connecting' }), { port });
    expect(result.current).toBe('ringing');
    expect(port.start).toHaveBeenCalledTimes(1);
    expect(port.stop).not.toHaveBeenCalled();
  });

  it('stays ringing without re-starting when it moves to connected with no remote peer', () => {
    const port = fakePort();
    const { rerender, result } = renderRingback(fakeBinding({ phase: 'connecting' }), {
      selfParticipantId: 'self-1',
      port
    });
    rerender({
      binding: fakeBinding({
        phase: 'connected',
        participants: [participant({ participantId: 'self-1' })]
      }),
      options: { selfParticipantId: 'self-1', port }
    });
    expect(result.current).toBe('ringing');
    expect(port.start).toHaveBeenCalledTimes(1);
    expect(port.stop).not.toHaveBeenCalled();
  });

  it('stops once when a remote peer becomes present', () => {
    const port = fakePort();
    const { rerender, result } = renderRingback(fakeBinding({ phase: 'connecting' }), {
      selfParticipantId: 'self-1',
      port
    });
    rerender({
      binding: fakeBinding({
        phase: 'connected',
        participants: [
          participant({ participantId: 'self-1' }),
          participant({ participantId: 'peer-1' })
        ]
      }),
      options: { selfParticipantId: 'self-1', port }
    });
    expect(result.current).toBe('silent');
    expect(port.start).toHaveBeenCalledTimes(1);
    expect(port.stop).toHaveBeenCalledTimes(1);
  });

  it('stops when the call is left', () => {
    const port = fakePort();
    const { rerender } = renderRingback(fakeBinding({ phase: 'connecting' }), { port });
    rerender({ binding: fakeBinding({ phase: 'left' }), options: { port } });
    expect(port.start).toHaveBeenCalledTimes(1);
    expect(port.stop).toHaveBeenCalledTimes(1);
  });

  it('never constructs a port while idle', () => {
    const { result } = renderRingback(fakeBinding({ phase: 'idle' }), {});
    expect(result.current).toBe('silent');
    expect(hoisted.createRingbackPort).not.toHaveBeenCalled();
  });

  it('holds silent while audioBlocked, then starts once when it is unblocked', () => {
    const port = fakePort();
    const { rerender, result } = renderRingback(
      fakeBinding({ phase: 'connecting', audioBlocked: true }),
      { port }
    );
    expect(result.current).toBe('ringing');
    expect(port.start).not.toHaveBeenCalled();
    rerender({
      binding: fakeBinding({ phase: 'connecting', audioBlocked: false }),
      options: { port }
    });
    expect(port.start).toHaveBeenCalledTimes(1);
  });

  it('does not start or stop again on a re-render with an unchanged mode', () => {
    const port = fakePort();
    const { rerender } = renderRingback(fakeBinding({ phase: 'connecting' }), { port });
    rerender({ binding: fakeBinding({ phase: 'connecting' }), options: { port } });
    rerender({ binding: fakeBinding({ phase: 'connecting' }), options: { port } });
    expect(port.start).toHaveBeenCalledTimes(1);
    expect(port.stop).not.toHaveBeenCalled();
  });

  it('forces silent and never starts when enabled is false', () => {
    const port = fakePort();
    const { result } = renderRingback(fakeBinding({ phase: 'connecting' }), {
      enabled: false,
      port
    });
    expect(result.current).toBe('silent');
    expect(port.start).not.toHaveBeenCalled();
  });

  it('stops an active ring when enabled flips to false', () => {
    const port = fakePort();
    const { rerender } = renderRingback(fakeBinding({ phase: 'connecting' }), {
      enabled: true,
      port
    });
    expect(port.start).toHaveBeenCalledTimes(1);
    rerender({ binding: fakeBinding({ phase: 'connecting' }), options: { enabled: false, port } });
    expect(port.stop).toHaveBeenCalledTimes(1);
  });

  it('stops and closes a hook-created port on unmount', () => {
    const created = fakePort();
    hoisted.createRingbackPort.mockReturnValueOnce(created);
    const { unmount } = renderRingback(fakeBinding({ phase: 'connecting' }), {});
    expect(hoisted.createRingbackPort).toHaveBeenCalledTimes(1);
    expect(created.start).toHaveBeenCalledTimes(1);
    unmount();
    expect(created.stop).toHaveBeenCalledTimes(1);
    expect(created.close).toHaveBeenCalledTimes(1);
  });

  it('stops but never closes an adopter-supplied port on unmount', () => {
    const port = fakePort();
    const { unmount } = renderRingback(fakeBinding({ phase: 'connecting' }), { port });
    unmount();
    expect(port.stop).toHaveBeenCalledTimes(1);
    expect(port.close).not.toHaveBeenCalled();
  });

  it('uses the supplied port verbatim and never invokes the factory', () => {
    const port = fakePort();
    renderRingback(fakeBinding({ phase: 'connecting' }), { port });
    expect(hoisted.createRingbackPort).not.toHaveBeenCalled();
    expect(port.start).toHaveBeenCalledTimes(1);
  });

  it('forwards the tone to the factory on the lazy path', () => {
    const tone = { frequencies: [660], cadence: { onMs: 500, offMs: 500 }, gain: 0.2 };
    renderRingback(fakeBinding({ phase: 'connecting' }), { tone });
    expect(hoisted.createRingbackPort).toHaveBeenCalledTimes(1);
    expect(hoisted.createRingbackPort).toHaveBeenCalledWith(tone);
  });

  it('constructs no port and touches no audio when the hook is never called', () => {
    const binding = fakeBinding({ phase: 'connecting' });
    renderHook(() => binding.phase);
    expect(hoisted.createRingbackPort).not.toHaveBeenCalled();
  });

  it('never touches a supplied port on unmount when it never rings', () => {
    const port = fakePort();
    const { unmount } = renderRingback(fakeBinding({ phase: 'idle' }), { port });
    expect(port.start).not.toHaveBeenCalled();
    expect(() => unmount()).not.toThrow();
    expect(port.stop).not.toHaveBeenCalled();
    expect(port.close).not.toHaveBeenCalled();
  });
});
