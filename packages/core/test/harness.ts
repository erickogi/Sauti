import { createSautiCall } from '../src/index.js';
import type { SautiCall } from '../src/call.js';
import type { SautiOptions } from '../src/types.js';
import { flush, makeRuntime, readyFrame, type RuntimeHarness } from './fakes.js';
import type { FakeWebSocket } from './fakes.js';

export const URL = 'wss://sauti.test/ws';
export const TOKEN = 'extended-join-token';

export interface Connected {
  h: RuntimeHarness;
  call: SautiCall;
  socket: FakeWebSocket;
}

export function makeCall(
  options: SautiOptions = {},
  runtimeHarness?: RuntimeHarness
): { h: RuntimeHarness; call: SautiCall } {
  const h = runtimeHarness ?? makeRuntime();
  const call = createSautiCall({ ...options, runtime: h.runtime });
  return { h, call };
}

export async function connect(
  ready: ReturnType<typeof readyFrame>,
  options: SautiOptions = {},
  runtimeHarness?: RuntimeHarness
): Promise<Connected> {
  const { h, call } = makeCall(options, runtimeHarness);
  const promise = call.join({ url: URL, token: TOKEN });
  await flush();
  const socket = h.lastSocket();
  socket.open();
  socket.deliver(ready);
  await promise;
  return { h, call, socket };
}

export { readyFrame };
