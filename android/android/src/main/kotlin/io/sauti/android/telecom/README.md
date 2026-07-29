# io.sauti.android.telecom

An opt-in `android.telecom` self-managed `ConnectionService` binding. When an
adopter wires it, a Sauti call registers as a real OS call: native in-call
priority, OS-driven hold when a cellular call arrives, Bluetooth and car routing,
and Do-Not-Disturb participation.

This package is off by default. The default `SautiClient` constructor does not
touch telecom, registers no `PhoneAccount`, and keeps `AudioSessionCoordinator` as
the audio owner. Nothing here runs unless the adopter opts in.

## What the adopter declares

None of these entries live in the library's base manifest, because a manifest
merge would change every adopter's install-time permission set. The adopter adds
them to its own app manifest only when it opts in.

```xml
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />

<service
    android:name="io.sauti.android.telecom.SautiConnectionService"
    android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.telecom.ConnectionService" />
    </intent-filter>
</service>
```

`MANAGE_OWN_CALLS` is required for a self-managed account to place or report calls.
`BIND_TELECOM_CONNECTION_SERVICE` on the service lets the platform bind it.
`FOREGROUND_SERVICE_PHONE_CALL` is optional and only needed if the adopter runs a
phone-call-typed foreground service alongside the connection.

The telecom classes require API 26. On lower API levels the adopter keeps the
default path.

## What telecom adoption replaces

Adoption is a clean either/or with the default coordinator. An adopter runs one
path, not both.

- It replaces `AudioSessionCoordinator`. Telecom owns audio: mode,
  focus, and communication-device routing belong to the OS `Connection`. The
  adopter injects `TelecomAudioController`, which enumerates devices for the UI but
  never sets `MODE_IN_COMMUNICATION`, never requests audio focus, and never calls
  `setCommunicationDevice`. Physical routing follows `onCallAudioStateChanged`.
- It replaces the `TelephonyWatcher` mute policy. The OS itself drives
  `Connection.onHold` when a cellular call arrives, so the telecom wiring installs
  an inert telephony watcher. The adopter does not run both the OS hold and the
  `InterruptionReducer` mute policy.

## Wiring

```kotlin
val client = SautiTelecomClient.build(context)

val account = SautiTelecomAccount(context)
account.register(displayTitle)

val adapter = SautiTelecomAdapter(scope, client, onAnswer = { /* join */ })
adapter.attach(displayTitle, participantId)

account.place(participantId)
```

`SautiTelecomClient.build` constructs a `SautiClient` with `TelecomAudioController`
and the inert telephony watcher. `SautiTelecomAccount` wraps `TelecomManager` with a
`CAPABILITY_SELF_MANAGED` `PhoneAccountHandle`. The account, connection, and address
carry only the opaque `displayTitle` and `participantId` under a neutral `sauti`
scheme. No phone number, account identifier, or contact name is used as identity.

## What is not unit-proven here

The following depend on the device telecom stack and are covered by a standing
manual device demo, not by unit tests:

- the audio-focus handoff between the OS `Connection` and the audio owner
- `onCallAudioStateChanged` physical routing
- `onShowIncomingCallUi`
- the crash path when a call is placed against an unregistered `PhoneAccount`

The unit tests cover the pure `TelecomReducer`, the `TelecomAudioController`
no-ownership behaviour, and that the default `SautiClient` path keeps
`AudioSessionCoordinator` and the `TelephonyWatcher`.
