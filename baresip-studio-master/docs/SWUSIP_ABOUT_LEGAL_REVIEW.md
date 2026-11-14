# Device acceptance update: 1.0.5-pilot

The complete public About/legal instrumentation test now passes on the authorised
25028RN03A handset, including licence search and portrait/landscape navigation.
Bounded accessibility waits resolved the earlier harness assertions. Required
bundled notices and the native SIP library are byte-identical to the baseline.
Five public screenshots were visually reviewed in
[current evidence](evidence/customer-workflows/verification.json):
[About](evidence/customer-workflows/about-portrait.png),
[Legal information](evidence/customer-workflows/legal-information-portrait.png),
[licences](evidence/customer-workflows/open-source-licences-portrait.png),
[About landscape](evidence/customer-workflows/about-landscape.png), and
[Legal landscape](evidence/customer-workflows/legal-information-landscape.png).
The older draft/test failures below are retained as history; current results are
in [Customer workflows](SWUSIP_CUSTOMER_WORKFLOWS.md).

# SWUSIP About and legal information — review build

Baseline: `677f938` (the preceding Android continuation was committed and pushed
before this separate design task). Build: **1.0.4-pilot / 590**.

**Status: implementation and host checks complete; device visual acceptance and
screenshots unfinished. This is a review draft, not a passed screen-test milestone.**

## Presentation and access

About now leads with SWUSIP, South Western Umbrella Call Centre, South Western
Umbrella of Water and Sanitation, and Ministry of Water and Environment, Uganda.
It uses a white surface, institutional blue headings, readable wrapping text and
consistent spacing. No official logos were invented or downloaded; none were supplied.

A plain secondary-colour **Legal information** entry sits at the bottom of the
scrolling About content. It opens a neutral information page, then **Open-source
licences**. These routes have no agent, administrator or developer restriction.
The legacy `licences` route remains available. Source/repository links and upstream
attribution are confined to the legal notices, not ordinary About content.

The licence page keeps the entire notice text visible, selectable and searchable;
search highlights and moves to matching text without filtering out conditions or
disclaimers. It reads bundled assets locally and needs no server connection.

## Preservation checks

- The packaged third-party notice asset is **byte-identical** to the 1.0.3 APK:
  **146,505 bytes**, SHA-256
  `d64218b789a63ed2634483f7ed30472cab4c9c0ea51f4849b15b225c3bc6829b`.
  Copyright notices, licence terms, disclaimers and original attribution remain.
- The genuine native library is unchanged, SHA-256
  `108931f7a0780b4009c9548ecd5824a6ef26e266466870aa9456c50c3bc19fed`.
- No native source, telephony handler, SIP provisioning, customer schema, stored
  data or application-setting behaviour was changed by this task.
- The blue/white theme is scoped to information pages. Test rotation is restored
  in `finally`; screenshot restrictions are never disabled.

## APK and measured checks

APK: `artifacts/SWUSIP-1.0.4-pilot-debug.apk` in the Android project.
SHA-256: `7e51e912a3f7182396f33cbd17cb545c685dd0a286ebeecaacd2bdeb5e889c79`.

- Application/test APK build: **PASS**.
- **35 JVM tests passed**, zero failures/errors.
- Lint: **PASS**, zero errors/fatal issues, **84 warnings**; no rules disabled.
- APK identity, arm64 ELF/JNI exports and signature: **PASS**.
- Application and test APK were installed successfully with `adb install -r` on
  the explicitly selected handset, preserving data. No uninstall/clear occurred.
- The initial combined device run passed the **10 existing regressions** and
  failed the new About screen test while trying to reach the About entry.
- Subsequent screen-test harness attempts exposed foreground/window and scrolling
  handling problems. A cleanup exception also masked an earlier failure; that
  cleanup was removed, window waits and normal touch swipes were added, and
  screenshot checks now require the expected public SWUSIP page before and after
  capture. These harness changes build and pass lint.
- **The final corrected screen test has not run:** before its installation,
  `192.168.1.112:46723` disappeared. Reconnection to the same endpoint timed out
  with Windows socket error 10060. No duplicate ADB entry was substituted.
- **No verified screenshot files are available or included.** Earlier claims of
  capture success were corrected after the expected files were found absent.
  Unrelated/unverified captures are excluded.

The passing core regressions do not substitute for the unfinished About/legal
navigation, search and portrait/landscape visual acceptance. Do not count the new
screen test as passed. Evidence is in [about-legal](evidence/about-legal/).

## Resume device acceptance

Obtain the current Wireless debugging IP:port from the owner, retain explicit
`adb -s IP:port` targeting, and keep SWUSIP foreground on the unlocked idle phone.
Install the current test APK without clearing data and run `AboutLegalDeviceTest`
with raw instrumentation statuses. Diagnose the original result now that cleanup
cannot hide it. Verify every saved PNG shows the intended public page before
including About, Legal information and licence-page screenshots in the review.
Repeat the dialler regressions after the screen test passes.

No phone PIN/password is needed in chat and public legal pages require no customer
workspace session. Previous authenticated-directory, credential-storage and genuine
PBX release gates remain documented in [the Android continuation](SWUSIP_ANDROID_CONTINUATION.md).
