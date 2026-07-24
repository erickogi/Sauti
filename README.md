# Sauti

Sauti (Swahili for "voice") is a domain-agnostic, self-hosted multi-party audio
calling library. It handles the WebRTC mesh, signaling, reconnection, and in-call
state. It knows nothing about the application on top of it: no users, no roles, no
trips. It deals in opaque participants and rooms, and the host application maps its
own domain onto participant metadata.

It grew out of a proof of concept for internet audio calls between the two sides of
a ride, but the library itself carries none of that vocabulary and is usable by any
application that needs small-group audio.

## Packages

| Package | What it is | Status |
|---|---|---|
| `@sauti/protocol` | Shared wire types, schemas, and the frozen frame contract | Phase 1 |
| `@sauti/server` | Framework-agnostic signaling library configured by injected dependencies (Redis, TURN config, an authorize hook, an HTTP server to attach to) | Phase 1 |
| `@sauti/core` | Framework-agnostic browser client: mesh, perfect negotiation, in-call state | Phase 1 (later wave) |
| `@sauti/react` | Thin React binding over `@sauti/core` | Phase 1 (later wave) |
| `io.sauti:calling-core` | Reusable Kotlin calling engine for Android | Phase 1 (later wave) |

## The contract

`CONTRACT.md` is the frozen, versioned wire protocol and object model that every
package conforms to. Change the contract before changing any package.

## Design

The full design and the reasoning behind it are in
`../docs/voip-audio-calling/PHASE1-RECOMMENDATIONS.md`.

## Rules

- No comments in code, in any language, anywhere. Markdown docs carry the
  explanation.
- The library never contains host-domain vocabulary. A CI purity check fails the
  build if it does.
- Everything is tested.
