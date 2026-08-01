# io.sauti.ui.compose.overlay

An opt-in system overlay that floats a compact call bubble over other apps. When an
adopter wires it, an active call shows a small circular handle on top of whatever the
user has in front. The handle is draggable and snaps to the nearest screen edge; a
tap expands it to a row of controls (return, mute, end) and a second tap collapses it
back. It re-clamps to the nearest edge on rotation so it cannot strand off-screen.
This is the over-other-apps surface. It is distinct from the Wave 5 in-app
`SautiMinimizedCall`, which only renders inside the adopter's own composition while
the app is foreground.

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
stop.
