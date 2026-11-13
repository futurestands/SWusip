# SIP interoperability

Status: **NOT TESTED**. Neither UCM6302A credentials nor two approved test
extensions have been supplied. No live PBX settings were changed.

## Next milestone: Gate 2 internal test

Prerequisites: administrator approval for two **non-production** extensions A and
B on the actual UCM6302A; an authorised arm64 Android handset; a second approved
SIP endpoint/tester; approved internal network reachability; administrator-supplied
signalling transport/port, certificate trust and codec/media policy. Deliver secrets
out of band and enter them only on the approved endpoint. Never commit credentials,
internal addresses, raw authentication headers or customer data. No PBX management
API or menu path is assumed.

1. Record handset model, Android version, APK SHA-256, tester and timestamp. First
   pass installation/startup/native loading/foreground-service checks in
   `PHYSICAL_DEVICE_TEST_GUIDE.md`, granting normal permissions on the handset.
2. Register A from SWUSIP and B from the approved second endpoint to the actual
   test PBX. Record the SIP digest challenge and successful authenticated REGISTER
   response, visible account state and PBX administrator confirmation. An initial
   `401` challenge alone is normal digest negotiation, not proof of failed login.
3. With administrator approval and its lockout policy understood, make one controlled
   incorrect-password attempt on a test account. Confirm rejection; restore the
   approved credentials privately. Never brute-force or test production accounts.
4. Observe refresh across the configured expiry and record the interval/outcome.
   Do not assert refresh works based only on an initial registration indicator.
5. Dial **internal extension B only** from A. Confirm ringing/answer, then have both
   testers speak distinct test phrases: A must hear B and B must hear A. Record the
   negotiated codec, audio routing, duration and independent tester sign-off. End
   the call and verify termination at both endpoints.
6. Repeat the internal call from B to A. Then test incoming calls with the phone
   locked. Record hold/resume and DTMF separately; transfer/reconnect/Doze testing
   belongs to subsequent cases and cannot be inferred from this basic call.

Gate 2 acceptance requires actual-PBX registration plus a verified internal call
with **two-way audio**, with dated tester evidence. Build checks, launch success,
ringing, fabricated UI indicators or one-way audio do not meet it. Record each case
as PASS / FAIL / BLOCKED / NOT RUN; currently **all cases are NOT RUN**.

Keep detailed SIP/Logcat captures in the ignored local recovery cache. Public
evidence may contain pseudonyms A/B, response codes, method names, codec and observed
outcomes only after sanitisation. No live trunk, production extension, external
calling, VPN, firewall or remote-access change is part of this milestone.

Automated results: none yet. Source and symbol checks are build checks, not SIP tests.
Real-device results: none. Gate 2 is open.

After a genuine APK: use an approved isolated lab first, then administrator-provided
non-production extensions. Test digest registration; wrong-password rejection;
refresh; outgoing/incoming internal calls; demonstrable two-way audio; termination
from either side; hold/resume; DTMF; blind/attended transfer; negotiated codec;
locked-screen incoming calls. Record transport, device model, Android version,
APK hash, date, tester and outcome separately for every case.

Traces must omit Authorization/Proxy-Authorization and authentication headers,
passwords, identifiers/customer data and internal addressing before repository
publication. Do not enable verbose SIP traces on operational devices by default.
