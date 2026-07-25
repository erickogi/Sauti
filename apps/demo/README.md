# Sauti demo

A two-tab "call yourself" demo for the Sauti web stack. It runs a tiny local
signaling and token server, creates a call with `createSautiCall`, joins a room, and
renders the prebuilt `CallScreen` from `@sauti/react-ui`.

## Run

From the repo root:

```
pnpm install
pnpm demo
```

`pnpm demo` builds the workspace packages, then starts the dev server and Vite
together. Open the printed HTTPS URL (something like `https://127.0.0.1:5173`) in two
browser tabs. The certificate is self-signed, so accept the browser warning once per
tab. Enter the same room id in both tabs, allow the microphone, and you will hear
yourself.

## How it fits together

- `server/dev-server.ts` runs the `@sauti/server` signaling on a plain HTTP server at
  path `/ws` and exposes `POST /token`. Vite serves the app over HTTPS and proxies
  `/ws` and `/token` to this server, so the whole demo lives on one origin and one
  certificate. `@sauti/core` requires a `wss://` endpoint in a secure context, which
  the HTTPS origin provides.
- `server/token.ts` mints an opaque dev token (base64url JSON) that the server's
  `authorize` hook decodes. This is a development stand-in, not a signed credential.
  A real deployment issues signed tokens from an authenticated backend.
- `server/redis.ts` is an in-memory `RedisPort`. It keeps the demo dependency-free.
  Because both tabs connect to the same server process, its in-process pub/sub is
  enough. A real deployment uses Redis so signaling can span processes.
- ICE defaults to public STUN (`stun:stun.l.google.com:19302`), which is enough for
  two tabs on one machine. A real deployment needs a TURN server for peers behind
  symmetric NATs. Override the STUN URL with `SAUTI_DEMO_STUN` and the server port
  with `SAUTI_DEMO_PORT`.

## The part worth copying

`src/App.tsx` is the integration example. It owns the room entry, token fetch, and
`call.join`, then hands the joined `call` to `CallScreen`. The package renders the call
surface; the app owns the orchestration.
