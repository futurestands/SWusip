# Genuine UCM6302A SIP acceptance checklist

Record APK version/checksum, handset/Android version, network, approved test
extension, second internal endpoint, tester, time and actual outcome. Enter
credentials directly in the phone; exclude secrets and customer data from evidence.
Use synthetic directory records. No production PBX/trunk/firewall changes are
authorised by this checklist.

## Internal extension tests — first gate

- Register the approved spare extension with the administrator-supplied host, port,
  transport, certificate and media security settings. Compare SWUSIP's displayed
  status with the UCM's actual test-extension registration. Test loss/recovery of
  the office network; never report Connected based on app launch alone.
- Call the approved second internal extension. Confirm its ringing destination,
  the entered-number routing and outgoing/cancel/connected UI. Answer at that
  endpoint. Speak and listen both ways; record both participants' confirmation.
- Call SWUSIP from the second endpoint. Check immediate answer/reject controls,
  foreground and locked/background presentation, notification privacy and audio.
- Test Mute/unmute, Speaker/earpiece, Hold/resume, in-call keypad/DTMF, approved
  blind/attended transfer destinations, and hang-up from both ends. Confirm UI,
  audio and PBX call state are released after each test.
- With approved synthetic caller identification, verify local normalisation,
  one/multiple/no customer matches, account and scheme text, profile opening and
  call-note entry without interrupting controls/audio. A short internal extension
  is not automatically an international customer number; do not invent a match.
- Test workspace expiry/phone lock: customer information must become inaccessible,
  but incoming controls and genuine calling must remain responsive.
- Test background delivery after app switching and screen locking under the
  handset's approved battery policy. Test notification permission denial/recovery
  and microphone denial/recovery. Record observation durations and limitations.
- Repeat after app/service restart and handset reboot, including secure credential
  availability. A key invalidation must not silently delete customer data.

## External/MTN tests — separate gate

Only after internal tests pass, obtain a separately approved external destination,
cost authorisation and UCM trunk/routing arrangement. Record external outcomes
separately. A trunk rejection, unavailable credit/capacity or provider restriction
is not evidence that keypad input is broken. Do not modify trunks to make a test pass.

## Current result

**NOT VERIFIED:** no approved configured SIP test extension and genuine call
evidence have been supplied. Native integrity, successful build, app launch,
synthetic lookup and an unprovisioned Call-button message do not pass this gate.
