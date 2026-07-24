# @sauti/core

A framework-agnostic browser client for Sauti multi-party audio calls. It speaks the
`@sauti/protocol` wire format, keeps one `RTCPeerConnection` per remote participant in
a mesh, runs perfect negotiation off the shared politeness helper, and exposes a single
observable `CallState` snapshot plus a typed event emitter. It holds no UI framework
dependency; `@sauti/react` is the only package that binds it to React.

## Using it

```ts
import { createSautiCall } from '@sauti/core';

const call = createSautiCall();

call.on('error', (err) => console.error(err.code, err.message));
call.subscribe(() => render(call.getSnapshot()));

await call.acquireMic();
await call.join({ url: 'wss://sauti.example/ws', token });

call.setMuted(true);
call.setHold(true);
await call.unlockAudio();
call.leave();
```

`createSautiCall` reads WebRTC, WebSocket, `getUserMedia`, and audio-sink construction
from an injected runtime. The default runtime binds to the browser globals; tests pass
fakes through the `runtime` option, so the core needs no real browser to unit test.

## Commands and state

Commands: `join`, `leave`, `setMuted`, `setHold`, `acquireMic`, `unlockAudio`,
`enumerateDevices`, `selectInputDevice`.

`getSnapshot()` returns the current `CallState`: `participants` (each with
`participantId`, `metadata`, `muted`, `onHold`, `connectionState`, `quality`),
`localMuted`, `localOnHold`, `durationMs`, aggregate `quality`, `reconnecting`,
`audioBlocked`, and `fallback`. The snapshot is a fresh object on every change and a
stable reference between changes, so it drives `useSyncExternalStore` without tearing.

## Testing layers

The unit suite runs under jsdom with scriptable fakes for `RTCPeerConnection`,
`MediaStream`/`MediaStreamTrack`, `WebSocket`, and `getUserMedia`. jsdom has no WebRTC
media stack, so real SDP and ICE interop is out of scope here. The jsdom suite covers
wire behaviour, mesh bookkeeping, negotiation policy, reconnect, duration, quality, and
error mapping.

The live media path is a known gap. The intended vehicle is a headed-browser Playwright
interop project that drives two real clients through a connected call, asserts
`iceConnectionState` reaches `connected`/`completed`, and confirms audio flows both ways.
That project is not yet implemented, so NEG-05 (two-client convergence) is not covered by
the current suite. Do not read the jsdom fakes as evidence of real-browser interop; the
two layers are deliberately kept separate.
