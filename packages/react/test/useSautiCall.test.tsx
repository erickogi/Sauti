import { describe, it, expect, afterEach, vi } from 'vitest';
import { act, cleanup, render, renderHook, screen } from '@testing-library/react';
import { renderToString } from 'react-dom/server';
import { useSautiCall } from '../src/useSautiCall.js';
import * as pkg from '../src/index.js';
import { FakeCall } from './fakeCall.js';

afterEach(cleanup);

it('exposes the hook from the package entrypoint [REACT-04]', () => {
  expect(pkg.useSautiCall).toBe(useSautiCall);
});

function Duration({ call }: { call: FakeCall }): JSX.Element {
  const state = useSautiCall(call);
  return <span data-testid="duration">{state.durationMs}</span>;
}

describe('useSautiCall', () => {
  it('re-renders on CallState changes without tearing [REACT-01]', () => {
    const errors = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const call = new FakeCall();
    render(<Duration call={call} />);
    expect(screen.getByTestId('duration').textContent).toBe('0');
    act(() => call.push({ durationMs: 4200 }));
    expect(screen.getByTestId('duration').textContent).toBe('4200');
    expect(errors).not.toHaveBeenCalled();
    errors.mockRestore();
  });

  it('does not crash under server-side rendering [REACT-02]', () => {
    const call = new FakeCall();
    call.push({ durationMs: 7 });
    const html = renderToString(<Duration call={call} />);
    expect(html).toContain('7');
  });

  it('returns stable-referenced bound commands that delegate to the core [REACT-03]', () => {
    const call = new FakeCall();
    const { result, rerender } = renderHook(() => useSautiCall(call));
    const first = result.current;
    act(() => call.push({ durationMs: 1 }));
    rerender();
    const second = result.current;
    expect(second.join).toBe(first.join);
    expect(second.leave).toBe(first.leave);
    expect(second.setMuted).toBe(first.setMuted);
    expect(second.setHold).toBe(first.setHold);
    expect(second.unlockAudio).toBe(first.unlockAudio);

    second.setMuted(true);
    second.setHold(true);
    second.leave();
    void second.join({ url: 'wss://x', token: 't' });
    void second.unlockAudio();
    void second.acquireMic('mic-2');
    void second.selectInputDevice('mic-2');
    void second.enumerateDevices();
    expect(call.setMuted).toHaveBeenCalledWith(true);
    expect(call.setHold).toHaveBeenCalledWith(true);
    expect(call.leave).toHaveBeenCalledTimes(1);
    expect(call.join).toHaveBeenCalledWith({ url: 'wss://x', token: 't' });
    expect(call.unlockAudio).toHaveBeenCalledTimes(1);
    expect(call.acquireMic).toHaveBeenCalledWith('mic-2');
    expect(call.selectInputDevice).toHaveBeenCalledWith('mic-2');
    expect(call.enumerateDevices).toHaveBeenCalledTimes(1);
  });

  it('reflects the current snapshot fields [REACT-01]', () => {
    const call = new FakeCall();
    const { result } = renderHook(() => useSautiCall(call));
    expect(result.current.localMuted).toBe(false);
    act(() => call.push({ localMuted: true, reconnecting: true, quality: 'poor' }));
    expect(result.current.localMuted).toBe(true);
    expect(result.current.reconnecting).toBe(true);
    expect(result.current.quality).toBe('poor');
  });

  it('unsubscribes from the core on unmount [REACT-05]', () => {
    const call = new FakeCall();
    const { unmount } = renderHook(() => useSautiCall(call));
    expect(call.listenerCount()).toBe(1);
    unmount();
    expect(call.listenerCount()).toBe(0);
  });
});
