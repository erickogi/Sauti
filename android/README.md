# Sauti Android

A Kotlin implementation of the Sauti call client for Android. It re-implements the
`CONTRACT.md` wire protocol and object model in Kotlin. It does not depend on the
TypeScript packages; the two speak the same frames so a Kotlin peer and a
`@sauti/core` web peer interoperate.

## Modules

```
:engine   pure Kotlin/JVM. No android, androidx, or org.webrtc on the classpath.
          CallSession, the signaling client, the full mesh, perfect negotiation,
          server-anchored duration, reconnect/resume, mute/hold over signaling,
          and the quality classifier. JVM-unit-testable with fakes.
:android  com.android.library. The WebRTC-backed RtcFactory and the OkHttp signaling
          transport that implement the engine ports, plus the audio session
          coordinator, telephony and connectivity watchers, the microphone
          foreground service, and DataStore resume persistence.
:rx2      com.android.library. A thin RxJava2 adapter over the engine's Flow and
          suspend surface. No call logic of its own.
```

The engine names no `org.webrtc` type. It reaches the media stack only through the
`PeerConnectionPort` / `RtcFactory` interfaces (SDP as `String`, ICE as a data
class). The `:android` module supplies the concrete implementation.

## Build

```
./gradlew :engine:test
./gradlew :engine:build :android:assembleRelease :rx2:assembleRelease
./gradlew :android:assembleDebug :rx2:assembleDebug
```

Set `sdk.dir` in `local.properties` (or `ANDROID_HOME`) to your Android SDK. The
engine module needs no SDK.

## Gates

All are wired into the build and the `sauti-android` CI workflow.

- `scripts/purity.sh` fails on `driver|passenger|trip|rider` in any module's
  `src/main`. The build depends on it.
- `scripts/check_engine_purity.sh` fails if the engine imports
  `android`, `androidx`, or `org.webrtc`.
- `scripts/check_no_comments.sh` fails on any code comment in module Kotlin sources.
- `:android:checkSoAlignment` extracts the WebRTC `.so` from
  `io.getstream:stream-webrtc-android` and fails if any 64-bit library carries a
  LOAD segment aligned below 16 KB. `:android:checkSoAlignmentFails` proves the gate
  rejects a deliberately misaligned fixture.

## minSdk 21

Every API 31+ call has a `Build.VERSION.SDK_INT` guard and a working legacy path:

- Audio routing: `setCommunicationDevice` + `AudioDeviceCallback` on API 31+,
  `setSpeakerphoneOn` + `startBluetoothSco` below.
- Telephony: `TelephonyCallback` on API 31+, `PhoneStateListener` below.
- Notification: `Notification.CallStyle` on API 31+, `NotificationCompat` below.

`./gradlew :android:lint` reports no `NewApi` violations at minSdk 21. The WebRTC
fork declares `minSdkVersion 21`, so the merged manifest stays at 21.

## Duration

Duration is server-anchored, never a local stopwatch. The engine captures
`clockOffset = serverNow - localNow` once from `ready`, and reports
`elapsed = (localNow + clockOffset) - startedAt`. It is recomputed from the
server anchor on every `ready`, including `resumed: true`, so it survives reconnect
and process death.

## Perfect negotiation

Politeness is the lexicographic compare of the two `participantId` strings: the
smaller id is polite. This matches `@sauti/protocol`'s `politePeer` byte for byte
(`a <= b ? a : b`), computed locally with no server round-trip.
