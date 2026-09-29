# Sauti Android adopter guide

How to add one-to-one (or small-group) voice calling to an Android app with the
ready-made Sauti call host. You configure the host once, hand it a way to mint tokens,
and start calls with a single line — the library draws the outgoing, incoming, and
in-call screens, runs the microphone foreground service, and offers the return-to-call
bubble and bar. No call UI to build.

This guide covers the **batteries-included host** (`io.sauti:ui-compose`). If you want to
render your own call UI instead, skip to [Building your own UI](#12-building-your-own-ui-instead)
and the low-level `SautiClient` surface in [`INTEGRATION.md` §4](./INTEGRATION.md).

Current published version: **`0.1.25`**.

## Contents

1. [What you get](#1-what-you-get)
2. [Install](#2-install)
3. [Manifest](#3-manifest)
4. [Configure the host once](#4-configure-the-host-once)
5. [What your backend must return: `SautiSessionTicket`](#5-what-your-backend-must-return-sautisessionticket)
6. [Make an outgoing call](#6-make-an-outgoing-call)
7. [Receive an incoming call](#7-receive-an-incoming-call)
8. [Slide-to-answer](#8-slide-to-answer)
9. [Return-to-call: the in-app bar and the overlay bubble](#9-return-to-call-the-in-app-bar-and-the-overlay-bubble)
10. [Ending a call and the lifecycle callbacks](#10-ending-a-call-and-the-lifecycle-callbacks)
11. [Theming](#11-theming)
12. [Building your own UI instead](#12-building-your-own-ui-instead)
13. [Full worked example](#13-full-worked-example)
14. [Versioning](#14-versioning)
15. [Adoption checklist](#15-adoption-checklist)

---

## 1. What you get

Wire the host and an active call gives your users, for free:

- A full-screen **outgoing** (dialing) screen, a full-screen **incoming** screen with
  **slide-to-answer**, a **connecting** state, and the **in-call** screen with
  mute/hold, audio-route chooser (earpiece / speaker / Bluetooth / wired), a
  server-anchored duration timer, and live quality.
- The **microphone foreground service** and its ongoing notification, started and
  stopped for you, so a call survives backgrounding.
- **Resume after process death** — if the process is killed mid-call, the host can
  reclaim the same call.
- Two opt-in **return-to-call** surfaces: an in-app bar pinned to your own screens, and
  a draggable bubble that floats over other apps.
- Telephony handling — a cellular call auto-mutes and restores the Sauti call around it.
- Full **theming** of every surface (colors, copy, type) and localization, with no host
  code to touch.

You supply three things: a **manifest entry**, a **`Config`** (mostly a set of suspend
callbacks that mint tokens and settle backend state), and the **push handling** that
turns your wake-push into an incoming call.

## 2. Install

```kotlin
// build.gradle.kts (app module)
repositories {
    mavenLocal()          // during library development
    maven("https://jitpack.io")
}

dependencies {
    implementation("io.sauti:android:0.1.25")     // the call engine + Android runtime
    implementation("io.sauti:ui-compose:0.1.25")  // the ready-made host + UI
    // implementation("io.sauti:rx2:0.1.25")       // only if you are not on coroutines
}
```

Any module that consumes `ui-compose` — even one that only calls an installer — must
apply the Compose compiler and enable Compose, because the theme lambdas are
`@Composable`:

```kotlin
plugins { id("org.jetbrains.kotlin.plugin.compose") }
android { buildFeatures { compose = true } }
```

The library targets `minSdk 21`; every API 31+ path has a working legacy fallback.

## 3. Manifest

Register the host Activity (the library does not, so you own its theme and task
affinity):

```xml
<activity
    android:name="io.sauti.ui.compose.SautiCallActivity"
    android:exported="false"
    android:launchMode="singleTask"
    android:showWhenLocked="true"
    android:turnScreenOn="true"
    android:theme="@style/Theme.YourApp.Call" />
```

The `:android` library manifest already declares and merges the call permissions —
`INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`,
`READ_PHONE_STATE`, Bluetooth, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MICROPHONE` — and the microphone foreground service. You do **not**
re-declare those.

`RECORD_AUDIO` and, on Android 13+, `POST_NOTIFICATIONS` are runtime permissions —
request them before a call (the host requests `acceptPermissions` on accept for you; you
still request them for the outgoing path). `Sauti.missingCallPermissions(context)` returns
exactly which are still ungranted — sourced from `Config.acceptPermissions` (one source of
truth) with `POST_NOTIFICATIONS` included only on API 33+ — so you request the right set
without SDK-gating by hand:

```kotlin
val missing = Sauti.missingCallPermissions(this)
if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
```

The only manifest entry you add yourself, and
only if you opt into the over-other-apps bubble, is:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

## 4. Configure the host once

Call `Sauti.configure(...)` once, at process start (typically `Application.onCreate`).
`Config` is the whole contract: the host asks you for tokens and for
accept/decline/cancel/end decisions through suspend callbacks, and you give it your theme
and copy.

```kotlin
Sauti.configure(
    SautiCallHost.Config(
        // Outgoing: turn a target + metadata into a joinable ticket, or null to abort.
        onStartCall = { request -> backend.mintOutgoing(request.target, request.metadata) },

        // Incoming: accept -> return a ticket to join; decline -> tell your backend.
        onAccept = { incoming -> backend.mintForIncoming(incoming) },
        onDecline = { incoming -> backend.decline(incoming.callId) },

        // Lifecycle hangups so your backend can settle call state.
        onCancelCall = { callId -> backend.cancel(callId) },   // caller aborts a ringing call
        onEndCall    = { callId -> backend.end(callId) },      // either side ends a connected call

        // Look and copy.
        colors  = appSautiColors(),
        strings = appSautiStrings(),

        // Behaviour.
        incomingRequiresSlide = true,
        onOutgoingFailed = { toast("Could not start the call") }
    )
)
```

The full surface:

| Field | Type | Default | Purpose |
|---|---|---|---|
| `onAccept` | `suspend (SautiIncomingCall) -> SautiSessionTicket?` | **required** | Callee accepted; return a ticket to join, `null` to abort. |
| `onDecline` | `suspend (SautiIncomingCall) -> Unit` | **required** | Callee declined; tell your backend. |
| `onBusyDecline` | `(suspend (SautiIncomingCall) -> Unit)?` | `null` | A second call arriving while one is already live is auto-declined; hook to tell your backend. |
| `incomingRingTimeoutMs` | `Long` | `45_000` | Ring timeout before an unanswered incoming call is treated as missed. |
| `onMissedCall` | `suspend (SautiIncomingCall) -> Unit` | `{}` | Incoming call was never answered within `incomingRingTimeoutMs`. |
| `onStartCall` | `suspend (SautiOutgoingRequest) -> SautiSessionTicket?` | `{ null }` | Caller dialed via `Sauti.startCall`; mint the ticket, `null` to abort. |
| `onCancelCall` | `suspend (callId) -> Unit` | `{}` | Caller cancelled a still-**ringing** outgoing call. |
| `onEndCall` | `suspend (callId) -> Unit` | `{}` | Either side ended a **connected** call. |
| `callerNameResolver` | `(SautiIncomingCall) -> String` | `::defaultCallerName` | Display name (falls back to metadata `name`, then a short id). |
| `colors` / `typography` / `strings` | `SautiColors?` / `SautiTypography?` / `SautiStrings` | library defaults | Theme + copy for every surface. |
| `acceptPermissions` | `List<String>` | `[RECORD_AUDIO]` | Runtime permissions requested on accept. |
| `onAcceptPermissionDenied` | `() -> Unit` | `{}` | Accept blocked because a permission was denied. |
| `onAfterAccept` | `(SautiIncomingCall) -> Unit` | `{}` | Hook after a successful accept. |
| `ringOverrides` | `RingOverrides` | defaults | Ringtone customization. |
| `notificationOverrides` | `IncomingCallOverrides` | defaults | Incoming-notification icon/text branding. |
| `autoMuteOnCellularCall` | `Boolean` | `false` | Auto-mute the Sauti call during a GSM interruption. |
| `sessionDefaults` | `SautiSessionDefaults` | defaults | Proximity, audio processing, ICE-restart debounce, QoE sink. |
| `outgoingNoAnswerTimeoutMs` | `Long` | `35_000` | Ring timeout before an unanswered outgoing call gives up. |
| `onOutgoingFailed` | `() -> Unit` | `{}` | Outgoing call could not start (ticket mint returned null or threw). |
| `overlayPromptContent` | `(@Composable (onConfirm, onDismiss) -> Unit)?` | `null` | Your own overlay-permission dialog; `null` uses the built-in one. |
| `incomingRequiresSlide` | `Boolean` | `true` | Incoming screen: slide-to-answer (`true`) or two tap buttons (`false`). |
| `pushKeys` | `SautiPushKeys` | wire defaults | Envelope keys/event names `Sauti.handlePush` reads (see §7). |
| `tokenProvider` | `SautiTokenProvider?` | `null` | One `mint(request)` in place of `onStartCall` + `onAccept` (see §5). |

## 5. What your backend must return: `SautiSessionTicket`

Every mint callback returns a `SautiSessionTicket` — the joinable credentials your
backend produced from the signaling server's token endpoint — or `null` to abort:

```kotlin
data class SautiSessionTicket(
    val url: String,            // wss:// signaling URL
    val token: String,          // host token minted by your backend
    val roomId: String,
    val participantId: String,
    val displayTitle: String,   // shown on the ongoing-call notification
    val callId: String = "",    // YOUR backend's call id; flows back to onCancelCall / onEndCall
    val endWhenLastPeerLeaves: Boolean = true,
    val initialDevice: AudioDevice = AudioDevice.EARPIECE
)
```

Set `callId` to your backend's identifier for the call. The host threads it back into
`onCancelCall` and `onEndCall`, which is how your backend settles state when the user
hangs up. `url`, `token`, `roomId`, `participantId` come from the same token-mint
endpoint the rest of Sauti uses; the host never sees your user identity or your API.

### One provider instead of two mint callbacks (optional)

`onStartCall` and `onAccept` usually do the same thing — hit your backend and return a
ticket. `Config.tokenProvider` collapses them into one:

```kotlin
Sauti.configure(
    SautiCallHost.Config(
        onDecline = { incoming -> backend.decline(incoming.callId) },
        tokenProvider = SautiTokenProvider { request ->
            when (request) {
                is SautiTokenRequest.Outgoing -> backend.mintOutgoing(request.target, request.metadata)
                is SautiTokenRequest.Accept   -> backend.mintForIncoming(request.call)
            }
        }
    )
)
```

`request.metadata` is available on both variants (so a `plane`/thread branch survives),
and `SautiTokenRequest.Accept` carries the full `SautiIncomingCall`. When `tokenProvider`
is set it is **authoritative** — the host never also calls `onStartCall`/`onAccept`, and a
`null`/thrown result aborts the call exactly as a `null` from those callbacks does (so no
double-mint). Leave `tokenProvider` unset to keep using the two callbacks.

## 6. Make an outgoing call

One line — the host fires `onStartCall`, opens `SautiCallActivity`, and dials:

```kotlin
Sauti.startCall(
    context,
    target   = calleeId,                       // opaque; only your onStartCall interprets it
    metadata = mapOf("name" to calleeName)     // your opaque bag; "name" is echoed onto the UI
)
```

`metadata` is passed through to `onStartCall` untouched (except that the host reads
`name` for display). Use it to carry whatever your token mint needs — a trip id, a
thread id, a role. If `onStartCall` returns `null` or throws, the host calls
`onOutgoingFailed` and shows nothing.

## 7. Receive an incoming call

Your app owns push delivery; the library owns what a call push **means**. Forward every
Sauti call push to `Sauti.handlePush` — it recognizes the call events, builds the
`SautiIncomingCall`, and dispatches internally (ring → present, cancelled → tear down,
declined → notify the caller). It returns `true` if it consumed the message, so your own
pushes fall through:

```kotlin
class AppMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (Sauti.handlePush(this, message.data)) return   // Sauti consumed a call push
        // ...your app's own (non-call) pushes...
    }

    override fun onNewToken(token: String) { backend.registerPushToken(token) }
}
```

That is the whole incoming integration. When Sauti adds a new call lifecycle event, it is
handled inside a library bump — **no app change**.

**The envelope.** By default `handlePush` reads the wire the calls backend emits: an
`event_name` of `voip-call-incoming` / `voip-call-cancelled` / `voip-call-declined`, and a
`payload` JSON string carrying `callId`, `roomId`, `callerName`, and any extra keys (which
flow through as `SautiIncomingCall.metadata`). If your push uses different keys or event
names, override them once — `handlePush` picks them up:

```kotlin
Sauti.configure(
    SautiCallHost.Config(
        /* ...callbacks, theme... */
        pushKeys = SautiPushKeys(eventKey = "type", payloadKey = "body")
    )
)
```

Or pass a `SautiPushKeys` to the three-arg `handlePush(context, data, keys)` overload.

**Escape hatch.** If you need to drive presentation yourself, the primitives
`handlePush` calls remain public: `Sauti.presentIncomingCall(context, call)` (raises the
full-screen incoming UI, or a heads-up notification when an Activity can't launch from the
background), `Sauti.cancelIncomingCall(context, callId)`, and
`Sauti.outgoingDeclined(context, callId)`.

### Building the marker yourself: `SautiPushBuilder`

`SautiPushParser` reads a call marker; `SautiPushBuilder` writes one. They are symmetric
inverses over the same `SautiPushKeys` envelope, so if you deliver call invites over a
channel you already run (your own socket, an in-app message topic) rather than FCM, build
the marker on the caller and parse it on the callee:

```kotlin
// Caller: build the marker and send it over your own channel.
val marker = SautiPushBuilder.encode(
    SautiPushCommand.Incoming(
        SautiIncomingCall(
            callId = callId,
            roomId = roomId,
            callerName = callerName,
            metadata = linkedMapOf("yourField" to value)   // opaque adopter fields
        )
    )
)
yourChannel.send(marker)

// Callee: parse it back and present.
val command = SautiPushParser.parse(marker)
Sauti.presentIncomingCall(context, command.call)
```

`encode` produces the same `event_name` / `payload` envelope `handlePush` expects, so a
marker built this way is interchangeable with an FCM call push. Anything you put in
`metadata` travels through `SautiIncomingCall.metadata` untouched; the library never reads
or names adopter-specific fields, so a trip id, a thread id, or a caller account id ride as
opaque metadata and reach your `onAccept` and your callee code unchanged. Pass a
non-default `SautiPushKeys` to both `encode` and `parse` if your envelope keys differ.

## 8. Slide-to-answer

With `incomingRequiresSlide = true` (the default) the incoming screen presents a
horizontal **slide-to-answer** track: the callee drags a handle past 60% of the track to
connect, with **decline as a single tap** below. The asymmetry is deliberate — an
accidental answer opens the mic and starts billing, so accept is made deliberate while
decline stays instant. It gives a haptic on completion and springs back if released
short. TalkBack and Switch Access users get a tappable "Answer" action on the track, so
accessibility is not gated behind the drag.

Set `incomingRequiresSlide = false` for the classic two-button (decline / accept) layout.
`onAccept` / `onDecline` are identical either way — this is a pure presentation choice.

## 9. Return-to-call: the in-app bar and the overlay bubble

Two optional, opt-in surfaces let a user get back to a live call after navigating away.
Install either or both once, after `Sauti.configure`. Both no-op when `optedIn = false`,
so you can ship them dark behind a flag.

```kotlin
// A) In-app return bar — a themed bar pinned to the top of YOUR screens while a call is
//    live and the call screen is not in front. No permission, shows immediately.
installSautiCallBar(
    application = this,
    onReturn = { startActivity(Sauti.resumeIntent(this)) },
    optedIn  = true,
    theme    = { content -> AppSautiBubbleTheme(content) },
    content  = null   // null = built-in SautiCallBar; or pass your own (peerName, durationMs, onReturn)
)

// B) Over-other-apps bubble — a draggable, edge-snapping bubble that floats above OTHER
//    apps while the call runs in the background. Needs SYSTEM_ALERT_WINDOW.
installSautiCallBubble(
    application = this,
    onReturn = { startActivity(Sauti.resumeIntent(this)) },
    optedIn  = true,
    theme    = { content -> AppSautiBubbleTheme(content) }
)
```

They are complementary: the **bar** covers "call live, user on another of my screens";
the **bubble** covers "call live, user left my app". `Sauti.resumeIntent(context)` returns
an Intent that brings the current call back to the foreground — wire both `onReturn`s to
it.

The bubble needs the special-access `SYSTEM_ALERT_WINDOW` permission. Rather than eject
the user to settings mid-dial, the host asks **at the point of need**: the first time the
user minimizes an active call, it shows a short explainer and only then sends them to
grant. Decline once and it will not nag again for that call. Override the explainer copy
with `SautiStrings.overlayPrompt*`, or replace the whole dialog with
`Config.overlayPromptContent`. See
[`overlay/README.md`](../android/ui-compose/src/main/kotlin/io/sauti/ui/compose/overlay/README.md)
for the internals.

### Observing call state yourself (`Sauti.callState`)

If you want your **own** in-app affordance — a "tap to return to your call" chip on a
specific screen, or hiding a "call" button while a call is already live — observe the host
instead of scraping internals:

```kotlin
val summary by Sauti.callState.collectAsState()   // StateFlow<SautiCallSummary>
if (summary.active) MyReturnChip(summary.peerLabel, summary.durationMs) { returnToCall() }

// Or a one-shot synchronous check (correct even with no collectors):
if (Sauti.isCallActive) { /* a call is running */ }
```

`SautiCallSummary` is read-only — `active`, `connected`, `peerLabel` (an opaque display
label, never your domain id), and `durationMs`. `isCallActive` reads the live call state
directly, so it is reliable as a synchronous guard; `callState` emits an inactive,
zero-duration summary the moment a call ends. Use the built-in bar/bubble above for the
default experience; reach for `callState` only when you want a bespoke surface.

## 10. Ending a call and the lifecycle callbacks

There are two distinct hangup callbacks because the backend transitions differ:

- **`onCancelCall(callId)`** — the caller aborts an outgoing call that is still
  **ringing** (never connected).
- **`onEndCall(callId)`** — either side ends a call that has already **connected**. The
  host fires this when the user taps end on a live call, and the outgoing controller fires
  it when the media session drops after the peer had joined.

Wiring `onEndCall` matters: without it, a call that connected and then ended leaves your
backend thinking it is still active, which can block the next call to the same
counterparty. Point it at your backend's end/teardown endpoint. (The `callId` is the one
you put on the ticket in step 5.)

## 11. Theming

Every surface is themed through three data classes on `Config` — no host code to touch.

**`SautiColors`** — surfaces, content, accent, danger, positive, the control idle/active
tones, and, for the return bar, `callBar` / `onCallBar`:

```kotlin
fun appSautiColors() = SautiColors(
    surface = ..., onSurface = ..., onSurfaceMuted = ..., accent = ...,
    danger = ..., onDanger = ...,
    qualityGood = ..., qualityFair = ..., qualityPoor = ...,
    // callBar / onCallBar default to the positive tone. Override them if your own app
    // chrome is already that color, so the bar does not render color-on-color:
    callBar = colorResource(R.color.brandBar),
    onCallBar = colorResource(R.color.white)
)
```

**`SautiStrings`** — every user-facing string, so you localize or rebrand without forking.
Adopter-relevant entries include `slideToAnswer`, `callBarReturn`, and the overlay prompt
copy `overlayPromptTitle` / `overlayPromptBody` / `overlayPromptConfirm` /
`overlayPromptDismiss`. The library defaults are deliberately generic ("Keep this call
handy", "Slide to answer") — pass your product's voice.

**`SautiTypography`** — an optional type scale.

The bar and bubble take a `SautiBubbleTheme` (a `@Composable (@Composable () -> Unit) ->
Unit` wrapper) so they render inside your Material theme; colors still flow from
`SautiColors`.

## 12. Building your own UI instead

The host is optional. If you want your own call screens, depend on `io.sauti:android`
only and drive `SautiClient` directly — `join` / `leave` / `setMuted` / `setHold` /
`selectDevice`, and render its `state: StateFlow<CallState>`, `currentDevice`,
`availableDevices`, and `events`. The host is built on exactly this surface. It is fully
documented in [`INTEGRATION.md` §4 "The client surface"](./INTEGRATION.md).

## 13. Full worked example

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Sauti.configure(
            SautiCallHost.Config(
                onStartCall = { req -> tickets.mintOutgoing(req.target, req.metadata) },
                onAccept    = { call -> tickets.mintForIncoming(call) },
                onDecline   = { call -> calls.decline(call.callId) },
                onCancelCall = { id -> calls.cancel(id) },
                onEndCall    = { id -> calls.end(id) },
                colors  = appSautiColors(),
                strings = appSautiStrings(),
                incomingRequiresSlide = true,
                onOutgoingFailed = { mainHandler.post { toast("Could not start the call") } }
            )
        )

        val bubbleTheme: SautiBubbleTheme = { content -> AppSautiBubbleTheme(content) }
        installSautiCallBar(this, onReturn = ::returnToCall, optedIn = true, theme = bubbleTheme)
        installSautiCallBubble(this, onReturn = ::returnToCall, optedIn = true, theme = bubbleTheme)
    }

    private fun returnToCall() = startActivity(
        Sauti.resumeIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

class AppMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (Sauti.handlePush(this, message.data)) return
        // ...your app's own pushes...
    }

    override fun onNewToken(token: String) { backend.registerPushToken(token) }
}

// Start a call from anywhere in the app:
fun call(context: Context, calleeId: String, calleeName: String) {
    Sauti.startCall(context, calleeId, mapOf("name" to calleeName))
}
```

Resume after process death is automatic: the host persists the live call and reclaims it
on relaunch while the token is still within its server grace window. You do not call any
resume API for the host path — `Sauti.resumeIntent` only brings an already-live call
back to the foreground.

## 14. Versioning

The four Android artifacts version together — keep `engine`, `android`, `rx2`, and
`ui-compose` on the **same** version. During library development they resolve from
`mavenLocal()` after a `./gradlew publishToMavenLocal`; releases go through JitPack. The
current published version is **`0.1.25`**.

### What changed in 0.1.25

Behaviour, mostly around the incoming and ringback paths. Nothing here is a breaking
change; the new `Config` fields all default to the previous behaviour.

- Outgoing ringback now stops as soon as the callee joins the room. It waits for a
  resolved self participant id before treating anyone else in the room as the remote
  party, so it no longer stops early or rings on after connect.
- A second incoming call that arrives while a call is already live is auto-declined
  instead of replacing the live call. Wire `onBusyDecline` to tell your backend.
- An unanswered incoming call is treated as missed after `incomingRingTimeoutMs`
  (default 45s). `onMissedCall` fires when that happens. The timeout is cancelled when
  the call is accepted, including when acceptance comes in through the notification
  intent or after the app resumes.
- If the current audio output device is removed mid-call (a headset unplugged, for
  example), routing falls back to the earpiece instead of holding the now-absent device.

## 15. Adoption checklist

- [ ] `io.sauti:android` + `io.sauti:ui-compose` at the same version; Compose compiler
      applied in the consuming module.
- [ ] `SautiCallActivity` declared in the manifest with your call theme.
- [ ] `Sauti.configure(Config)` called once in `Application.onCreate`.
- [ ] `onStartCall` / `onAccept` / `onDecline` return real tickets / settle state; the
      ticket carries your backend `callId`.
- [ ] `onCancelCall` **and** `onEndCall` wired — the connected-call end is the one most
      often missed.
- [ ] Push handler forwards to `Sauti.handlePush(context, data)` (override `pushKeys` in
      `Config` if your envelope differs from the default).
- [ ] `RECORD_AUDIO` (and `POST_NOTIFICATIONS` on 13+) requested before the outgoing path.
- [ ] If using the bubble: `SYSTEM_ALERT_WINDOW` in the manifest; `installSautiCallBubble`
      opted in.
- [ ] `installSautiCallBar` opted in for the in-app return path.
- [ ] `SautiColors` (including `callBar` if your chrome clashes) and `SautiStrings`
      themed to your brand and locale.
