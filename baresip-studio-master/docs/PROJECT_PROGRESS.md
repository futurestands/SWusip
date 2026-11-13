# Recovery progress — 2026-11-13

## Latest milestone: real physical-device startup (from 751e150)

The project lead's authorised wireless ADB phone is accessible: model 25028RN03A,
Android 15/API 35, arm64-v8a. The verified SWUSIP 94.1.9 (585) APK installed with
data-preserving `-r` and launched successfully. Installed/local SHA-256 values match.
Baresip executable mapping and its active foreground service were verified, with
no detected startup/JNI/loading/foreground/security failure in a 30-second scoped
Logcat observation. Microphone/notification/Bluetooth permissions were granted.

The test harness was corrected for padded Logcat epoch timestamps and Android's
direct APK native mappings; incomplete native/service evidence can no longer pass.
Twelve Python tests pass. App/native code and APK are unchanged; no new Gradle build
was needed. See `DEVICE_STARTUP_REPORT.md` for exact hash, results and limitations.
Raw device identifiers/logs remain private and ignored. Gate 2 is **NOT PASSED**.

Next: administrator-approved non-production SIP test credentials entered privately
on the phone, a second approved endpoint/extension, then actual registration and
two-way internal audio verification. No production PBX/trunk/network change was made.

## Previous milestone: localisation and device preparation (from a1678b2)

All 401 MissingTranslation errors are resolved for the supported pilot languages:
default English plus complete Czech, Swedish and Simplified Chinese packs.
The 23 unfinished upstream files remain unchanged in `translations/pending/`.
No human-facing defaults were marked non-translatable and no lint checks were disabled.

Separate clean, assembleDebug, lintDebug and Android unit-task invocations each
exit 0. Lint: 0 errors / 45 warnings. Android unit tests: NO-SOURCE; eight Python
regression/privacy tests pass. Fresh APK and all 15 native archives are verified;
see `LOCALISATION_AND_DEVICE_MILESTONE.md` for commands, hash and evidence.
Resource correction commit: `9d80a14`; device/CI/report changes follow separately.

ADB rechecks and the prepared device harness detect no physical handset, so
install/launch/Logcat/native-mapping/foreground-service observations are blocked.
`PHYSICAL_DEVICE_TEST_GUIDE.md` gives exact USB setup and test commands.
`SIP_INTEROP_REPORT.md` now defines the two-approved-extension internal-call test.
Gate 2 remains **NOT PASSED**. No live PBX, trunks, production extensions, VPN or
external calling were configured. Commit/push is authorised to the existing branch.

Next: connect the authorised arm64 phone; execute installation/startup tests; obtain
two approved non-production UCM6302A extensions privately; verify registration and
two-way internal audio. Review remaining lint warnings and security findings before pilot rollout.

## Initial recovery observations at a1678b2

Baseline branch `feature/swusip-call-centre`, commit `ff3d538` (remote verified).
Recovery implementation commit: `a788536c7ded458105e489350dad268f9aab61f9`.
The project lead explicitly authorised GitHub publication on 2026-11-13. This
report and sanitised evidence are maintained in the follow-up documentation commit.
No production deployment was performed.
The remote currently has only this branch and it is also the default branch; a PR
needs a distinct review branch/base. No protected/default branch change was made.

Completed: read both audits and available native logs; verified branch; identified
Windows/Linux NDK mismatch and 45 empty archives; acquired exact Linux NDK; fetched
13 locked upstream source commits; repaired wrapper launcher and local wrapper JAR;
added source/archive/ABI/symbol guards; restricted default build to arm64; repaired
repository-root submodule mapping. Recovered wrapper version check passes.

Newly discovered: Windows `core.autocrlf=true` converts even `git archive` exports.
First staging attempt failed to apply the upstream G.722.1 patch because of CRLF.
Exports now explicitly use `-c core.autocrlf=false`. Subsequent fixes let the NDK
select stable absolute compiler paths and set CMake's compatibility policy floor
for inherited ZRTP. The complete unskipped arm64 pipeline now exits **0**.
All 15 archives pass object/LTO target-triple, ABI and required-symbol checks.
Original local sources are preserved under `.native-build/original-local-native`.
The genuine submodule is initialised at the unchanged gitlink revision.

G.722 contains genuine LLVM bitcode; the verifier uses llvm-dis to validate its
Android arm64 target instead of incorrectly treating a missing ELF header as failure.
Thirty unused legacy empty archives for other ABIs were quarantined in the ignored
recovery cache, leaving only genuine arm64 archives in distribution.

Wrapper version and project `help` configuration checks both exit 0. The genuine
native library linked and the clean debug APK was generated and independently
inspected (exit 0); see `BUILD_VERIFICATION.md` for its hash and package details.
The combined clean build command exited 1 after assembly because lint found
401 inherited MissingTranslation errors and 100 warnings. No checks were suppressed.
The Android unit-test task reports NO-SOURCE; two Python source-integrity regression
tests pass, including rejection of the actual historical fake engine.
Separate offline assembly/unit-task confirmation exits 0 in 32s, with APK hash
unchanged. This does not override the recorded lint failure.
Gitleaks history/working-tree scans exit 0;
OSV native commit queries return no matching advisories (limited coverage).

Gate 1: **PASSED locally** (genuine compile, link, APK generation and inspection).
Gates 2–6: unverified. Blockers for later gates: approved test extensions/devices,
security corrections, handset measurements and institutional deployment decisions.
No CRM, visual redesign, live PBX, public exposure or paid service changes made.

Changed files: repository-root `.gitmodules` and `.github/workflows/`; Android
wrapper JAR/launchers and POSIX executable bit; `.gitignore`/`.gitattributes`;
`app/build.gradle.kts`; native `CMakeLists.txt`; `BUILD_INSTRUCTIONS.md`; build,
verification, source-lock, scanning and regression scripts; the eight required
project documents, draft PR text and sanitised evidence. The nested `.gitmodules`
was replaced by the actual root mapping. The SIP JNI implementation was preserved.

Next: resolve localisation scope/findings, obtain an authorised arm64 handset and
two non-production test extensions out of band, then test startup/registration/audio.
Commit/push authorisation was received for `feature/swusip-call-centre`. The PR
text remains a draft; no separate PR/review branch has been created. Verify the
published revision with `git ls-remote origin refs/heads/feature/swusip-call-centre`.
