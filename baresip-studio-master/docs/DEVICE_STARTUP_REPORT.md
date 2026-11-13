# Authorised physical-device startup — 2026-11-13

Starting revision: `751e150`, branch `feature/swusip-call-centre`.
Verified diagnostic correction commit: `f82f517`; the sanitised device evidence
and report updates are maintained in the follow-up documentation commit.
The project lead supplied an already-authorised wireless ADB connection. The device
endpoint, serials, installed paths and raw diagnostics stay in the ignored local
test cache; they are not part of public evidence.

## Device and APK

- Model: `25028RN03A`; Android **15**, API **35**.
- Supported ABIs: arm64-v8a, armeabi-v7a, armeabi; the tested APK is arm64-v8a.
- Application: `ug.go.mwe.swusip`, label `SWUSIP`.
- Installed version: **94.1.9**, version code **585**.
- Local APK: `app/build/outputs/apk/debug/app-debug.apk`, 87,360,786 bytes.
- SHA-256: `4ba7c341017b2c4525798b172f128e9b0aa0b71c35718f0d6c77d30a3353b081`.

The local APK matches the latest committed build evidence. All 15 native archives
and APK JNI/ABI/runtime checks pass again. The installed base APK's SHA-256 was
calculated on the phone and matches the verified local APK exactly.
Android application/native source was unchanged; no replacement APK build was needed.

## Observed results

- ADB state: `device`, model/API/ABI queries successful.
- `adb -s <authorised-device> install -r <verified-apk>`: **exit 0 / Success**.
  Existing app data was retained through reinstall; no uninstall/data-clear command
  was issued and no credentials were migrated or edited by the test harness.
- `am start -W -n ug.go.mwe.swusip/com.tutpro.baresip.MainActivity`: **exit 0**, status ok.
- UI/application process remained alive during the observation.
- Corrected follow-up observation: **exit 0**, 30 seconds, 9 app-UID Logcat lines
  within the device-clock window. Detected crash/JNI-loading/foreground-service/
  security-exception counts: **0 / 0 / 0 / 0**.
- Genuine Baresip executable mapping observed in the exact installed base APK;
  `BaresipService` observed active in the foreground.
- Microphone, notification and Bluetooth-connect runtime permissions were granted
  when inspected. The harness did not grant or change permissions.

These are bounded startup observations, not a long-term reliability result. No
registration, call establishment, microphone audio quality or two-way RTP test has
been performed. **Gate 2 is NOT PASSED.**

## Necessary test-harness fixes

The first run installed/launched successfully, but its diagnostic interpretation
was inconclusive: padded epoch timestamps were discarded, and `/proc/maps` did not
name `libbaresip.so`. This was not evidence of an Android app defect.

1. Logcat epoch parsing now accepts leading whitespace and excludes entries older
   than the device-clock test boundary. Historical crash entries preceding this
   installation were excluded from current failure counts.
2. Modern Android directly maps uncompressed native ELF segments from `base.apk`.
   The harness now checks executable mapping offsets within Baresip's ZIP entry
   in the exact, checksum-verified installed APK, as well as extracted-library paths.
3. Process survival alone no longer passes the service test: native mapping and
   the foreground service must both be observed.
4. `--observe-only` permits repeat diagnostics without reinstalling; expected APK
   checksum and report-path options make verification and evidence explicit.

All **12 Python regression tests pass**, including timestamp filtering, exact app
UID selection, APK mapping boundaries and rejection of incomplete service evidence.
The APK itself was unchanged; previous separate Gradle check results remain valid.

## Evidence and next step

Authoritative results: `evidence/device-startup/observed-result.json` and
`installation.json`. The initial harness report is preserved and explicitly
superseded. Raw Logcat, package dumps, memory mappings and install output remain
under ignored `.native-build/device-tests/`. Public evidence contains only
sanitised outcomes, model/version, hash and counts.

The phone is ready for administrator-approved test-account entry. Open SWUSIP's
Accounts menu and use only a designated non-production extension. Obtain registrar,
transport/port, authentication identity, certificate/media policy and test secret
privately from the PBX administrator. Enter the secret directly on the approved
phone; do not put it in Git, diagnostics or public chat evidence.

Use a second approved extension/endpoint and follow `SIP_INTEROP_REPORT.md` to
verify digest registration, refresh, incoming/outgoing internal calls and actual
two-way audio with tester sign-off. No production PBX setting, live trunk,
external calling route, VPN, firewall or wireless pairing configuration was changed.
