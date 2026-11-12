# SWUSIP — Umbrella Call Centre Build & Deployment Instructions

**Organisation:** South Western Umbrella of Water and Sanitation (SWUWS), Ministry of Water and Environment, Uganda  
**Application Name:** SWUSIP  
**Display Name:** Umbrella Call Centre  
**Application Identifier:** `ug.go.mwe.swusip`  
**PBX Integration:** Grandstream UCM6302A SIP PBX  

---

## 1. Prerequisites & Development Environment

- **Development OS:** Windows with Windows Subsystem for Linux (WSL) enabled.
- **IDE:** Android Studio (Koala / Ladybug or newer) with Java Development Kit (JDK 21) and Android SDK (Compile SDK 37, Target SDK 36, Min SDK 28).
- **Build Tool:** Gradle 9.6.0+

---

## 2. Native Library Compilation (`libbaresip-android`)

The underlying SIP and VoIP protocol engine (`baresip` and media libraries like OpenSSL, Opus, Libre, etc.) is compiled as native C/C++ libraries (`.a`/`.so`) via the `libbaresip-android` project.

### Building via WSL (Recommended on Windows):
1. Open WSL terminal in the project root.
2. Ensure prerequisites are installed:
   ```bash
   sudo apt update && sudo apt install -y wget cmake make libtool m4 automake pkg-config unzip build-essential
   ```
3. Navigate to `libbaresip-android`:
   ```bash
   cd libbaresip-android
   ```
4. Configure the Android NDK path in the `Makefile` (pointing to your Android NDK installation, e.g. `/mnt/c/Users/MJ/AppData/Local/Android/Sdk/ndk/...`).
5. Download sources and patch:
   ```bash
   make download-sources
   ```
6. Build libraries for target architectures (`arm64-v8a`, `armeabi-v7a`):
   ```bash
   make all
   ```
   This populates the `distribution/` directory with static libraries required by CMake (`app/src/main/cpp/CMakeLists.txt`).

---

## 3. Building the Android APK

Once native libraries are compiled into `distribution/`:

1. Open the project in Android Studio or build via command line:
   ```cmd
   cd baresip-studio-master
   gradlew assembleDebug
   ```
2. The debug APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`
3. Install onto test Android devices (API 28+):
   ```cmd
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 4. Grandstream UCM6302A PBX Configuration Guide

To connect SWUSIP to the Grandstream UCM6302A PBX:

1. **Log in to UCM6302A Web UI** as administrator.
2. **Create SIP Extension:**
   - Go to **PBX > Basic/Call Routes > Extensions**.
   - Add new SIP Extension (e.g., Extension `1001` for Agent 1).
   - Set a secure SIP Password.
3. **Configure Transport & Network:**
   - Transport: UDP / TCP / TLS (TLS recommended).
   - Enable SIP registration.
4. **Compatible Audio Codecs:**
   - Enable G.711u (PCMU), G.711a (PCMA), Opus, and G.722.
5. **SWUSIP Client Configuration:**
   - Open SWUSIP app on Android device.
   - Go to **Accounts > Add SIP Account**.
   - Enter SIP Server IP/Domain (UCM6302A address), Port (`5060` or `5061`), Username (`1001`), and Password.
   - Verify green registration indicator light.
