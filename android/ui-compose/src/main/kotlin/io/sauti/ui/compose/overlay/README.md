# io.sauti.ui.compose.overlay

This package holds the "get back to a live call" surfaces plus the permission helper
they need. Three things live here, and an adopter can wire any subset:

1. **The over-other-apps bubble** (`installSautiCallBubble` /
   `WindowManagerOverlayHost`) — a compact call bubble that floats above **other apps**
   while the call runs in the background. Needs `SYSTEM_ALERT_WINDOW`.
2. **The in-app return bar** (`installSautiCallBar` / `SautiCallBar`) — a themed bar
   pinned to the top of the **adopter's own** screens while a call is live and the call
   screen is not in front. Needs no permission.
3. **The overlay-permission prompt** (`OverlayPromptDecision`) — the point-of-need
   explainer that asks for `SYSTEM_ALERT_WINDOW` the first time the user minimizes a
   call, rather than mid-dial. Driven from `SautiCallActivity`.

The bubble and the bar are complementary: the bar covers "call is live, user is on
another screen of my app"; the bubble covers "call is live, user left my app entirely".
Both are distinct from the in-app `SautiMinimizedCall`, which only renders inside the
adopter's own composition while the call screen itself is foreground.

## The over-other-apps bubble

When an adopter wires it, an active call shows a small circular handle on top of
whatever the user has in front. The handle is draggable and snaps to the nearest screen
edge; a tap expands it to a row of controls (return, mute, end) and a second tap
collapses it back. It re-clamps to the nearest edge on rotation so it cannot strand
off-screen.

This package is off by default. The default `SautiClient` constructor never builds a
`SautiCallBubble`. `optedIn` defaults to nothing being shown: the reducer yields
`Hidden` unless the adopter passes `optedIn = true`, so `start()` adds no window and
holds no overlay resource until every gate is met.

## What the adopter declares

The base library manifest already declares `FOREGROUND_SERVICE` and
`FOREGROUND_SERVICE_MICROPHONE` for the ongoing-call service. Do not re-declare
them. The only entry the adopter adds, and only when it opts in, is the overlay
permission:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

`SYSTEM_ALERT_WINDOW` is not in any base manifest, because a manifest merge would
change every adopter's install-time permission set. It is the adopter's choice.

## The permission flow the adopter triggers

`SYSTEM_ALERT_WINDOW` is a special-access permission. It is not granted by the
manifest entry alone; the user grants it from a settings screen. The adopter checks
`Settings.canDrawOverlays` through `DefaultOverlayPermission.granted()` and, when it
returns false, sends the user to the grant screen with `manageOverlayIntent`, which
builds an `ACTION_MANAGE_OVERLAY_PERMISSION` intent scoped to the adopter's package.

```kotlin
val permission = DefaultOverlayPermission(context)
if (!permission.granted()) {
    context.startActivity(manageOverlayIntent(context))
}
```

The controller re-checks `permission.granted()` on every emission, so a grant or a
revoke that happens while a call is live is picked up on the next state change: a
revoke removes the overlay, a grant lets it appear again.

## Wiring

```kotlin
val bubble = SautiCallBubble(
    context = context,
    uiStateFlow = uiStateFlow,
    optedIn = true,
    onReturn = { openCallScreen() }
)

bubble.start()
```

`start()` collects `uiStateFlow` and, on each emission, recomputes `BubbleReducer`
from call activity, the app foreground signal, the live permission check, and
`optedIn`. When the reducer yields `Shown` it hands the state to a
`BubbleOverlayHost`; otherwise it removes the overlay. `stop()` cancels the
collection and removes the overlay.

The default host is `WindowManagerOverlayHost`, which adds a `ComposeView` in a
`TYPE_APPLICATION_OVERLAY` window rendering `SautiCallBubbleContent`, wires the
view-tree lifecycle, saved-state, and view-model owners, and tears them down on
removal so no window or owner leaks. It owns the drag gesture (move, tap-versus-drag
by touch slop, snap to the nearest edge) and a `ComponentCallbacks` that re-clamps on
configuration change. Position and expansion persist across state re-emissions within
a call and reset when the bubble is hidden. The overlay reuses the same
`CallForegroundService` as the rest of the library; there is no second service.
`WindowManagerOverlayHost` requires API 26; on lower levels the adopter keeps the
in-app pill only.

The host takes a `SautiBubbleTheme` and a `BubbleConfig` so an adopter can reskin the
bubble (colors flow from `SautiTheme`) and tune the collapsed size, edge margin, and
initial edge without touching library code. Because `SautiBubbleTheme` is a
`@Composable` lambda, the adopter module that passes one must apply the Compose
compiler plugin, otherwise the theme argument is compiled as a non-composable type and
the call fails to link at runtime.

The collapsed handle uses an opaque fill, an adaptive edge ring, and a drop shadow so
it separates from any wallpaper in both themes; the glyph and ring tones are chosen
from the fill and surface luminance by the pure `BubbleContrast` helper rather than a
fixed color.

The controller and geometry are testable off-device: the drag math, tap detection,
expansion transitions, and contrast tone selection live in pure helpers
(`BubbleGeometry`, `BubbleGesture`, `BubbleExpansion`, `BubbleContrast`) with JVM unit
tests, and the controller takes a `BubbleOverlayHost`
so the show and hide drive logic can be exercised with a recording fake. Adopters that
need a different surface can supply their own host.

## The in-app return bar

`installSautiCallBar(application, onReturn, optedIn = true, theme, content = null)`
registers `Application.ActivityLifecycleCallbacks` and, while a call is live, attaches a
`ComposeView` to the top of the resumed activity's `android.R.id.content`. It shows the
peer name and a live, server-anchored timer, and taps through to `onReturn` (wire this
to `Sauti.resumeIntent`).

Visibility is the pure `CallBarReducer.visible(optedIn, callActive, onCallScreen)`:
shown only when the adopter opted in, a call is active, and the current activity is
**not** `SautiCallActivity` (no bar on top of the call screen itself). The installer
re-syncs on every activity resume/pause/destroy and on every `CallForegroundService.call`
emission, detaching the view when the reducer turns false and re-attaching to whichever
activity is now in front, so the bar never leaks across activities.

The bar renders inside the adopter's `SautiBubbleTheme`. With `content = null` it uses
the built-in `SautiCallBar`, which draws on `SautiColors.callBar` / `onCallBar` (both
default to the positive tone; override them when the app's own chrome is already that
color so the bar does not go color-on-color) and elevates with a shadow. An adopter that
wants a different bar passes a `SautiCallBarContent` lambda
`(peerName, durationMs, onReturn) -> Unit` and draws its own. `optedIn = false` attaches
nothing, so the bar ships dark behind a flag. It needs no permission and no manifest
entry.

## The overlay-permission prompt

`SYSTEM_ALERT_WINDOW` is a special-access permission, so pushing the user to the grant
screen at the wrong moment (mid-dial) reads as hostile. Instead `SautiCallActivity`
requests it **at the point of need**: the first time the user minimizes an active call.
The pure `OverlayPromptDecision.shouldPrompt(optedIn, granted, dismissed)` gates it —
prompt only when the adopter opted into the bubble, the permission is not already
granted, and the user has not already dismissed the prompt this call.

On a minimize that clears the gate, the host shows a short explainer and, on confirm,
launches the grant intent through a registered `ActivityResultLauncher`; on dismiss it
sets the per-call dismissed flag so it does not nag again. The prompt's shown/dismissed
state is persisted across configuration change and process death via
`onSaveInstanceState` and reset when a fresh incoming call rebinds. The copy comes from
`SautiStrings.overlayPrompt*` (generic by default), and an adopter can replace the whole
dialog by passing `SautiCallHost.Config.overlayPromptContent`, a
`@Composable (onConfirm, onDismiss) -> Unit` slot — the host still owns when to show it
and still wires the launcher and the dismissed flag, so an adopter dialog cannot break
the flow.

## What is not unit-proven here

The following depend on the device window and compose runtime and are covered by a
standing manual device demo, not by unit tests:

- the real render of the bubble over other apps
- the `ComposeView`-in-overlay view-tree lifecycle and teardown
- the `canDrawOverlays` grant and revoke settings flow
- tap-to-expand, the expanded controls, and tap-to-collapse from the overlay
- the drag gesture, snap-to-edge, and re-clamp on rotation
- the overlay and the foreground service running at the same time
- removal of the overlay when the permission is revoked while it is shown

The unit tests cover the controller drive logic against a fake host, permission, and
foreground probe: show only when opted in, permitted, active, and backgrounded; hide
on revoke, on foreground, and on call end; no window when not opted in; and hide on
stop. The pure decision helpers behind the bar and the prompt each carry their own JVM
tests: `CallBarReducer.visible` (bar shows only when opted in, active, and off the call
screen), `OverlayPromptDecision.shouldPrompt` (prompt only when opted in, not granted,
not dismissed), and `IncomingSlideDecision.accepts` (the slide-to-answer 60% threshold).
The device-dependent halves — the bar's real `ComposeView` attach/detach across
activities, and the settings grant flow — are exercised by the same standing manual
demo.
