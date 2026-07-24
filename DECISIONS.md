# Decisions

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
