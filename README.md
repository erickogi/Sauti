# Sauti

Sauti (Swahili for "voice") is a domain-agnostic, self-hosted multi-party audio
calling library. It handles the WebRTC mesh, signaling, reconnection, and in-call
state. 


## Packages

| Package | What it is 
|---|---|---|
| `@sauti/protocol` | Shared wire types, schemas, and the frozen frame contract 
| `@sauti/server` | Framework-agnostic signaling library configured by injected dependencies (Redis, TURN config, an authorize hook, an HTTP server to attach to)
| `@sauti/core` | Framework-agnostic browser client: mesh, perfect negotiation, in-call state 
| `@sauti/react` | Thin React binding over `@sauti/core` 
| `io.sauti:calling-core` | Reusable Kotlin calling engine for Android 

## The contract

`CONTRACT.md` is the frozen, versioned wire protocol and object model that every
package conforms to. Change the contract before changing any package.


