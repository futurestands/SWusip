# Build verification

Gate 1 (genuine native compilation/linking and inspected APK) **PASSED locally**.
Runtime registration and calling are not established. Gate 2 remains **NOT PASSED**.

## Physical-device follow-up from 751e150

The exact verified APK below was installed on the authorised Android 15/API 35
arm64 handset using `adb install -r` (exit 0), then launched (exit 0). The installed
APK checksum matches the local/build evidence. The process, Baresip native executable
mapping and foreground BaresipService were observed; corrected scoped Logcat found
no startup failure during 30 seconds. See `DEVICE_STARTUP_REPORT.md`.
Application/native source and APK were unchanged; the necessary corrections affect
only diagnostic parsing. All **12 Python tests pass**. Previous separate Gradle
build/lint results remain valid. Real-PBX registration and two-way audio are untested.

## Latest localisation/device-preparation milestone

Continued from `a1678b2`. Clean, assembleDebug, lintDebug and testDebugUnitTest were
run in four separate Gradle invocations, each exiting **0**. Lint reports **0 errors,
45 warnings**; no lint rules were disabled. The 401 MissingTranslation errors were
resolved by shipping complete language packs and preserving unfinished upstream
packs for review, as documented in `LOCALISATION_AND_DEVICE_MILESTONE.md`.

Eight Python unit tests pass. Android `testDebugUnitTest` remains **NO-SOURCE**.
All 15 native archives and the new APK pass genuine-engine/ABI/JNI checks.

Current APK: `app/build/outputs/apk/debug/app-debug.apk`, 87,360,786 bytes;
SHA-256 `4ba7c341017b2c4525798b172f128e9b0aa0b71c35718f0d6c77d30a3353b081`.
The package is still `ug.go.mwe.swusip`, label `SWUSIP`, version 585 / 94.1.9.
Evidence: `docs/evidence/localisation/`, with exact commands and individual exits
in `gradle-results.json`. At that localisation milestone no phone was connected;
the subsequent physical-device results are recorded above. Registration and audio
remain untested.

The earlier recovery results below are retained as historical evidence, including
the original combined validation run that exited **1 due to lint**.

## Recovery APK at a1678b2 (historical)

- Path: `app/build/outputs/apk/debug/app-debug.apk` (ignored build output).
- Size: 87,796,706 bytes.
- SHA-256: `75c5b0046d01f6dc6b105d17d8e3c2968dddddc6569f73f4c79cf63c5d1a404a`.
- Package: `ug.go.mwe.swusip`; label: `SWUSIP`; version 585 / 94.1.9.
- APK native entries: `arm64-v8a/libbaresip.so`, `libc++_shared.so` and
  dependency-provided `libandroidx.graphics.path.so`; no other ABIs.
- Native ELF: AArch64. Required register/connect/transfer/start JNI exports present;
  libc++ shared dependency is packaged. `apk-verification.txt`, exit **0**.
- Gradle's real configure/link/package/assemble tasks completed. The unit-test
  task reports **NO-SOURCE**; no unit-test pass count is claimed.
- Separate confirmation command
  `gradlew.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest`
  exits **0** (32s, existing outputs up to date). See
  `gradle-assemble-confirmation.log` and its exit sidecar. APK hash is unchanged.
- No device/AVD available. No registration, audio or startup runtime test performed.

## Lint and regression results

`scripts/build-android.ps1` ran
`gradlew.bat --no-daemon clean :app:clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
Native configure/link, clean debug packaging and `assembleDebug` completed. Lint
then failed: **401 MissingTranslation errors and 100 warnings**, exit 1, after
11m 27s. See `gradle-debug-build.log`, its exit sidecar and `lint-results-debug.txt`.
The errors cover incomplete inherited translations across upstream locales; all
401 are MissingTranslation. Warnings include string formatting, unused resources,
typography, target/version notices and launcher icon issues. No lint baseline or
suppression was added. Localisation remediation is outstanding; lint is not passed.

`python scripts/test_source_integrity.py`: **2 tests passed**, exit 0. These test
the current JNI source and the actual historical fake engine at `c336f73`, read
as Git data without writing or compiling it. This is distinct from Android
`testDebugUnitTest`, which remains NO-SOURCE.

## Baseline evidence

- `git ls-remote origin refs/heads/feature/swusip-call-centre`: exit 0, matches
  `ff3d5388e29abc3cc83cb1154575f353cc2f742b`.
- `llvm-ar t distribution/baresip/lib/arm64-v8a/libbaresip.a`: exit 0 with no members.
- `python scripts/verify_native.py --tools <Windows NDK>/toolchains/llvm/prebuilt/windows-x86_64/bin`:
  exit **1**, rejects all 15 arm64 archives. `baseline-archive-verification.txt`.
- `python scripts/check_sip_source.py`: exit 0, 94 JNI exports, required calls present.
- `gradlew.bat --version`: exit 0, Gradle 9.6.0 / JDK 21. See `gradle-wrapper-version.txt`.
- Locked source resolution/fetch/export: exit 0. See `native-source-*.txt`.
- Complete unskipped native pipeline: **exit 0**. See
  `native-arm64-v8a-build.log` and its `.exit` sidecar. All 15 archives verified,
  including G.722 LLVM bitcode target triples. Required six SIP/Libre functions
  are defined implementations, not merely undefined references.
- Windows-host LLVM verification of the final Linux-built archives also passes;
  `native-windows-verification.txt` records the final SHA-256 values.
- Gradle `--no-daemon help`: exit 0; `gradle-configuration.txt`.
- Negative guard test against preserved legacy x86_64 empty archives: exits 1 as
  expected; `stub-guard-negative.txt` and its exit sidecar. No empty libraries were
  created for testing or used for linking.

Earlier failure logs are retained separately: wrong host NDK, CRLF patch application,
ZRTP policy floor and compiler-cache invalidation. During recovery, editing a running
driver interrupted its reporting footer; the final successful run used unchanged
drivers and no skipped targets. Two native runs did not produce identical libcrypto
hashes; bit-for-bit reproducibility remains unverified and must not be claimed.

## Build commands

Use Linux NDK revision 30.0.16248370 and documented prerequisite packages from the
pinned native builder README. Stage/export as described in `NATIVE_DEPENDENCIES.md`.

```sh
export ANDROID_NDK_ROOT=/path/to/linux/android-ndk-r30
export SWUSIP_NATIVE_SOURCE=/path/to/staged/native-source
bash scripts/build-native.sh arm64-v8a
```

On Windows after verified native build:

```powershell
.\gradlew.bat --no-daemon clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The recovery command is `powershell -File scripts/build-android.ps1`, which runs
Windows LLVM verification first, then
`gradlew.bat --no-daemon clean :app:clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`,
records exact exits and inspects APK identity/native exports. It discovers the
SDK from ANDROID_HOME/ANDROID_SDK_ROOT or ignored local.properties; no developer
path is embedded in the launcher.

Default ABI is arm64 only. Additional ABIs are explicit:
`-PswusipAbis=arm64-v8a,armeabi-v7a,x86_64`, after building and verifying every
selected ABI. Do not enable x86_64 merely to make an incompatible emulator install.

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Inspect APK manifest, package
`ug.go.mwe.swusip`, display label, native ELF machine/dependencies and JNI symbols.
Record SHA-256 and exact task exits; do not commit APKs or signing keys.

## Limits

No unit-test pass may be claimed when Gradle says NO-SOURCE. No registration,
audio, device reliability or PBX interoperability result follows from a successful
build. Keep those results in their respective reports.
