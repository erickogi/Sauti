# io.sauti.ui.compose.overlay

An opt-in system overlay that floats the minimized call pill over other apps. When
an adopter wires it, an active call shows a small draggable-free bubble on top of
whatever the user has in front, and a tap returns them to the call. This is the
over-other-apps surface. It is distinct from the Wave 5 in-app `SautiMinimizedCall`,
which only renders inside the adopter's own composition while the app is foreground.

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
`TYPE_APPLICATION_OVERLAY` window rendering `SautiMinimizedCall`, wires the view-tree
lifecycle, saved-state, and view-model owners, and tears them down on removal so no
window or owner leaks. The overlay reuses the same pill and the same
`CallForegroundService` as the rest of the library. There is no second service and
no forked pill. `WindowManagerOverlayHost` requires API 26; on lower levels the
adopter keeps the in-app pill only.

The controller takes a `BubbleOverlayHost` so the show and hide drive logic can be
exercised on the JVM with a recording fake. Adopters that need a different surface
can supply their own host.

## What is not unit-proven here

The following depend on the device window and compose runtime and are covered by a
standing manual device demo, not by unit tests:

- the real render of the pill over other apps
- the `ComposeView`-in-overlay view-tree lifecycle and teardown
- the `canDrawOverlays` grant and revoke settings flow
- tap-to-return from the overlay
- the overlay and the foreground service running at the same time
- removal of the overlay when the permission is revoked while it is shown

The unit tests cover the controller drive logic against a fake host, permission, and
foreground probe: show only when opted in, permitted, active, and backgrounded; hide
on revoke, on foreground, and on call end; no window when not opted in; and hide on
stop.
