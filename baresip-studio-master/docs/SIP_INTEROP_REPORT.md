# SIP interoperability

Status: **NOT TESTED**. Neither UCM6302A credentials nor two approved test
extensions have been supplied. No live PBX settings were changed.

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
