# Accessible audio-output selection — Sauti in-call UI

Spec for making speaker / earpiece / Bluetooth reachable in the in-call control row,
module `android/ui-compose`. Reviewed through a design panel (APPROVE-WITH-CHANGES) and
an adversarial panel (PROCEED-WITH-CAVEATS). Pure presentation; routing is unchanged.

## Problem

The audio control is a single icon that opens a `DropdownMenu` of text items
(`SautiAudioButton.kt`, `AudioDevicePicker.kt`). It is undiscoverable: no label, no
affordance that it is a chooser, the glyph only shows the current route, speaker takes two
taps, and a dropdown is the wrong surface for an in-call, in-motion screen. Apple and Google
both converge on: a one-tap Speaker toggle when only phone routes exist, escalating to a
labelled route picker (list, active route checked) when an external route is present.

## Target behaviour

Escalation keyed on the live `availableDevices` set only (never on `currentDevice`):

- **No Bluetooth present** → a **one-tap Speaker toggle** (loudspeaker on/off). Matches
  iOS / Google dialer. Wired-headset-only also uses the toggle (a two-row sheet of
  `{Speaker, Wired}` is worse than a toggle; the OS auto-routes to the wired headset and the
  only real choice is loudspeaker vs not).
- **Bluetooth present** → a labelled **route button** (current-route glyph + route label)
  that opens an `AudioRouteSheet` listing every available route, the active one checked.

Escalation predicate (pure, testable):

```kotlin
internal fun showAudioRoutePicker(devices: Set<AudioDevice>): Boolean =
    devices.contains(AudioDevice.BLUETOOTH)
```

Toggle target — defined by the *target's* presence, never by assuming `currentDevice`:

```kotlin
internal fun speakerToggleOn(current: AudioDevice): Boolean =
    current == AudioDevice.SPEAKER

// From speaker, drop to the real non-speaker route (wired if present, else earpiece).
// From any non-speaker route (incl. a stale BLUETOOTH during a transition), go to speaker.
internal fun speakerToggleTarget(current: AudioDevice, devices: Set<AudioDevice>): AudioDevice =
    if (current == AudioDevice.SPEAKER) {
        if (devices.contains(AudioDevice.WIRED_HEADSET)) AudioDevice.WIRED_HEADSET
        else AudioDevice.EARPIECE
    } else {
        AudioDevice.SPEAKER
    }
```

The toggle's `onClick` must verify membership before dispatching:

```kotlin
val target = speakerToggleTarget(current, devices)
if (devices.contains(target)) uiState.selectDevice(target)   // else no-op
```

## Why the guardrails exist (load-bearing facts)

- `selectDevice` is **optimistic**: `AudioSessionCoordinator.selectDevice` sets
  `currentDeviceState.value = device` unconditionally (`AudioSessionCoordinator.kt:89`) even
  when the target type is not in `availableCommunicationDevices` and the route does not move
  (`:149-151`); `TelecomAudioController.selectDevice` only sets the flow with no validation
  (`:37-39`). So calling `selectDevice(X)` for an absent `X` paints the control active while
  audio stays put. The UI must never emit such a call.
- `currentDevice` and `availableDevices` are **separate** StateFlows combined in the UI
  (`SautiCallUiState.kt:140-154`, `:165`). `reconcileRouting` sets `availableDevices` first,
  then `selectDevice` sets `currentDevice` (`AudioSessionCoordinator.kt:209-219`), so there is
  a real transient frame where the set says phone-only while `currentDevice` is still a
  removed BLUETOOTH. Control type must come from the set; the toggle must treat a non-SPEAKER
  `currentDevice` as "not on speaker" → next tap selects SPEAKER.
- Both production controllers seed `{EARPIECE, SPEAKER}` before adding external routes
  (`AudioSessionCoordinator.kt:93`, `TelecomAudioController.kt:48`), so production sets are
  never empty. But `SautiCall` is an interface and tests inject arbitrary sets
  (`SautiCallUiStateTest.kt:76`) — the UI must degrade safely, not rely on the seed.

## Mandatory guardrails (the spec is BLOCK without all of these)

1. Never call `selectDevice(X)` when `X ∉ availableDevices`. Both the toggle and every sheet
   row re-check membership against the current set and no-op otherwise.
2. Toggle target guarded by the target's presence (above); a non-SPEAKER `currentDevice` in a
   phone-only frame resolves to SPEAKER.
3. Control type derives from `availableDevices` only (`showAudioRoutePicker`), recomputed every
   emission — never from `currentDevice`.
4. Auto-dismiss the sheet on state change via `sheetState.hide()` inside a `LaunchedEffect`
   watching `showAudioRoutePicker`; do not yank the sheet out of composition mid-animation.
5. The sheet's row list is read from the live `availableDevices` each recomposition, with the
   checked row keyed to the live `currentDevice`; never cache the list captured at open time.
6. Degenerate set (empty, or missing EARPIECE/SPEAKER): render a disabled / no-op control
   rather than crash or offer an absent route.
7. `@OptIn(ExperimentalMaterial3Api::class)` on the sheet composables — `ModalBottomSheet` and
   `rememberModalBottomSheetState` are experimental at Compose BOM `2024.06.00` (material3 1.2.1)
   and will not compile without it.
8. Theme the sheet explicitly: set `containerColor`, `scrimColor`, and drag-handle/content
   colours from `SautiTheme.colors` — the ambient `MaterialTheme` would paint M3 baseline
   colours over the Sauti-themed call. (Composition locals `SautiTheme`/`LocalSautiStrings`
   propagate into the sheet, so labels are fine; container colours are not.)
9. Host-window requirement: `ModalBottomSheet` needs `ViewTreeLifecycleOwner` +
   `ViewTreeSavedStateRegistryOwner`, which a bare `WindowManager` overlay `ComposeView` lacks
   (today's `DropdownMenu`/`Popup` does not need them). SafeBoda shows the call in a real
   Activity (`SautiCallActivity`), so this is satisfied; document it, and keep the `DropdownMenu`
   as the fallback surface for any overlay host. (The overlay call bubble has its own controls
   and never composes the picker — `SautiCallBubbleContent.kt` — so no sheet is composed in the
   overlay window.)
10. Preserve the tested internal API or migrate its tests in the same change: `audioButtonActive`
    (`SautiAudioButton.kt:21`) and `audioDeviceOrder` (`AudioDevicePicker.kt:25`) are referenced
    by `SautiSoloCallTest`/`SautiCallUiStateTest`. Migrate the solo screen (`SautiAudioButton`)
    and the group screen (`ControlBar` → `AudioDevicePicker`) together so neither regresses.

## Control layout

Both public composables keep their current signatures and share the decision helpers and one
`AudioRouteSheet`.

**Solo / icon context — `SautiAudioButton(uiState, modifier, size = 64.dp)`** (used by
`SautiSoloCallScreen.kt:87`, SafeBoda's 1:1 call). Stays a **circular `SautiIconButton`** in a
row of three 64dp circles — do not turn it into a pill. The text requirement is met by a
caption under the circle (`Column` + `Text`, `SautiTheme.typography.caption`).
- Toggle mode (`!showAudioRoutePicker`): `SpeakerGlyph`; active colours when
  `speakerToggleOn(current)`, else idle; onClick = guarded toggle; caption `strings.speaker`;
  `stateDescription = if (on) strings.speakerOn else strings.speakerOff`.
- Picker mode: current-device glyph (existing `when(device)` block, `SautiAudioButton.kt:43-48`);
  background via `audioButtonActive(current)`; onClick opens the sheet; caption
  `audioDeviceLabel(current, strings)`; `contentDescription = "${strings.audioOutput}, ${label}"`.

**ControlBar / text context — `AudioDevicePicker(uiState, modifier)`** (used by `ControlBar`,
`Controls.kt:105`, in the group `SautiCallScreen`). Stays a `FilledTonalButton`,
`shape = SautiTheme.shapes.control`.
- Toggle mode: `Text(strings.speaker)`; `semantics { stateDescription = on/off }`; guarded toggle.
- Picker mode: `Text(currentLabel)`; keep existing `contentDescription` (`AudioDevicePicker.kt:34`);
  onClick opens the sheet (replacing the inline `DropdownMenu` at `:43`).

The repo's only chevron glyph (`ReturnChevronGlyph`, `overlay/BubbleGlyphs.kt:90`) is a *back*
chevron — do not reuse it as an expand affordance. A down-caret is optional; recommended omitted
to keep scope tight (the caption already signals the control).

## AudioRouteSheet (new internal composable)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioRouteSheet(
    devices: List<AudioDevice>,          // audioDeviceOrder(uiState.availableDevices), live
    current: AudioDevice,                // uiState.currentDevice, live
    onSelect: (AudioDevice) -> Unit,     // membership-checked before selectDevice
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- `ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(),
  containerColor = SautiTheme.colors.surface, scrimColor = …)`.
- Header `Text(strings.audioRouteSheetTitle)`, `SautiTheme.typography.label`.
- One row per device, `heightIn(min = 56.dp)`, `selectable(selected = device == current,
  role = Role.RadioButton, onClick = { onSelect(device); onDismiss() })`: leading device glyph,
  `Text(audioDeviceLabel(device, strings))`, trailing check when `device == current` (decorative,
  `contentDescription = null`). Selected content colour `controlActiveContent`.
- Row tap animates hide (`sheetState.hide()` in a `rememberCoroutineScope`) then `onDismiss`.

## API additions

New internals in `io.sauti.ui.compose`:
- `showAudioRoutePicker(devices)`, `speakerToggleOn(current)`, `speakerToggleTarget(current, devices)`
- `@Composable internal fun AudioRouteSheet(...)`
- reuse `audioDeviceOrder` / `audioDeviceLabel` unchanged.

Owned in-module change: add `stateDescription: String? = null` (and `role = Role.Button`) to
`SautiIconButton` (`IconButton.kt:19`, applied in the `semantics {}` at `:34`) — it has no
state/role hook today, so the solo toggle cannot otherwise announce on/off. Additive,
backward-compatible.

## SautiStrings additions (append, keep all existing fields)

```kotlin
val speakerOn: String = "Speaker on",
val speakerOff: String = "Speaker off",
val audioRouteSheetTitle: String = "Audio output",
```

Reuse existing `audioOutput` for the picker-button contentDescription prefix. No change to
`earpiece/speaker/bluetooth/wiredHeadset`.

## Accessibility

- Solo toggle: `Role.Button` + `stateDescription` on/off (needs the `SautiIconButton` param).
  Target ≥48dp (default 64dp).
- Picker control (both contexts): `contentDescription = "Audio output, <route>"`.
- Sheet rows: `selectable(role = Role.RadioButton, selected = …)` so the active route announces
  as selected; rows ≥56dp; the check icon is decorative.

## Tests

The module has **no Compose UI-test harness** (no `createComposeRule`/`ui-test`). Required set is
pure-function + controller-capture, in the `SautiSoloCallTest`/`CallUiStateFlowTest` style; add
`AudioOutputTest.kt`. Each asserts real behaviour.

Escalation predicate:
1. `routePickerShownWhenBluetoothPresent` — `{EARPIECE,SPEAKER,BLUETOOTH}` → true.
2. `routePickerHiddenWhenPhoneOnly` — `{EARPIECE,SPEAKER}` → false.
3. `routePickerHiddenWhenOnlyWiredHeadset` — `{SPEAKER,WIRED_HEADSET}` and
   `{EARPIECE,SPEAKER,WIRED_HEADSET}` → false.
4. `routePickerShownWhenBluetoothAndWiredPresent` — full set → true.

Toggle target:
5. `toggleFromSpeakerGoesToEarpieceWhenNoWired` — `(SPEAKER, {EARPIECE,SPEAKER})` → EARPIECE.
6. `toggleFromSpeakerPrefersWiredWhenPresent` — `(SPEAKER, {SPEAKER,WIRED_HEADSET})` → WIRED_HEADSET.
7. `toggleFromEarpieceGoesToSpeaker` → SPEAKER.
8. `toggleFromStaleBluetoothGoesToSpeaker` — `(BLUETOOTH, {EARPIECE,SPEAKER})` → SPEAKER (transient frame).

Toggle on/off:
9. `speakerToggleOnWhenSpeaker` — true for SPEAKER.
10. `speakerToggleOffWhenEarpieceOrWired` — false for EARPIECE and WIRED_HEADSET.

Membership guard:
11. `toggleNoOpsWhenTargetAbsent` — target computed but `target ∉ devices` ⇒ no `selectDevice` call
    (controller-capture double).

Labels / order:
12. `audioDeviceLabelsAreGeneric` — each enum → its SautiStrings default.
13. `audioDeviceOrderPlacesBluetoothInEnumPosition` — `audioDeviceOrder({BLUETOOTH,EARPIECE,SPEAKER})
    == [EARPIECE,SPEAKER,BLUETOOTH]`.

Controller capture (fake `SautiCallController`, pattern from `CallUiStateFlowTest.kt:15`):
14. `toggleSelectsComputedTarget` — invoking the toggle handler captures the expected device per
    starting route.

Keep existing `audioButtonActiveIsFalseForEarpiece` / `…TrueForOtherDevices` unchanged.

Optional, only if `androidx.compose.ui:ui-test-junit4` + Robolectric `createComposeRule` is added
(a new dependency, not present today; must not block the change if declined):
15. phone-only renders the Speaker toggle + caption, no sheet.
16. Bluetooth present opens the sheet on tap with all routes.
17. current route row is `Selected`.
18. row tap selects the route and dismisses; sheet auto-dismisses when BT disappears mid-open.

## Rollout

Bump `android/ui-compose/build.gradle.kts` `0.1.26 → 0.1.27`, publish the JitPack tag (erickogi +
SafeBoda snapshot), bump both apps. Device names for Bluetooth are out of scope (`AudioDevice` is a
bare enum, `AudioSessionCoordinator.kt:15`); exposing names would need an engine `AudioController`
contract change and is a separate follow-up.
