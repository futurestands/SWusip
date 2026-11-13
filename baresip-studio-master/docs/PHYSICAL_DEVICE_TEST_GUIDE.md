# Authorised SWUSIP physical-phone test

Latest result: the authorised Android 15 arm64 phone passed installation, launch,
native executable mapping and foreground-service checks; see `DEVICE_STARTUP_REPORT.md`.
SIP registration and audio are still untested. The existing wireless ADB connection
was explicitly authorised by the project lead; the harness did not pair a device,
enable wireless debugging or change network/security settings.

## USB setup on the phone and Windows

1. Use an institution-authorised test phone running Android 9/API 28 or later
   with arm64-v8a support. Keep it unlocked during USB setup.
2. Open Settings → About phone (on some devices, Software information), find
   **Build number**, and tap it seven times. Enter the device PIN if requested.
3. Return to Settings, search for **Developer options**, open it, and enable
   **USB debugging**. USB debugging does not require OEM unlocking or rooting.
4. Connect to this Windows computer with a **data-capable USB cable**, preferably
   directly rather than through a hub. If the phone offers a USB mode, select
   **File transfer / Android Auto** where required by the manufacturer.
5. Accept **Allow USB debugging?** on the phone after checking it is this trusted
   computer. An `unauthorized` ADB entry means this prompt is still pending.
6. Run the command below. The target must show state `device`. If nothing appears,
   try another data cable/USB port. In Windows Device Manager check for an Android
   ADB interface or a device with a warning icon. Obtain the signed USB driver
   from the phone manufacturer's official support site when needed. Do not change
   driver signing or disable security controls. Existing explicitly authorised
   wireless ADB connections can instead be selected using `--serial`.

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $adb devices -l
```

A custom Android SDK installation requires its actual platform-tools path.
Serial numbers in ADB output stay in the ignored local test cache.

Official references: [hardware device setup](https://developer.android.com/studio/run/device),
[developer options](https://developer.android.com/studio/debug/dev-options),
[Windows OEM USB drivers](https://developer.android.com/studio/run/oem-usb).

## Install, launch and inspect

After native/APK verification and while only the authorised phone is connected:

```powershell
python scripts/test_android_device.py --adb $adb --seconds 30
```

With multiple physical phones, add `--serial <authorised-device-serial>`; do not
put that identifier into public evidence. The script checks API/ABI, runs
`adb install -r`, launches
`ug.go.mwe.swusip/com.tutpro.baresip.MainActivity`, and observes startup.
Reinstall preserves app data. Signature mismatch or downgrade failures must be
investigated; the script never uninstalls, clears data or bypasses authentication.

Use `--expected-apk-sha256 <verified-build-hash>` to bind the test to a particular
build. The harness also checks the installed base APK's digest against the local
APK. To observe an already-installed verified build without reinstalling, add
`--observe-only`. To retain phase-specific evidence without replacing historical
results, supply `--report docs/evidence/device-startup/observed-result.json`.

On the phone, handle the ordinary microphone and notification permission prompts
for the approved test. Do not select default SMS/phone roles just to test VoIP.
Starting the UI alone does not establish that BaresipService loaded or registered.

The harness scopes Logcat by this app's UID and the device-clock test boundary;
checks fatal exceptions/signals, native loading/link errors and foreground-service
exceptions; checks process survival, the debug app's mapped `libbaresip.so` when
permitted, and app-scoped service state. Both extracted libraries and executable
ZIP-entry offsets in the exact installed base APK are supported. Process survival
alone does not pass; native mapping and foreground service must both be observed.
Missing native mapping/foreground state
requires investigation and another observed test after necessary permissions.

Raw install output, Logcat and service diagnostics remain under ignored
`.native-build/device-tests/`. They must not be committed: they can contain SIP
identifiers or authentication details. The public JSON report contains model,
Android version, APK hash, outcomes/counts and limitations, without credentials,
device serial or raw log messages. A clean 30-second observation is not a
reliability or SIP acceptance result.

Disable USB debugging or revoke the workstation's debugging authorisation after
the institution's testing is finished, following its handset policy.
