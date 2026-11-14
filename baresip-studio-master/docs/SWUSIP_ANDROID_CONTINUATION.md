# SWUSIP 1.0.3-pilot Android continuation

Baseline: `df3c713`, branch `feature/swusip-call-centre`.
Authorised physical handset: 25028RN03A, Android 15/API 35.
Every ADB operation after endpoint selection explicitly uses
`-s 192.168.1.112:46723`; the duplicate discovery entry is not used as a target.

## Installed APK

- Package `ug.go.mwe.swusip`, version **1.0.3-pilot / 589**.
- Artifact `artifacts/SWUSIP-1.0.3-pilot-debug.apk` in the Android project folder.
- SHA-256 `7c51e35ff7f3b7ffb0e4cc97a3e9ba0df97ee6a079cebef3c93ad0302750d4cf`.
- Application and test APK installed using `adb install -r`, with Success.
  Installed version was read back from the explicitly selected endpoint.
- No uninstall, app-data clear, production PBX/server change or real customer
  data was used. This is still a debug pilot, not a production release.

## Changes

See [the prioritised code review](SWUSIP_ANDROID_REVIEW.md) for defect evidence,
release blockers and existing features deliberately retained. This update fixes
account/phone cross-matching, indexes imports for large batches, validates repository
input, reuses authenticated Keystore handles, adds explicit undo confirmation and
bounded management controls, prevents password validation messages from echoing
secrets, removes message content from a debug statement, and explains empty Call
History navigation. It retains the repaired dialler and genuine native SIP library.
Shared-account preview now rejects ambiguous ownership consistently with commit,
regardless of which associated customer appears last in the input data.

Settings now displays authorised agent identity, temporary local desk status and
an Android app/battery-settings shortcut. Managed identities can use
`swusip_agent_id`. New history records retain assigned-agent IDs and call details
show assignment without attributing old calls or claiming who answered.

## Measured tests

- Gradle debug application/test APK assembly and JVM tests: **PASS**.
- **35 JVM tests passed**, no failures/errors: CSV/XLSX parsing and 10,000-row
  batches, leading-zero identifiers, matching, duplicates/conflicts, encrypted
  portable export, and legacy/new call-history identity serialisation.
- **10 physical-device instrumentation tests passed**, 17.151 seconds: all
  previous dialler/Excel/schema tests plus 10,000-row CSV parsing and denial of
  protected repository operations in a locked workspace.
- **14 Python checks passed**: native source integrity (2), diagnostic privacy (6),
  resource coverage (4), and the actual account-phone SQL relation query (2).
  The SQL tests use an isolated synthetic SQLite schema; they are not an encrypted
  Android repository or document-picker workflow test.
- APK identity, arm64 ELF, required JNI exports and APK signature: **PASS**.
- Android lint: **PASS**, 0 errors/fatal issues, 83 warnings; no rules disabled.
- Final installed checksum matched the artifact. Ten-second scoped startup:
  process alive, genuine library mapped, foreground service present; zero crash,
  native-loading, foreground-service and security-exception counters. This proves
  startup only, not real calling or long-term reliability.
- The native library is byte-identical to the baseline APK (SHA-256
  `108931f7a0780b4009c9548ecd5824a6ef26e266466870aa9456c50c3bc19fed`).

## Authentication-dependent tests — blocked, not passed

The opt-in authenticated test attempted the real Customers workspace credential flow
and waited 90 seconds. An owner-confirmed administrator workspace was not observed. The test therefore
stopped at its authorisation assumption before encrypted imports/search/notes/undo.
The Android runner prints `OK (1 test)` for this assumption skip; **that output is
not counted as a passed authenticated test**. Its final 92.84-second transcript
explicitly records `INSTRUMENTATION_STATUS_CODE: -4` (assumption skip) and is
retained separately from the ten passing device tests.

Consequently the following remain **NOT VERIFIED on the handset**:

- CSV/XLSX document-picker import into the actual encrypted directory, preview,
  duplicate/update totals, scheme/account search, profiles and UI undo.
- Authenticated 10,000-record encrypted database performance and reversal.
- Agent-role UI comparison against administrator-role UI using normally issued
  grants. Locked-workspace denial passed; it does not replace this role comparison.
- Customer lookup/profile controls during genuine incoming/active SIP calls.

Required owner action: unlock the handset, then complete **Unlock SWUSIP** using
the phone's PIN/pattern/password directly on the phone. The workspace must use its
signed administrator grant for imports. Do not send credentials in chat. No test
reflects into or injects the workspace session, modifies grants, or bypasses the
Keystore/phone security controls. The opt-in repository test uses only uniquely
named synthetic databases and deletes only its own fixtures after actual access
has been authorised; it does not overwrite or clear the real directory.

## Remaining release gates

- Saved native SIP/STUN credentials and legacy history/recordings still require
  a reviewed storage/migration design. Password-error disclosure was fixed, but
  this update does not claim an encrypted SIP credential vault or production
  security acceptance. Keep native diagnostic traces away from real data.
- Real UCM registration, internal incoming/outgoing calls, two-way audio, mute,
  speaker, hold, transfer, keypad/DTMF, hang-up, background reliability and caller
  identification remain **NOT VERIFIED**. No test credentials or live call were
  invented. External/MTN trunk tests remain a separate approved gate.
- PBX queues, shared availability, trunk capacity, shared CRM and central history
  need PBX/server configuration. `CustomerSync` has no network implementation.
- SMS/MMS internals remain pending genuine SIP regression, as instructed.

Guides: [agents](SWUSIP_AGENT_GUIDE.md), [administrators](SWUSIP_ADMIN_GUIDE.md),
[genuine SIP checklist](SWUSIP_UCM_TEST_CHECKLIST.md).

Evidence: [continuation records](evidence/android-continuation/),
[startup observation](evidence/device-validation/continuation-startup.json).
