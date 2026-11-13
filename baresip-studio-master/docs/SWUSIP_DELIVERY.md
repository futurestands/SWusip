# SWUSIP 1.0.0-pilot development delivery

**Follow-up:** 1.0.1-pilot / 587 is now installed on the authorised Android phone.
It corrects the Android XLSX parser defect found by physical-device testing;
seven Android instrumentation tests pass. Interactive UI review still requires
phone unlock. See [the physical-device validation record](SWUSIP_DEVICE_VALIDATION.md)
for the new APK path/checksum and remaining tests. The record below describes
the original `170be36` milestone and preserves its original APK checksum.

Package `ug.go.mwe.swusip`, version code **586**, version **1.0.0-pilot**.

## Delivered code and artifacts

- SWUSIP institutional home, neutral app/notification marks, labelled navigation,
  connection status, recent calls and simplified agent settings.
- Separate administrator authorisation using signed offline device grants, with
  Android managed-role support for future managed devices. Protected workspace
  unlock uses the device credential and expires after five minutes.
- Encrypted SQLite customer directory, relational phone/account/scheme associations,
  customer and scheme search, profiles, primary/additional phones, call notes,
  administrator name/location updates and reviewed existing-account association.
- Local CSV/XLSX import, downloadable text-formatted templates, preview actions,
  row validation, duplicate/conflict handling, import totals and safe latest-batch undo.
- Password-encrypted spreadsheet export and preview-based restore. No automatic
  deletion or external customer-data upload. Future sync is an unconfigured interface.
- Asynchronous customer lookup connected to the genuine call object's peer URI,
  with multiple-account indication and a profile link. Account data is not added
  to notifications. Customer/account screens block screenshots and task thumbnails.
- Institutional About, bundled original third-party licence notices, test suites
  and setup/security documentation.

Local APK: `artifacts/SWUSIP-1.0.0-pilot-debug.apk` (ignored build deliverable).
The normal Android output remains `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `8c3c6ac27920c31f62485b3190ba80723d1e12491154f09da6e8d2e43042177d`.

## Passed local checks

- APK assembly and Android device-test APK assembly.
- **28 JVM unit tests**, including actual XLSX-template parsing, leading-zero
  preservation, row numbers, formula/DTD rejection, required search fields,
  duplicate/conflicting accounts, multiple schemes, synthetic incoming-number
  matching and encrypted-export round trips/tampering/wrong-password rejection.
- **12 existing Python tests** for genuine native source integrity, scoped device-log
  privacy and supported translation resources.
- Android lint: **0 errors, 79 warnings**. Lint checks were not disabled.
- All **15 native archives** passed ABI, archive and required SIP-symbol checks.
  Native source, submodule and archive inputs were not changed by this milestone.
- Final APK identity, arm64 native library/JNI exports and debug signature verified.

Evidence: [verification.json](evidence/swusip-pilot/verification.json),
[build log](evidence/swusip-pilot-build.log),
[native archive checks](evidence/swusip-redesign-native.txt),
[APK inspection](evidence/swusip-pilot/apk-inspection.txt), and test XML files in
`docs/evidence/swusip-pilot/`. Earlier failed development attempts are retained;
the final build log and current test results identify the delivered artifact.

## Explicitly deferred and remaining

The user powered off the phone to charge and requested that device testing occur
later. **This new APK has not been installed or reviewed on the phone.** Six Android
instrumentation tests are compiled but have not been executed. Authenticated
directory import/profile/note/undo flows, small-screen navigation and background-call
behaviour still need physical-device review.

No SIP extension has yet been provisioned for this milestone. Real registration,
incoming/outgoing calls, two-way audio, mute, speaker, hold, transfer, keypad,
termination and live incoming customer identification are **not verified**.
The synthetic lookup test is a directory test and is not a simulated successful PBX call.

Underlying SMS/MMS components remain until genuine SIP regression is completed,
as required by the original execution condition. Their ordinary home/contact/history
controls have been removed. The debug pilot authority's private key stays local and
is not committed. Production institutional signing/key custody and an independent
security/data-protection review remain necessary before real-customer deployment.

No production PBX or server changes were made. Full acceptance is not yet claimed.
Follow [the pilot guide](SWUSIP_PILOT_GUIDE.md) when the phone is available.
