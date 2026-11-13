# SWUSIP recovery baseline — 2026-11-13

Initial revision: `ff3d5388e29abc3cc83cb1154575f353cc2f742b`, branch
`feature/swusip-call-centre`. `git ls-remote origin refs/heads/feature/swusip-call-centre`
confirmed the same remote revision (exit 0). Working tree initially clean.

## Repository and references

Git root is the **parent** of the Android project. The project is nested in
`baresip-studio-master/`. Gitlink `libbaresip-android` pins upstream
`bf8448d8db4c3ff0ad3dabdd2d957954fed8ce22`, but `.gitmodules` is nested and
the root mapping is missing. `git submodule status` fails accordingly.
The populated native directory has no independent Git metadata; local Makefile
contains developer-specific overrides. Do not treat those files as a verified checkout.

Read both original reports from Downloads: `SWUSIP_TECHNICAL_AUDIT.md` (31,072 bytes)
and `CODEX_CORRECTION_PLAN.md`. Audit revision was `77ff2e3`. Reviewed history
includes stub introduction `c336f73`, genuine JNI restoration `77ff2e3`, and
ignore update `ff3d538`. The audit's build conclusions still apply; source presence
does not establish a working binary. User directive supersedes report instructions
where they conflict. No CRM or redesign work is authorised before native verification.

Official references reviewed:
- https://github.com/juha-h/baresip-studio
- https://github.com/juha-h/libbaresip-android (Linux NDK, native make targets)

## Toolchain observed

- Windows JDK: Temurin 21.0.12.1.
- Android SDK: installed; configured compile 37 / target 36 / minimum 28.
- NDK `30.0.16248370`: **Windows host only**, `windows-x86_64`.
- SDK CMake/Ninja: CMake `3.31.6` installation present.
- Confirmed Windows CMake 3.31.6 / Ninja 1.12.1; Ubuntu CMake 4.2.3.
- Gradle wrapper properties: 9.6.0; installed distribution present. Windows launcher
  bypasses wrapper with an absolute user path; wrapper jar exists locally but is untracked.
- AGP 9.5.1 / Kotlin 2.4.21 configured. Compatibility/build resolution not yet verified.
- WSL2 Ubuntu available outside sandbox; cmake, make, gcc, autoconf, automake,
  libtoolize, curl, unzip, python3 available. Linux NDK absent at baseline.
- About 65 GB free on C: during inspection. Ubuntu outbound curl to Google timed out;
  Windows curl reached official Linux NDK package successfully.

## First underlying failures

`libbaresip-android/amr/config.log` records exit 77: command
`aarch64-linux-android28-clang++` not found, then `C++ compiler cannot create executables`.
Makefile computed `prebuilt/linux-x86_64` inside a Windows-only NDK. This is a host
toolchain mismatch, before SIP source compilation.

All 45 existing `.a` archives (15 for each of three ABIs) are eight-byte archive
headers with no object members. LLVM `ar t` on arm64 libbaresip returns no members.
Latest available native linker stdout under
`app/build/intermediates/cxx/Debug/303894b6/logs/arm64-v8a/` reports undefined
`bevent_register`, `baresip_message`, and error-limit termination. Empty archives
explain those unresolved symbols; changing link order cannot supply missing code.
Cached CMake compiler tests passed using the Windows NDK; they do not verify the engine.

## Open gates and constraints

Gate 1 is **NOT PASSED**: no genuine APK verified at baseline. Existing APKs must
not be deployed as evidence of SIP capability. Gates 2–6 remain untested.
Public repository requires institutional governance review; visibility unchanged.
Root mapping repair needs a write outside the current writable project root.
No production PBX/network access, extension credentials, or approved test handset
has been supplied. No external exposure or institutional configuration will be changed.

Next: acquire matching Linux NDK, isolate genuine pinned native sources, build arm64,
inspect symbols/ABI, then link and package APK; retain exact commands and exit codes.
