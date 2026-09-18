# Sauti

Sauti (Swahili for "voice") is a domain-agnostic, self-hosted multi-party audio
calling library. It handles the WebRTC mesh, signaling, reconnection, and in-call
state. 


## Packages

| Package | What it is |
|---|---|
| `@sauti/protocol` | Shared wire types, schemas, and the frozen frame contract |
| `@sauti/server` | Framework-agnostic signaling library configured by injected dependencies (Redis, TURN config, an authorize hook, an HTTP server to attach to) |
| `@sauti/core` | Framework-agnostic browser client: mesh, perfect negotiation, in-call state |
| `@sauti/react` | Thin React binding over `@sauti/core` |
| `io.sauti:engine` | Pure Kotlin/JVM calling engine: `CallSession`, signaling, mesh, perfect negotiation, reconnect/resume, quality. No Android or WebRTC types. |
| `io.sauti:android` | The Android library: WebRTC-backed transport, audio/telephony coordination, microphone foreground service, resume persistence. The one most apps consume. |
| `io.sauti:rx2` | Thin RxJava2 adapter over the engine for non-coroutine codebases. |
| `io.sauti:ui-compose` | Optional Jetpack Compose UI: the ready-made call host (`Sauti` + `SautiCallActivity`), in-call/incoming/connecting screens, slide-to-answer, the opt-in over-other-apps bubble and in-app return bar. |

## Docs

- [`docs/ANDROID-ADOPTER-GUIDE.md`](docs/ANDROID-ADOPTER-GUIDE.md) — **start here if you
  are a mobile developer adding calling to an Android app.** The ready-made host, end to
  end: install, manifest, `Config`, tokens, outgoing/incoming, slide-to-answer, the
  return bar and bubble, theming, and a full worked example.
- [`docs/INTEGRATION.md`](docs/INTEGRATION.md) — the full cross-audience guide: backend
  signaling server, web client, the low-level Android `SautiClient`, and DevOps.
- [`CONTRACT.md`](CONTRACT.md) — the frozen wire protocol.

## The contract

`CONTRACT.md` is the frozen, versioned wire protocol and object model that every
package conforms to. Change the contract before changing any package.


