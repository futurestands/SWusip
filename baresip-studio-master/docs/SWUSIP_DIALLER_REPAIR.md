# SWUSIP dialler repair — 1.0.2-pilot

Validation date: 14 November 2026 (Africa/Nairobi). Baseline: `f9c312d` on
`feature/swusip-call-centre`. Device: authorised 25028RN03A, Android 15/API 35,
connected through wireless ADB.

## Root cause and diagnosis

Two Android defects were confirmed in `MainScreen.kt`:

- The Dialler navigation toggled `isDialpadVisible`, but that state only changed
  the software keyboard type. No numeric keypad was rendered. Tapping Dialler
  therefore did not reliably present number-entry controls.
- `callClick` silently did nothing when no account was selected. The handset
  showed **Extension not provisioned** and **SIP connection: Not registered**.
  A missing extension prevented any genuine outgoing SIP attempt.

The handset had both microphone and notification permissions granted. No JNI
loading failure was observed. No evidence establishes a PBX or trunk restriction:
no registered test extension or approved live call was available. An external MTN
call was not attempted and is not used to assess the dialler.

## Correction

- Render an accessible 12-key number pad with Backspace and Clear controls.
- Make Dialler navigation select phone keypad mode consistently, rather than
  toggling it away. Keep typed SIP address entry available.
- Keep entered digits in the existing `DialerState.callUri`; keypad edits also
  clear stale redial/suggestion state and update the selected account's resume
  number when an account exists.
- Label the number field and Call/clear/conference actions. Show the conference
  action only when a call exists.
- When no extension is configured, pressing Call displays:
  **Not connected to the office telephone system. Contact your administrator to
  configure your extension.**
- Explain registration and microphone-permission failures and Android call-start
  security failures. Accounts intentionally configured without registration retain
  the existing direct SIP behaviour.

The genuine calling route remains `makeCall` → Android Telecom →
`ConnectionService` → `BaresipService.runCall` → `Call.connect` → JNI
`Api.call_connect`. Native sources, build settings and security authorisation
controls were not changed. The packaged `libbaresip.so` is byte-identical to the
1.0.1-pilot APK (SHA-256
`108931f7a0780b4009c9548ecd5824a6ef26e266466870aa9456c50c3bc19fed`).
This source inspection does not prove live number delivery to the PBX.

## APK and installation

- Package: `ug.go.mwe.swusip`.
- Version: **1.0.2-pilot**, version code **588**; debug pilot signing.
- APK: `artifacts/SWUSIP-1.0.2-pilot-debug.apk` in the Android project directory.
- SHA-256: `6b9971dc38932f6235bd4d200742f5d67d295eaf4460b36ba847da12b21510ad`.
- `adb install -r` succeeded for application and test APK. No uninstall or data
  clearing was used. The installed base APK checksum matched this artifact.

## Verification

- Debug APK, instrumentation APK and JVM test build: **passed**.
- **28 JVM tests passed**, no failures.
- Android lint: **passed, 0 errors and 79 warnings**; no rules disabled.
- **8 physical-device instrumentation tests passed** (`OK (8 tests)`, 12.684 s).
  The new real-activity test exercised repeated Dialler selection, every key in
  `123456789*0#`, exact number-field contents after each tap, Backspace, clearing,
  entering synthetic extension `101`, Call error feedback and dismissal. It
  asserted that no call was allocated and cleared the synthetic input afterwards.
  This test requires an unlocked, unprovisioned handset; it never calls the PBX.
  The seven existing Excel/security/database tests also passed.
- **12 Python checks passed**: native source integrity, diagnostic privacy and
  translation resource coverage.
- APK identity, arm64 ELF, required JNI exports and APK signature: **passed**.
- Ten-second scoped startup observation: process alive, native library mapped,
  foreground service present; **zero** crash, native-loading, foreground-service
  and security-exception counters.
- Accessibility inspection and actual control actions were used. Screenshot
  capture was protected/black; no capture protection was bypassed and no pixel
  layout pass is claimed.

Evidence: [build/test records](evidence/dialler-repair/),
[startup/checksum record](evidence/device-validation/dialler-startup.json).

## Required genuine SIP verification

An administrator must configure an approved spare UCM6302A extension on the
handset, entering its secret directly on the phone, and identify a second approved
internal test endpoint. A request for these test arrangements is pending.

Registration, number delivery to the native engine during a live call, internal
incoming/outgoing call presentation, two-way audio, mute, speaker, hold, transfer,
keypad during a call and hang-up remain **NOT VERIFIED**. Do not treat the passing
unprovisioned Call feedback test as a successful SIP call. Test internal extensions
first; MTN/external trunk tests require a separate approved destination and record.

No production PBX/server configuration was changed. No real customer records or
live synthetic telephone destinations were used.
