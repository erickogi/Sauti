# @sauti/react-ui

A prebuilt, domain-agnostic call surface for `@sauti/core`, styled with plain CSS
custom properties. It renders the in-call state that `useSautiCall` exposes: status,
duration, per-participant roster, quality, mute, device selection, the audio-unlock
affordance, and the weak-connection banner. It holds no join, token, signaling, or
sharing logic. That orchestration stays in your app.

## Install

```
pnpm add @sauti/react-ui @sauti/react @sauti/core react react-dom
```

Import the stylesheet once, near your app root:

```ts
import '@sauti/react-ui/styles.css';
```

## The entry point: CallScreen

`CallScreen` is a thin composition of the styled primitives. Give it a `SautiCall`
(created and joined by your app) and it renders the whole surface.

```tsx
import { createSautiCall } from '@sauti/core';
import { CallScreen } from '@sauti/react-ui';
import '@sauti/react-ui/styles.css';

const call = createSautiCall();

function Screen({ selfId }: { selfId: string }) {
  return <CallScreen call={call} selfParticipantId={selfId} />;
}
```

`CallScreen` calls `useSautiCall(call)` for you. It does not join, mint tokens, resolve
signaling URLs, or share links.

Props:

- `call` — a `SautiCall` from `createSautiCall`.
- `selfParticipantId?` — the id of the local participant, used to sort the roster and
  label the local row.
- `labels?` — a `Partial<SautiLabels>` overriding any visible string.
- `slots?` — swap `controlBar` or `participantTile` for your own component.
- `renderParticipant?` — render each roster entry yourself.
- `onEnd?` — called after the end control leaves the call.
- `className?`, `style?`.

## The next depth: primitives

Each primitive takes the binding (or a slice of it), a `className`/`style`, and
`labels`. Compose them if `CallScreen`'s layout does not fit.

`MuteButton`, `EndCallButton`, `ControlBar`, `ParticipantList`, `ParticipantTile`,
`QualityIndicator`, `DurationTimer`, `CallStatus`, `AudioDevicePicker`,
`AudioUnlockPrompt`, `WeakConnectionBanner`.

`AudioDevicePicker` selects the audio input device. The web core exposes input-device
selection only (`enumerateDevices` plus `selectInputDevice`), so there is no output
picker.

## The escape hatch: useSautiCall

For a fully custom UI, drop to the headless hook re-exported from `@sauti/react`:

```tsx
import { useSautiCall } from '@sauti/react-ui';
```

## Theming

The stylesheet defines light defaults on `:root` and a dark set under
`prefers-color-scheme: dark`. Override any of the namespaced properties:

`--sauti-color-bg`, `--sauti-color-surface`, `--sauti-color-text`,
`--sauti-color-muted`, `--sauti-color-border`, `--sauti-color-accent`,
`--sauti-color-on-accent`, `--sauti-color-danger`, `--sauti-color-danger-surface`,
`--sauti-color-quality-good`, `--sauti-color-quality-fair`,
`--sauti-color-quality-poor`, `--sauti-radius`, `--sauti-font`,
`--sauti-space-unit`, `--sauti-touch-target`.

`SautiThemeProvider` writes a partial theme object as inline custom properties for a
subtree. Passing nothing yields the defaults.

```tsx
import { SautiThemeProvider } from '@sauti/react-ui';

<SautiThemeProvider theme={{ colorAccent: '#7c3aed', radius: '16px' }}>
  <CallScreen call={call} />
</SautiThemeProvider>;
```

Motion is applied only under `prefers-reduced-motion: no-preference`.
