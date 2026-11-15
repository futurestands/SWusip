# Authenticated directory verification after 339560a

Baseline application: SWUSIP 1.0.5-pilot / 591, package `ug.go.mwe.swusip`.
The application APK, production sources, native SIP engine and directory schema
are unchanged. Only instrumentation coverage and verification documentation change.
The phone is model 25028RN03A, Android 15/API 35. Every ADB operation explicitly
targets the owner-confirmed wireless endpoint. No PBX/network settings change.

## Authentication and test isolation

Android instrumentation restarts the application, clearing its five-minute
in-memory workspace session. The owner confirmed Android credentials on the
handset; no session injection, role override, fake grant or PIN collection is used.
The tests verified a genuine administrator session before protected operations.
Data tests use randomly named `swusip-synthetic-*.db` databases and delete those
isolated databases in `finally`; they do not import into or clear the existing
customer directory. The existing authenticated Keystore key and access checks apply.

The first run reached the large import after passing the small protected CSV,
association, note and follow-up assertions. It failed at the large import with
Android Keystore `Device locked`. A temporary charging stay-awake setting could
not prevent sleep while the phone was unplugged. The expanded harness now uses
`FLAG_KEEP_SCREEN_ON` only on its test activity after authentication, and restores
that flag after the test. Screen lock, secure-window flags and authentication
requirements remain intact. Owner reauthentication may still be required when
the normal security window expires.

## Confirmed small-workflow assertions

The expanded run emitted a checkpoint only after all of these assertions passed:

- CSV preview/import and duplicate skipping, including reversal of duplicate-only
  and additive-update batches without discarding the original records.
- Name, formatted phone, account number, scheme code and scheme-name searches.
- Multiple customers sharing one phone and explicit multiple-account associations.
- No phone leakage to an unrelated associated account.
- Encrypted notes, callback outcome, pending follow-up, completion identity/time,
  and preservation of original note author/time.
- Guarded undo refusal after later notes/associations; customer data remains.
- Authorised customer name/location editing in the isolated database.
- XLSX import using a populated copy of the actual supplied template; text account
  `0000456` retains its zeroes, preview passes, and the imported record is found.
- Synthetic Uganda SIP-URI lookup identifies the expected customer, account and
  scheme; undo removes that synthetic spreadsheet batch.
- Customer payloads do not contain the synthetic name in plaintext.

These are protected repository tests, not proof of document-picker interaction
or a full customer-profile editing session through the visual interface.
The separated small-workflow test subsequently completed with a genuine test
pass, rather than only a checkpoint inside the failed large-batch test.

## Bulk performance and remaining checks

The expanded encrypted run failed after **929.325 seconds**. Its 10,000-record
accepted-count assertion passed, but a subsequent directory read failed inside
Keystore decryption with `UserNotAuthenticatedException`. The full read/search/undo
cycle did not pass. This is a bulk performance/authentication-lifetime release
issue, not a successful 10,000-record workflow. Exact per-stage timings were not
available in that attempt; the total includes normal owner-authentication wait.
Do not count the small checkpoint or historical CSV parsing as full bulk acceptance.
The harness now reports stage timings and avoids decrypting the same large
directory twice solely for assertions. No security limit is relaxed.

Protected navigation reached Customers, Scheme Directory, back navigation and
administrator import controls. The combined picker check first failed because
the phone's actual picker is `com.google.android.go.documentsui`, then encountered
file rows without clickable accessibility ancestors. A fresh local layout confirmed
that both synthetic files were present. Test-only fixes recognise that verified
package and allow a bounded touch only on an exact synthetic filename in the
foreground system picker. Generic navigation still requires clickable controls.
No application import
code is altered. The picker test never confirms an import; any preview it reaches
is cancelled. Final statuses are in `evidence/authenticated-directory/`.

The separated **authenticated navigation test passed**. The final picker-preview
attempt still failed to locate the synthetic fixture during automation, despite
the files being present in a separate fresh layout check. Its end-to-end CSV/XLSX
picker/preview/cancel flow is **not verified**. This is an unresolved device UI/test
integration issue; no application parser failure was established. The test remains
opt-in and its failure is retained rather than reported as a pass. No confirmed
import into the existing directory occurred.

## Final checks and delivery state

- Thirteen distinct physical-device tests passed across the final applicable runs:
  the protected small-data test, protected navigation, and all eleven standard
  parser/security/migration/dialler/About regressions. Standard regressions had
  no skipped tests. Earlier failed attempts are retained in the evidence folder.
- The bulk cycle and picker-preview workflow remain failed/unresolved; they are
  not included in the passed count. The newly separated bulk performance method
  was compiled but not repeated after the 929-second failure.
- Test APK assembly and lint succeeded: 0 errors, 85 warnings. The unit-test task
  succeeded up-to-date with 47 existing passing results. Fourteen source-integrity,
  relationship, resource and log-privacy Python checks were executed and passed.
- Scoped final Logcat: 0 fatal crashes, ANRs, native-loading errors, security
  exceptions or Keystore-authentication errors in that final window. This excludes
  the earlier recorded bulk authentication failures; raw Logcat remains private.
- Final installed-APK readback matches SHA-256
  `bc19bea5fd5897dc28a95497084fde6938ae6f482110fc52f8edcb3ee97dc4be`.
  The genuine native library is mapped and the foreground service is running.
  A 10-second startup observation is not a genuine SIP or reliability soak test.
- Original charging stay-awake setting 0 was restored and read back. Test-only
  keep-screen-on and accessibility flags are restored by test cleanup.
- App APK remains `artifacts/SWUSIP-1.0.5-pilot-debug.apk`; no production binary,
  dependency, credential store, schema or existing customer data was replaced.

To reproduce small data/navigation/picker tests, select their individual methods
in `AuthenticatedDirectoryDeviceTest` and pass `-e swusipAuthenticated true`; the
owner must confirm Android credentials normally. The separate large performance
method additionally requires `-e swusipLargeBatch true`. Do not report assumption
skips as passes. Picker fixtures are the synthetic CSV/XLSX files in Downloads.
Earlier attempt logs use the pre-split test class, so their test-file line numbers
differ from the final coverage commit. Production directory line numbers remain
unchanged. The evidence JSON identifies the final test coverage commit.

Next steps: profile the slow encrypted bulk read/import stages and retest under
the existing authentication policy; manually verify picker selection/preview/cancel
to distinguish provider behaviour from automation; perform a visual synthetic
profile-editing review; then use an approved internal Grandstream incoming call
to verify the live caller panel and responsive answer/reject/active-call controls.

## Actual incoming call boundary

Source review confirms the native incoming event creates a real `Call` from its
SIP peer URI, and MainScreen passes `call.peerUri` to `CallCustomerPanel`. Lookup
runs on `Dispatchers.IO`; call controls render separately. Locked workspaces hide
customer details and multiple matches have no arbitrarily selected account.
The authenticated synthetic lookup verifies directory matching only. Actual
incoming presentation, audio, call controls, background notifications and profile
access during a live call still require an approved genuine Grandstream call.
