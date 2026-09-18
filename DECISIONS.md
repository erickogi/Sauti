# Decisions

## Incoming screen rendering is proving-ground tested, not Robolectric

The `io.sauti:ui-compose` incoming half keeps every unit under plain JVM: the phase
mapping in `incomingPhaseFor`, the `SautiIncomingCallHost` config round trip, and the
ordered side effects of `IncomingCallCoordinator` (notification cancel then ring
start, then stop and release on dismiss). The composable click and render checks that
would need `androidx.compose.ui:ui-test-junit4` are not wired, so the accept and
decline callbacks, the caller name, and the absence of accept in CONNECTING are
verified through the extracted logic plus the passenger app proving ground. The
no-double-ring guarantee is asserted as an ordering test on the coordinator seam the
Activity delegates to, so the invariant is covered without a device.

## incomingPhaseFor is provided but not driven by the batteries Activity

`SautiIncomingCallActivity` flips a local `accepted` flag to move INCOMING to
CONNECTING for a snappy response, and forwards accept and decline to the injected
`SautiIncomingCallHost` callbacks. It holds no `SautiClient`, so it does not observe
the engine `CallPhase` itself. `incomingPhaseFor` is the pure mapping an adopter or a
client-aware caller uses to drive the CONNECTED handoff and the LEFT finish; keeping
it out of the domain-free Activity preserves the module direction and the
no-client-in-the-Activity rule while leaving the mapping fully unit tested.

## Teardown runs on decline and onDestroy

The incoming Activity releases the dedupe slot only on final teardown, from decline
and from `onDestroy`, not from `onStop`. Releasing on `onStop` would free the slot
while the user is briefly away during CONNECTING and let a duplicate invite present
over a live acceptance. The notification cancel is idempotent and safe to repeat, so
it also runs on teardown; the slot release is the one step gated to the terminal
path.

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

## Opus DTX inflates the cumulative-loss metric (Wave 2, capability 2C)

Enabling Opus DTX (`usedtx=1`) suppresses RTP during silence and sends comfort noise, so
`packetsReceived` flattens while `packetsLost` keeps ticking. The quality path computes
`loss = packetsLost / (packetsLost + packetsReceived)`, so a silent-but-alive peer reads
as rising loss and the existing classifier moves through DEGRADED to POOR and raises the
fallback flag after its hysteresis and consecutive-poor thresholds. This is expected, not
a bug: DTX is opt-in and default off, the classifier is unchanged, and the exact behavior
is pinned by `QualityDtxTest` (engine) and `dtx.test.ts` (core) as executable records.

Dead-peer detection is unaffected. `Unreachable` is emitted only from the server
`ParticipantUnreachable` frame; there is no client RTP-flow liveness timer, so DTX silence
cannot trip it. `CallSessionQoeTest.highLossSampleUpdatesQualityButNeverEmitsUnreachable`
proves a high-loss stats sample changes the quality label but never emits `Unreachable`.

An adopter enabling DTX on a lossy or metered link should expect more DEGRADED/POOR quality
labels during one-way silence. If that is undesirable, leave DTX off (the default) or have
the domain treat the QoE `fallback` signal with DTX in mind.

## Wake-push contract constants and parser style (Wave 3, capability 3A)

The call-invite wake push (Wave 3) gets a frozen kind and version, deliberately independent of the
WebSocket `PROTOCOL_VERSION`: `WAKE_PUSH_KIND = "io.sauti.wake.call"` and `WAKE_PUSH_VERSION = 1`. A
push can reach an app build that never opened the socket, and the push schema must be able to evolve
without a WS-frame break, so the two version lines are separate. The `kind` literal is frozen here so
a callee can distinguish a newer schema from garbage before touching a socket.

The wake payload lives in `@sauti/protocol/src/wakePush.ts` (a new file, one added `index.ts`
re-export, no existing type changed) and is validated with zod. zod is already a `@sauti/protocol`
dependency (used by `frames.ts`), so this adds no dependency and keeps the validation style consistent
with the other wire types in the package. `decodeWakePushData` is a tolerant reverse of the encoder;
its reason ordering is fixed: kind missing-or-mismatched -> `wrong-kind`; kind ok but `v` missing or
not 1 -> `unsupported-version`; kind and `v` ok but a required field missing/non-numeric ->
`malformed`. It emits `payload.v` as the numeric `WAKE_PUSH_VERSION`, not the string form carried in
the FCM data map.

The payload metadata is a flat `Record<string,string>` (FCM data messages are string-to-string and it
maps 1:1 onto Wave 1 `SautiIncomingCall`'s `Map<String,String>`), narrower than `model.ts`'s
`Record<string,Json>` by design. Metadata keys are namespaced under a reserved `meta.` prefix in the
data map so they cannot collide with the fixed top-level keys, and `WAKE_PUSH_JOIN_HINT_KEY` is the
reserved key that carries `joinHint` into the callee metadata. `callerDisplayHint` is an opaque
host-supplied label; the library never derives, logs, or inspects it.

## Overlay bubble: draggable compact handle and dark-mode contrast (adoption)

During the passenger-app adoption the Wave 6 overlay bubble evolved from a fixed
tap-to-return pill into a compact draggable handle. The handle drags and snaps to the
nearest screen edge (tap versus drag resolved by touch slop), a tap expands it to
return/mute/end controls and a second tap collapses it, and it re-clamps to the nearest
edge on a configuration change so a rotation cannot strand it off-screen. The geometry,
gesture, and expansion logic live in pure helpers (`BubbleGeometry`, `BubbleGesture`,
`BubbleExpansion`, `BubbleConfig`) so they are JVM-tested; only the `WindowManagerOverlayHost`
touch wiring stays device-only.

Visibility on dark themes and busy wallpapers is solved without any branding. The
collapsed handle keeps an opaque accent fill but adds an adaptive edge ring plus a drop
shadow, and the glyph and ring tones are chosen from the fill and surface luminance by
the pure `BubbleContrast` helper rather than a fixed `onAccent`. `contrastToneOn` uses
WCAG relative luminance with the pivot `0.179` (the luminance at which contrast to white
equals contrast to black), so the glyph stays readable whatever accent an adopter themes.
The visible fill is inset to 46dp inside the fixed 56dp touch box so the shadow and ring
have room without changing the collapsed geometry the snap and clamp math depend on. The
host is themeable via `SautiBubbleTheme` and `BubbleConfig`.

## Incoming-call adoption enablers: notification branding and an imperative pre-answer ring

Two additive changes let an adopter drive the library's incoming-call presentation without
regressing its own branding or its pre-answer ring. A parity analysis before switching the
passenger app onto the library found these two gaps blocked a no-regression switch.

`IncomingCallOverrides` gained an optional `smallIcon` and `contentText`. `IncomingCallNotification`
hardcoded a generic phone small icon on both the plain and the CallStyle path and set no body, so an
adopter with its own brand icon and title/body would lose them on switch. The overrides default to
the previous icon and no body, so the default path is unchanged.

For the pre-answer ring the choice was an imperative facade (`SautiIncomingRing`) over feeding a
synthetic `CallState` into the flow-driven `SautiRinger`. The incoming screen rings before the user
accepts, where there is no `SautiClient` or `CallState` yet, but `SautiRinger.start` requires a
`StateFlow<CallState>` and rings only in IDLE or CONNECTING. Fabricating a `CallState` the caller
does not own would leak an engine type into a context with no engine and shift the stop-or-leak risk
onto the adopter. `SautiIncomingRing` instead reads the ringer mode once and applies the existing
incoming mapping, with `start()` and `stop()` idempotent and leak-safe. To reuse the mapping without
duplicating it, `RingReducer.incomingFor` was widened from private to the public `RingReducer.incoming`;
`reduceIncoming` still delegates to it, so existing callers are unaffected. The leaf ring ports stay
internal; adopters go through `create(context, overrides)`.

`IncomingCallNotification.build`/`post` also gained an overload that takes a caller-supplied full-screen
`PendingIntent` instead of a `ComponentName`. The `ComponentName` overloads build their launch intent
from the library's own extra keys via `SautiIncomingCall.toExtras`, which only works if the adopter's
activity reads those keys. An adopter that already launches its own incoming-call activity with its own
extras needs to keep that intent; the `PendingIntent` overload lets it pass its own launch intent while
the library still owns the channel, small icon, category, full-screen flag, insistent flag, and the
`callId.hashCode()` id. The `ComponentName` overloads now delegate to the `PendingIntent` one, so their
behaviour is unchanged.

## Icon control primitive and control-state color tokens (call UI move, step 1)

The adopter call look uses circular icon control buttons, but the library shipped only text
buttons, which is why adopters re-implement the controls. Added `SautiIconButton`, a circular icon
button (default 64dp, glyph centred at `size * 0.44` inside a `CircleShape`), glyph-slotted so an
adopter passes a library Canvas glyph or its own painter. Added the missing audio-device and minimize
glyphs to the Canvas glyph set, and icon-variant `SautiMuteButton`/`SautiEndCallButton` alongside the
existing text controls. Extended `SautiColors` with six additive, defaulted control-state tokens
(`controlIdleBackground`/`controlIdleContent`, `controlActiveBackground`/`controlActiveContent`,
`positive`/`onPositive`) so the control buttons are themeable; `controlIdleContent` defaults to `accent`.
The library defaults are chosen to match the reference adopter's constants so a later swap onto these
buttons is visually identical. Existing text controls, glyphs, and every `SautiColors` construction site
are unchanged. This is the keystone for the avatar-centric solo call screen that follows.

## Solo call screen and avatar (call UI move, step 2)

Added `SautiSoloCallScreen` (the one-to-one avatar-centric in-call layout), the public `SautiAvatar`, and
`SautiAudioButton` (an icon audio-device picker), all built on the step-1 icon controls. The layout mirrors
the reference adopter's in-call screen one-for-one: a 96dp avatar, the name with a peer-muted badge, the
call status, a bold duration, the quality indicator, the interruption banner, an icon control row
(mute / audio / end), and a top-start minimize button. The no-peer waiting state falls out when there is no
other participant (no avatar or name, the waiting status, `00:00`). `onAccent` (text-on-accent, defaulted
white) and a `minimize` string were appended additively. The screen takes a prebuilt `SautiCallUiState` and
reads the ambient `SautiTheme`, matching the other state-taking members; the higher-tier `SautiCallScreen`
(client-driven, self-themed) is unchanged. An adopter who forgets the `SautiTheme` wrapper falls back to the
default colours and strings rather than crashing.

## Incoming call screen icon actions (call UI move, step 3)

`SautiIncomingCallScreen` now renders circular icon accept and decline actions (72dp) instead of text
buttons, mirroring the adopter incoming screen: decline uses `CallEndGlyph` on `danger`/`onDanger`, accept
uses `PhoneGlyph` on `positive`/`onPositive`. The private default avatar was replaced by the public
`SautiAvatar`, and the top padding, spacing, and typography were aligned to the adopter layout. The
`avatar`/`status`/`colors` customization slots are preserved. One deliberate tradeoff: the built-in name
and subtitle now use `MaterialTheme.typography` (headlineSmall bold, bodyMedium) to match the adopter
pixel-for-pixel, so the `SautiTypography` parameter no longer restyles those two texts (it still flows to
custom `avatar`/`status` slots); the old accept button also pulled its content colour from the danger
palette, which this corrects. This completes the Compose screen set (solo call, incoming, controls,
avatar, audio button) published at 0.1.6 for the app to adopt.

## Connecting screen (call UI move, step 4)

Added `SautiConnectingScreen(callerName, modifier, onCancel)`, the post-accept "Connecting" screen for the
window after the callee accepts and before the call state object exists. It mirrors the top block of
`SautiSoloCallScreen` exactly (surface background, centred column, 48dp top padding, 96dp `SautiAvatar`, the
name in headlineSmall bold `onSurface`) so the handoff into the in-call screen does not visibly jump. The
status line reuses the `CallStatus` connecting styling (`SautiTheme.typography.status`, `onSurfaceMuted`, a
polite live region) without needing a `SautiCallUiState`, since none exists yet at this stage. A blank caller
name renders only the status. When `onCancel` is supplied it shows a single `SautiEndCallButton` so the user
can abort while connecting; otherwise the bottom row is omitted. The adopter's previous bare spinner is
replaced by this themed screen. Published at 0.1.7.

## `onEndCall`: end-of-call as a first-class host callback (0.1.20)

`SautiCallHost.Config` gained `onEndCall: suspend (callId) -> Unit`, symmetric with the existing
`onStartCall`/`onCancelCall`. The gap it closes: hanging up a **connected** call fired no backend signal, so a
trip call that was answered and then ended left a stale server-side marker that rejected the next ring with a
conflict until a self-heal on the following attempt. `SautiCallActivity.onEndClicked` now fires `onEndCall`
when a `callId` is present, and the outgoing controller fires it when the media session drops after the peer
had joined (`OutgoingCancelDecision.endOnSessionGone(peerJoined, tearingDown)`, JVM-tested) — the two paths a
user actually ends a live call. It is a distinct callback rather than reusing `onCancelCall` because the
backend transitions differ: cancel aborts a still-ringing call, end settles an already-answered one. Adopters
that do not need it default it to a no-op, so this is additive.

## Slide-to-answer on the incoming screen (0.1.20)

`SautiIncomingCallScreen` gained a `requiresSlide` mode (surfaced as `Config.incomingRequiresSlide`, default
true). Instead of two equal tap buttons it renders a horizontal slide-to-answer track for accept, keeping
decline a single tap. The asymmetry is deliberate: an accidental answer is the costly error (it opens the mic
and bills a call), so accept is made harder to trigger while decline stays instant. The threshold is a pure,
JVM-tested `IncomingSlideDecision.accepts(offsetPx, travelPx, 0.6)` (drag past 60% of the track); the handle
gives a `LongPress` haptic on completion and springs back via `animate` if released short. Accessibility is
not gated behind the drag — the track carries a semantics `onClick(label = accept)` so TalkBack and Switch
users get a tappable Answer action — and a one-shot `accepted` guard plus the existing `incomingCall ?: return`
in `onAcceptClicked` stop a mid-drag call-end from firing a stale accept. `incomingRequiresSlide = false`
restores the classic two-button layout; `onAccept`/`onDecline` are identical either way, so it is a pure
presentation switch.

## In-app return bar, complementary to the over-other-apps bubble (0.1.20)

`installSautiCallBar(application, onReturn, optedIn, theme, content?)` adds a second "return to a live call"
surface alongside the overlay bubble. The two are complementary by design: the bubble (a
`TYPE_APPLICATION_OVERLAY` window needing `SYSTEM_ALERT_WINDOW`) covers "user left my app entirely", while the
bar (a `ComposeView` attached to the resumed activity's `android.R.id.content`, no permission) covers "user is
on another screen of my app". Visibility is the pure `CallBarReducer.visible(optedIn, callActive, onCallScreen)`
— shown only off the `SautiCallActivity` itself — and the installer re-syncs on every activity
resume/pause/destroy and every `CallForegroundService.call` emission, detaching and re-attaching so the view
never leaks across activities. The default `SautiCallBar` draws on new `SautiColors.callBar`/`onCallBar` tokens
(defaulting to the positive tone) so an app whose own chrome is already that color can retheme the bar off
green-on-green; an adopter can also replace the whole bar with a `SautiCallBarContent` lambda.

## Overlay permission asked at the point of need, not on resume (0.1.20)

The overlay-permission request moved out of `onResume` (which shoved the user to a settings screen the moment a
call opened, often mid-dial) to the first time the user **minimizes** an active call — the point where the
bubble is about to be useful. The pure `OverlayPromptDecision.shouldPrompt(optedIn, granted, dismissed)` gates
it, an explainer dialog precedes the system grant screen (launched via a registered `ActivityResultLauncher`),
and a per-call dismissed flag stops it nagging. Prompt shown/dismissed state persists across configuration
change and process death (`onSaveInstanceState`) and resets when a fresh incoming call rebinds. Copy is generic
by default (`SautiStrings.overlayPrompt*`) and an adopter can supply the entire dialog via
`Config.overlayPromptContent` while the host keeps ownership of when to show it and the launcher/dismissed
wiring, so an adopter dialog cannot break the flow.

## `Sauti.handlePush`: the library owns push routing (0.1.21)

Adopters were hand-routing the call push vocabulary — a `when` over `voip-call-incoming` / `-cancelled` /
`-declined` that mapped each to `presentIncomingCall` / `cancelIncomingCall` / `outgoingDeclined`, duplicated
across every app and requiring an app release whenever the protocol grew an event. `Sauti.handlePush(context,
data): Boolean` moves that decision behind the library boundary: forward the raw push data map and the library
recognizes the event, builds the `SautiIncomingCall`, and dispatches; it returns whether it consumed the
message so non-Sauti pushes fall through. New lifecycle events now ship in a library bump with no app change.
The decision is a pure `SautiPushParser.parse(data, keys): SautiPushCommand?` (JVM-tested, 11 cases) so the
envelope handling — nested-`payload` JSON or flat data keys, metadata passthrough for unknown fields, blank
guards — is verifiable off-device; `handlePush` is the thin `Context`-side dispatch over it. The envelope is
not hardcoded: `SautiPushKeys` (defaults matching the calls backend wire — `event_name` + `payload` JSON) is
configurable per call or once via `Config.pushKeys`, so an adopter with a different transport remaps keys
instead of forking. `presentIncomingCall` / `cancelIncomingCall` / `outgoingDeclined` stay public as the
escape hatch for custom presentation. This is the first of the "push the protocol logic into the library"
adopter-simplification moves; token-mint and lifecycle-settle HTTP are the larger follow-ups.

## Wave A adopter-simplification: callState, token provider, permission helper (0.1.22)

A four-lens panel (library engineer, QA, adversarial, PM) reviewed five proposed adopter conveniences before any
code. Two were dropped on the panel's evidence and three shipped with the panel's must-fixes; the unifying rule
was "extract a pure, injected-clock/source decision helper and JVM-test it," matching the module's existing seam.

`Sauti.callState: StateFlow<SautiCallSummary>` and `Sauti.isCallActive` expose a supported, read-only projection
of the live call (active, connected, opaque peerLabel, durationMs) so an adopter observes call state without
scraping `CallForegroundService` internals — the foundation for an in-app return affordance. Two panel fixes are
load-bearing: `isCallActive` reads `CallForegroundService.call.value` **directly**, not the summary flow, because
a `WhileSubscribed` StateFlow freezes `.value` with no collectors and would report "no call" mid-call; and the
flow emits an explicit inactive, zero-duration summary on a null source so a just-ended call cannot leave a stale
duration. The projection is a pure `summaryFrom` + an injectable `callSummaryFlow(source)`, JVM-tested. `peerLabel`
comes from `others` (never self) and stays an opaque display token, no domain vocabulary.

`SautiTokenProvider` (`fun interface suspend fun mint(request: SautiTokenRequest)`, request `Outgoing | Accept`)
merges the `onStartCall` + `onAccept` mint callbacks into one, added as `Config.tokenProvider` and resolved by a
pure `resolveTicket`. Precedence is authoritative-replace, not fallback: when a provider is set it is the only
path and a null/throown result fails closed to the existing abort — falling back to the legacy callback would
double-mint (double room, double charge). The resolver is wired inside the existing `runCatching` at both real
ticket sites (`SautiOutgoingController` outgoing and `SautiCallActivity.beginAccept`), so the null-abort /
`onOutgoingFailed` / watchdog semantics are byte-for-byte preserved; the accept site is the ticket-minting
`SautiCallHost.Config.onAccept`, not the unrelated `SautiIncomingCallHost` present path. `SautiTokenRequest`
carries full metadata so an adopter's plane/threading branch survives.

`Sauti.missingCallPermissions(context)` returns the ungranted call permissions, sourced from
`config.acceptPermissions` (one source of truth, no second definition) with `POST_NOTIFICATIONS` gated behind
API 33 via the pure `SautiCallPermissions.required/missing`. Only the pure helper shipped; the ergonomic launcher
was deferred because `registerForActivityResult` from a process-global object throws once past `STARTED`.

Two capabilities were **not** shipped. Ring idempotency inside `handlePush` was cut: `SautiIncomingCallRegistry`
already dedups (atomic `active.add`) and already tombstones the cancel-before-ring race, and `presentIncomingCall`
routes through it — a parallel TTL registry that "evicts on cancel" would re-open the very ghost-ring the existing
registry prevents. A Robolectric test now pins that `handlePush` inherits that registry's dedup and tombstone.
`SautiColors.from(ColorScheme)` was deferred: Material3 carries no positive/quality/callBar semantics so
derivation would invent wrong, potentially low-contrast tones on the call screen, and `material3` is an
`implementation` dependency so the signature would leak an ABI type — it needs a design/accessibility decision on
which colors stay library-locked plus an `api` promotion, not a silent DX helper.
