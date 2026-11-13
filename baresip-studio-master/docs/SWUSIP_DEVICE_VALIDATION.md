# SWUSIP physical-device validation follow-up

Baseline: GitHub commit `170be36` on `feature/swusip-call-centre`.

## Installed correction

- Package: `ug.go.mwe.swusip`.
- Installed version: **1.0.1-pilot**, version code **587**.
- Physical handset: model **25028RN03A**, Android **15**, API **35**, arm64.
- Local APK: `artifacts/SWUSIP-1.0.1-pilot-debug.apk`.
- SHA-256: `d09a666247efffcad42acd6ff8ff97d1f7ffe14e8fe7cbaca9a7097caa97d73f`.
- Both the original and corrected installations used `adb install -r`.
  No app uninstall, app-data clearing or existing-data deletion was performed.
- The installed APK checksum was independently read from the phone and matched.

The original **1.0.0-pilot / 586** APK was first verified against the SHA-256 in
the original delivery record, then installed successfully. Its original artifact
and historical build evidence remain available.

## Defect reproduced and corrected

The original Android instrumentation run executed six tests and failed four XLSX
cases with `DTD processing must remain disabled`. Host-side tests had passed.
On this handset, `android.util.Xml.newPullParser()` begins with document declaration
processing enabled, while the JVM test parser leaves it disabled.

The importer now explicitly disables that feature where supported and verifies
the resulting state. Parsers that already disable it but reject the setter remain
supported. Explicit DTD rejection before parsing is retained. No security check was
removed. An additional real-Android regression test verifies DTD rejection.

## Passed checks

- **7 of 7 Android instrumentation tests passed on the physical handset**:
  leading-zero text accounts, numeric-account rejection, formula rejection, the
  actual empty Excel template, DTD rejection, forged authorisation rejection,
  and schema reopen/unknown-migration preservation/foreign-key integrity.
- **28 JVM tests passed** after the correction.
- **12 existing Python checks passed**: native source integrity, diagnostic-log
  scoping/privacy and translation coverage.
- Corrected APK and device-test APK assembly passed.
- Lint passed: **0 errors, 79 warnings**.
- The corrected app launched; the process and genuine native SIP library were
  observed, and the foreground service was present.
- The ten-second corrected startup observation recorded **zero** crashes, native
  loading errors, foreground-service exceptions or security exceptions.
- Native SIP source and native build settings were not modified by this fix.

The original startup observation was incomplete because the foreground service
was not observed in its window. The later corrected observation did observe it.
This timing difference is not attributed to the XML fix.

Evidence is in [device-validation/verification.json](evidence/device-validation/verification.json),
the original/corrected instrumentation transcripts, corrected build/lint logs,
unit-test XML, and the original/corrected startup reports in that directory.
Raw UI dumps and device diagnostics remain in ignored local storage.

## Interactive tests blocked by the phone lock

ADB can reach the phone, but repeated UI inspection found only the Android System UI
keyguard. The SWUSIP home screen was not visible to the tester. A request to unlock
the phone and leave SWUSIP open is pending. The phone lock and workspace credential
gate were not bypassed or disabled.

Consequently these checks are **not yet completed**:

- Visual home/header, About, navigation, labels, buttons and small-screen layouts.
- Signed phone enrolment and credential-confirmed workspace access.
- Actual document-picker import into the encrypted customer database.
- Scheme/account search, multiple-match and customer-profile interaction on screen.
- Customer notes, profile callbacks, account association and undo through the UI.
- Customer panel behaviour while genuine incoming-call controls are active.

CSV and XLSX fixtures with **three synthetic rows**, two synthetic schemes and one
shared caller number have been prepared locally and copied to a new, uniquely named
phone Downloads folder. They have **not** been imported. Real customer records have
not been used. The synthetic telephone values are format fixtures and must not be
dialled as live test destinations.

Once the phone is unlocked, use the signed-authorisation workflow and confirm the
phone credential on the handset itself. Do not send a PIN, password or SIP secret
in chat. Then continue the interactive tests with these synthetic records.

## PBX verification remains separate

No approved Grandstream UCM6302A test call was performed. SIP registration,
incoming/outgoing call completion, two-way audio, hold, transfer, keypad, mute,
termination and live caller-to-customer identification remain **not verified**.
Native loading and foreground-service startup do not establish successful calling.

No production PBX/server configuration was changed. SMS/MMS backend removal remains
gated on genuine SIP regression, as required in the original work order. This is a
partial device-validation milestone, not full acceptance of the application.
