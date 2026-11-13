# Restore genuine arm64 SIP builds and reject empty native archives

The restored JNI source previously linked against eight-byte placeholder archives,
while the WSL dependency build selected Linux compilers inside a Windows-only NDK.
SWUSIP now builds the pinned upstream SIP engine with a matching Linux NDK and links
its genuine arm64 libraries into the Android client. Empty archives, wrong ABIs and
missing core SIP implementations fail verification before packaging.

The change repairs the root submodule mapping and wrapper portability, locks all
13 native dependency commits, preserves upstream patches and licences, selects
arm64 by default and supplies the required C++ shared runtime. Recovery scripts,
baseline/status documents, native evidence and source/secret/dependency CI checks
are included. Branding and `ug.go.mwe.swusip` remain intact.

Validation:
- Complete unskipped Linux native build and 15-archive symbol/ABI/LTO checks: exit 0.
- Windows LLVM verification of the resulting archives: exit 0.
- Empty-archive negative check: correctly rejects legacy placeholders with exit 1.
- Gradle wrapper and project configuration: exit 0.
- Genuine APK identity, arm64 native dependencies and JNI exports: exit 0;
  SHA-256 `75c5b0046d01f6dc6b105d17d8e3c2968dddddc6569f73f4c79cf63c5d1a404a`.
- Clean debug packaging completed; combined validation exits 1 due to 401 inherited
  MissingTranslation lint errors and 100 warnings, with no suppression/baseline.
- Separate offline `assembleDebug testDebugUnitTest` confirmation: exit 0, APK unchanged.
- Two Python native-source regression tests pass. Android unit-test task: NO-SOURCE.
- Gitleaks history and prospective tree: zero findings. OSV public native commit
  query: zero matching advisories, with limited coverage explicitly documented.

No PBX calling or handset result is claimed. Android unit-test sources are absent. Security
hardening, runtime reliability, codec licensing review and the development OpenSSL
snapshot remain release constraints. No live PBX/network setting was modified.

Publication status: draft PR text only. The project lead authorised committing
and pushing the recovery directly to `feature/swusip-call-centre` on 2026-11-13.
The remote has only `feature/swusip-call-centre`, which is also its default branch;
a distinct review head is required to open a PR against it. No PR or new branch
has been created. Implementation commit: `a788536`; validation evidence follows
in a separate documentation commit. Remote publication is verified independently.
