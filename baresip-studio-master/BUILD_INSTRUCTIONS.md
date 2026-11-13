# SWUSIP — Umbrella Call Centre Build & Deployment Instructions

**Organisation:** South Western Umbrella of Water and Sanitation (SWUWS), Ministry of Water and Environment, Uganda  
**Application Name:** SWUSIP  
**Display Name:** SWUSIP
**Application Identifier:** `ug.go.mwe.swusip`  
**PBX Integration:** Grandstream UCM6302A SIP PBX  

---

## 1. Prerequisites & Development Environment

- **Development OS:** Windows with Windows Subsystem for Linux (WSL) enabled.
- **IDE:** Android Studio (Koala / Ladybug or newer) with Java Development Kit (JDK 21) and Android SDK (Compile SDK 37, Target SDK 36, Min SDK 28).
- **Build Tool:** pinned Gradle 9.6.0 wrapper, JDK 21, SDK CMake 3.31.6, NDK 30.0.16248370.
- **Host distinction:** Windows NDK for Gradle; matching Linux NDK for WSL native builds.

---

## 2. Native Library Compilation (`libbaresip-android`)

The underlying SIP and VoIP protocol engine (`baresip` and media libraries like OpenSSL, Opus, Libre, etc.) is compiled as native C/C++ libraries (`.a`/`.so`) via the `libbaresip-android` project.

### Building via WSL (Recommended on Windows):
1. Open WSL terminal in the project root.
2. Ensure prerequisites are installed:
   ```bash
   sudo apt update && sudo apt install -y wget cmake make libtool m4 automake pkg-config unzip build-essential
   ```
3. At the actual Git root (one directory above this Android project), run
   `git submodule update --init`. The gitlink remains pinned to upstream `bf8448d`.
4. In the Android project run `python scripts/prepare_native.py`. This fetches
   immutable commits from the committed lock and exports native source archives.
   Do not run upstream `make download-sources` over existing source directories.
5. In WSL set `ANDROID_NDK_ROOT` to an extracted **Linux** r30 NDK, and
   `SWUSIP_NATIVE_SOURCE` to an initially absent directory on the Linux filesystem.
   Run `bash scripts/stage-native-linux.sh` to extract and apply upstream patches.
6. Run `bash scripts/run-native-build.sh arm64-v8a`. It records compilation output
   and the exact exit code under `docs/evidence/`, and checks object members,
   architecture and genuine engine symbols. No empty archive workaround is allowed.

The first build targets arm64 only. Build other ABIs separately before selecting
them with Gradle's `-PswusipAbis=...`. See `docs/NATIVE_DEPENDENCIES.md` for source
and licence details and `docs/BUILD_VERIFICATION.md` for acceptance evidence.

---

## 3. Building the Android APK

Once native libraries are compiled into `distribution/`:

1. Open the project in Android Studio or build via command line:
   ```cmd
   cd baresip-studio-master
   powershell -File scripts/validate-android.ps1
   ```
2. The debug APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`
   The validation script runs clean, assembleDebug, lintDebug and the Android unit
   task separately, recording individual exits. `--offline` is used by default for
   installed dependencies; supply `-Online` when dependency resolution is required.
   Genuine native/APK verification and current hashes are in `docs/BUILD_VERIFICATION.md`.
3. Use `docs/PHYSICAL_DEVICE_TEST_GUIDE.md` and `scripts/test_android_device.py`
   to select the authorised API 28+/arm64 phone, install, launch and inspect scoped
   diagnostics. Do not uninstall or clear data to work around installation failures.

---

## 4. Grandstream UCM6302A PBX Configuration Guide

Do not alter the live PBX or network. Obtain two approved non-production extensions
and configuration from its administrator, with passwords delivered out of band.
Follow `docs/SIP_INTEROP_REPORT.md`: prove digest authentication, rejection of
incorrect credentials, refresh, incoming/outgoing calls and two-way audio. UI
indicators and application startup alone do not establish interoperability.

This repository remains public. Do not commit internal addressing, credentials,
signing keys, customer records or unredacted authentication traces. Production
deployment requires the acceptance gates in `docs/DEPLOYMENT_GUIDE.md`.
