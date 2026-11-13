# Localisation and physical-device preparation

Starting revision: `a1678b222573d6bc790eee2b25769cfa4ffb769e`, branch
`feature/swusip-call-centre`. Gate 1 remains locally verified; Gate 2 is **NOT PASSED**.
Verified resource correction commit: `9d80a14`. Device preparation, CI and evidence
are recorded in the follow-up commit. Publication is authorised to this same branch.

## Resource correction

All 401 default human-facing strings remain translatable. The original errors were
caused by incomplete upstream packs across 26 locales, including seven empty files.
The pilot defaults to English and retains the three resource-complete upstream
packs (Czech, Swedish, Simplified Chinese). The 23 unfinished packs are preserved
unchanged in `translations/pending/` for completion and language review. On unsupported
device languages Android falls back to the default English UI.

The project lead was asked for preferred pilot languages; in the absence of a reply
this conservative scope was used. No translation was invented, copied from English
to pretend completion, marked non-translatable merely to hide lint, or globally
suppressed. Complete resource coverage does not mean native-speaker review passed.
See `translations/README.md` for restoring a reviewed language pack.

Regression checks verify complete active packs, ordinary UI translation eligibility,
and preservation of archived upstream file contents against `a1678b2`. Together
with the genuine JNI guard and exact-UID device privacy checks, **eight Python
unit tests pass**. The Android unit task
must be reported independently; it currently has no test sources.

## Build evidence

`scripts/validate-android.ps1` runs clean, assembleDebug, lintDebug and
testDebugUnitTest as separate Gradle invocations. Each command/log/exit is recorded
under `docs/evidence/localisation/`; `gradle-results.json` is authoritative.
Final independent invocation results (all using `--no-daemon --offline`):

- `clean :app:clean`: exit **0**, 31s.
- `:app:assembleDebug`: exit **0**, 2m 24s (fresh output after clean).
- `:app:lintDebug`: exit **0**, 4m 16s; **0 errors, 45 warnings**.
- `:app:testDebugUnitTest`: exit **0**, 29s; **NO-SOURCE** (no Android unit tests executed).

All **401 MissingTranslation errors are resolved** for the supported pilot resource
set. The 45 remaining warnings are 30 UnusedResources, 7 StringFormatCount,
4 TypographyDashes, 2 MonochromeLauncherIcon, 1 OldTargetApi and 1 TypographyEllipsis.
No lint severity, baseline, suppression or global disable was changed. Offline
validation uses already-resolved dependencies; it is not a dependency-update check.
The first correction attempt retained empty locale directories after preserving
their XML; lint still counted those directories as locale declarations. That
attempt's four separate task results and lint failure are retained in `attempt1/`.
The directories were removed only after verifying they were empty and within
the app resource root. A regression test now rejects empty active locale folders.

The freshly assembled APK has been independently verified: package
`ug.go.mwe.swusip`, label `SWUSIP`, AArch64 genuine `libbaresip.so`, required JNI
register/connect/transfer/start exports and packaged `libc++_shared.so`.
All 15 native archives still pass object/ABI/symbol checks.
The final prospective-file Gitleaks scan exits 0 with no leaks; raw device logs
and serials remain excluded from the repository.

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 87,360,786 bytes
- SHA-256: `4ba7c341017b2c4525798b172f128e9b0aa0b71c35718f0d6c77d30a3353b081`
- Native verification and APK inspection: exit 0, evidence in the milestone directory.

## Device and PBX blockers

At this earlier milestone ADB detected no physical handset, so no install, launch,
native loading or foreground-service result was claimed. The later authorised
wireless physical-device test now passes those startup observations; see
`DEVICE_STARTUP_REPORT.md`. For repeat testing, follow
`PHYSICAL_DEVICE_TEST_GUIDE.md`, connect the authorised phone and accept its RSA
prompt, then rerun the prepared test harness. Keep raw Logcat and device serials private.

Actual-PBX registration and two-way audio are untested. Two approved non-production
UCM6302A extensions and a second endpoint/tester are required, with credentials
delivered out of band. `SIP_INTEROP_REPORT.md` now provides the next milestone's
procedure and acceptance criteria. No production extensions, trunks, external calls,
VPN or institutional network controls were changed.
