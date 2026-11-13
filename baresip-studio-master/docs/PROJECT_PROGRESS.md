# Recovery progress — 2026-11-13

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
