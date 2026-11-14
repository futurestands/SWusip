# SWUSIP ? South Western Umbrella Call Centre

Android softphone and offline customer directory for the South Western Umbrella
of Water and Sanitation, Ministry of Water and Environment, Uganda.

The genuine Baresip native SIP engine is retained. The institutional interface
adds an agent workspace, protected administrator settings, local encrypted customer
records, scheme search, profiles, CSV/XLSX import and encrypted spreadsheet export.
The intended deployment is 15 authorised agents using existing Grandstream UCM6302A
extensions. No central directory server is required for this pilot.

## Start here

- [Feature inventory and data model](docs/SWUSIP_REDESIGN.md)
- [Pilot setup, offline phone authorisation and customer imports](docs/SWUSIP_PILOT_GUIDE.md)
- [Excel customer template](docs/templates/swusip-customers.xlsx)
- [CSV customer template](docs/templates/swusip-customers.csv)
- [Native build verification and earlier milestones](docs/BUILD_VERIFICATION.md)
- [Physical-device validation and the 1.0.1-pilot correction](docs/SWUSIP_DEVICE_VALIDATION.md)
- [1.0.2-pilot dialler repair and device tests](docs/SWUSIP_DIALLER_REPAIR.md)
- [1.0.3-pilot Android continuation and remaining release gates](docs/SWUSIP_ANDROID_CONTINUATION.md)
- [Agent guide](docs/SWUSIP_AGENT_GUIDE.md) and [administrator setup](docs/SWUSIP_ADMIN_GUIDE.md)

## Build

Open this Android project directory in Android Studio on Windows. Retain the
pinned toolchain and genuine native archives described by the existing build
documentation. Run:

`gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug`

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Device test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
Do not confuse a debug pilot with a production-signed institutional release.
Phone installation, authenticated directory review and real PBX/audio tests are
separate gates. Version 1.0.3-pilot is installed and ten Android instrumentation
tests pass, including keypad editing and locked-workspace denials. Directory
workflow tests require workspace authentication; approved PBX calling remains
unverified. See the Android continuation record for exact results and limits.

## Data and authorisation

Use synthetic records for development. Never commit real customer spreadsheets,
credentials or the offline issuer's private key. Authorisation supports device-bound
signed files and Android managed configurations. The debug public authority alone
is committed; the issuer private key stays in ignored `.local-pilot-authority/`.
Release builds require a separately approved institutional authority or managed policy.

## Third-party licences

SWUSIP derives from Baresip Studio by Juha Heinanen and TutPro Inc. and uses the
Baresip SIP engine and other open-source components. Original copyrights and licence
conditions are retained in [LICENSE](LICENSE), the in-app Third-Party Licences page
and [bundled notices](app/src/main/assets/third-party-notices.txt). Historical upstream
background is preserved in [UPSTREAM_README.md](docs/UPSTREAM_README.md).
