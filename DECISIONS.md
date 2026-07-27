# Decisions

## Ringback ignores ringer mode; incoming ring honors it

The in-app ring cue (capability 1B, `io.sauti.android.ring`) treats the two
directions differently. Ringback, the tone a caller hears while waiting for the
callee, plays through `ToneGenerator` on the music stream and is never gated by the
device ringer mode. A caller who started the call is not surprised by hearing the
waiting tone, and silencing it on a SILENT phone would leave the caller with no
feedback that the call is live. Only the incoming ringtone and vibration respect
SILENT, VIBRATE, and NORMAL, because those interrupt a callee who did not initiate
anything. The ringer mode is an input to `RingReducer.reduce`, so the split lives in
the pure layer and stays fully unit-tested.

## Incoming rings for IDLE and CONNECTING

`RingReducer` returns an incoming cue while the callee-side phase is IDLE or
CONNECTING, not IDLE alone. Adopters model an invite in one of two ways: some ring
before joining any room (phase still IDLE), others treat the invite as an early
CONNECTING. Covering both makes the component robust to either wiring. The answered
signal for a callee is the local phase reaching CONNECTED, at which point the cue
stops. An adopter that wants the ring to cut the instant the user taps accept calls
`SautiRinger.stop()` at that point rather than waiting for the phase transition.

## Room enumeration uses a registry hash, not SCAN

`listActiveRooms` enumerates active rooms by reading a maintained registry hash at
`<namespace>:rooms` with `hgetall`, then reading each room hash. It does not call
`SCAN`.

The acceptance note behind this area (DEPS-LISTROOMS) was phrased as "enumerate via
SCAN, never KEYS". The load-bearing half of that is the anti-goal: `KEYS` is a single
blocking O(all-keys) traversal of the whole keyspace and must never appear. That is
enforced by `contract.test.ts`, which greps the server source for any `KEYS` or
`scan` call and finds none.

`SCAN` itself is not reachable here because `RedisPort` is the frozen nine-method
contract in `CONTRACT.md`, and it deliberately exposes no `scan`. Adding one would
widen the injected surface every host has to implement, for a cursor loop that is
still O(rooms-worth-of-keys) and can miss or double-count rooms mutated mid-scan. The
registry hash gives the same enumeration in one round trip plus one read per active
room, is exact rather than best-effort, and stays in sync with room lifecycle: a
fresh claim writes the room id into the registry (`hsetnx`), and `cleanupRedisRoomIfEmpty`
removes it when the room hash empties.

So the enumeration meets the real requirement (never KEYS, O(active rooms)) through the
registry hash, and the `RedisPort` surface stays at nine methods.

## Real-browser interop (NEG-05) is a documented gap, not a shipped suite

jsdom has no WebRTC media stack, so the `@sauti/core` unit suite exercises SDP, ICE, and
the media path against scriptable fakes only. NEG-05 asks for two real browser clients
that join simultaneously, converge to `iceConnectionState` `connected`/`completed`, and
carry two-way audio. That needs a headed-browser Playwright project, a running signaling
server, and installed browsers, none of which exist in this tree yet.

Rather than assert a suite that does not exist, the core README now names Playwright as
the intended vehicle and states plainly that the interop leg is not yet implemented, so
NEG-05 is a known gap. PKG-06's jsdom-vs-interop separation still holds: the config pins
`environment: 'jsdom'`, the fakes are named `FakePeerConnection`/`FakeWebSocket`, and the
README keeps the two layers distinct instead of reading the fakes as interop evidence.
