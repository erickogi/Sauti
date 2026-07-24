# @sauti/react

A thin React binding over `@sauti/core`. It is one hook, `useSautiCall`, built on
`useSyncExternalStore`. It holds no WebRTC, WebSocket, or media logic; every call
behaviour lives in the core. This package exists only so React components re-render on
`CallState` changes without tearing.

```tsx
import { createSautiCall } from '@sauti/core';
import { useSautiCall } from '@sauti/react';

const call = createSautiCall();

function CallBar() {
  const { participants, durationMs, localMuted, setMuted, leave } = useSautiCall(call);
  return (
    <div>
      <span>{participants.length} in the room</span>
      <span>{Math.floor(durationMs / 1000)}s</span>
      <button onClick={() => setMuted(!localMuted)}>{localMuted ? 'Unmute' : 'Mute'}</button>
      <button onClick={() => leave()}>Leave</button>
    </div>
  );
}
```

The hook subscribes to the core store, returns the current snapshot spread with a set of
stable-referenced bound commands (`join`, `leave`, `setMuted`, `setHold`, `acquireMic`,
`unlockAudio`, `enumerateDevices`, `selectInputDevice`), and unsubscribes on unmount. It
passes the same snapshot as the server snapshot, so it does not throw under SSR. The core
manages its own hidden audio elements, so there is nothing for the component to render for
playback beyond calling `unlockAudio` from a user gesture on autoplay-restricted browsers.
