# Android device verification

Status: **NOT TESTED**. `adb devices -l` returned no attached devices and
`emulator -list-avds` returned no installed AVDs (both exit 0 during recovery).
No authorised device/compatible emulator run is recorded.
Record device model, Android build, APK hash, permissions and test observer before
testing startup/service loading. That result alone never establishes SIP registration.

Required handset cases after genuine build: reboot; foreground service; registration
refresh/recovery; battery onboarding; microphone/notification permissions; locked-screen
incoming calls; network reconnect; Bluetooth; Wi-Fi/mobile transitions; Doze;
24-hour idle reachability. Record measured recovery times and failures, not assumptions.
