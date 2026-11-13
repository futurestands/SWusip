# Android device verification

## Latest physical-device startup — PASSED (bounded observation)

An explicitly authorised wireless ADB handset, model `25028RN03A`, Android 15/API
35 with arm64-v8a support, passed installation and startup observation on 2026-11-13.
SWUSIP 94.1.9 (585) installed with `-r` without uninstall/data-clear commands.
Installed/local APK hashes match. The app remained alive, genuine Baresip executable
segments were mapped from the installed APK, and BaresipService was in the foreground.
The corrected 30-second UID/time-scoped Logcat check detected no crash, JNI/loading,
foreground-service or security-exception failure. Required microphone, notification
and Bluetooth permissions were already granted; the harness changed none.

See `DEVICE_STARTUP_REPORT.md` and `evidence/device-startup/observed-result.json`.
No SIP registration, internal calling, audio or long-term reliability pass is claimed.

## Previous no-device milestone (historical)

Status at that milestone: **NOT TESTED**. `adb devices -l` returned no attached devices and
`emulator -list-avds` returned no installed AVDs (both exit 0 during recovery).
No authorised device/compatible emulator run is recorded.
The follow-up localisation milestone rechecked ADB and ran
`scripts/test_android_device.py`; it still found no physical phone. See
`evidence/localisation/device-startup.json`. Installation, launch, scoped Logcat,
runtime native mapping and foreground-service checks are therefore **BLOCKED**,
not passed. USB setup and exact test commands are in
`PHYSICAL_DEVICE_TEST_GUIDE.md`; the harness keeps raw device logs and serials
outside Git and publishes only a sanitised outcome report.
Record device model, Android build, APK hash, permissions and test observer before
testing startup/service loading. That result alone never establishes SIP registration.

Required handset cases after genuine build: reboot; foreground service; registration
refresh/recovery; battery onboarding; microphone/notification permissions; locked-screen
incoming calls; network reconnect; Bluetooth; Wi-Fi/mobile transitions; Doze;
24-hour idle reachability. Record measured recovery times and failures, not assumptions.
