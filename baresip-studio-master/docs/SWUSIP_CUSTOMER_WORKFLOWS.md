# Customer workflows: 1.0.5-pilot

**Authenticated follow-up:** small protected CSV/XLSX workflows now pass; the full
10,000-record cycle failed on expired Keystore authentication. See the
[current authenticated report](SWUSIP_AUTHENTICATED_DIRECTORY_TESTS.md). The older
owner-authentication-pending statements below describe the initial delivery run.

## Priorities and changes

The user-confirmed dialler and genuine native SIP engine are unchanged. This is
not fresh PBX verification. Shared incoming telephone matches previously displayed
the first account alongside a multiple-match warning; now no account is selected
and the agent is asked to review the matching customers. Lookup remains asynchronous.
Partial formatted telephone searches now complement name, account and scheme search.

Agent-recorded outcomes and local follow-ups extend existing encrypted interaction
notes. Legacy notes retain their author, time and text and remain unclassified.
Administrator controls now react to authorisation loss. Directory-loading errors
are shown explicitly rather than implying an empty directory.

No dependency, database schema, encryption key, dialler or native changes are
required. Optional fields use the existing encrypted note payload. Import retains
its 10,000-row/file-size limits, validation, preview, explicit authorised updates
and guarded latest-batch undo. Existing CSV/XLSX templates remain applicable.

## Agent instructions

Open a customer profile, enter a note, choose an outcome if known and select
follow-up when needed. Callback required automatically requires follow-up. Save
the note. Outcomes are manually recorded, not inferred from SIP events. Use the
Customers follow-up filter to find pending work on this phone. Mark an interaction
complete after handling it; the original note remains and completion records the
completing identity and time. Unknown future statuses display as unavailable.
There is no shared queue, automatic reminder or central CRM service.

For shared incoming numbers, confirm the caller's account and scheme before
selecting a profile. Caller ID is not proof of authority. Call controls remain
independent of directory lookup. Locked workspaces expose no customer details.

## Security and remaining acceptance gates

Repository role checks and authenticated Keystore access remain enforced. Agents
may record interactions; imports, exports, batch undo and master-record edits
remain administrator-only. No authentication bypass was introduced; backup stays
disabled. SIP credentials may still persist in the native account file, protected
by Android sandbox/file encryption rather than a separate Keystore vault. A reviewed
credential migration and genuine SIP regression remain production release gates.

The old ADB endpoint was unavailable. The owner supplied and confirmed a replacement
endpoint; explicit `-s` targeting connected to model 25028RN03A, Android 15/API 35.
The existing 1.0.4 installation was updated with `install -r` to 1.0.5, without
uninstall or data clearing. Installed APK hash was read back and matched the build.
The 10-second startup observation found native-library and foreground-service
execution with zero scoped crash/native-loading/service/security failures.

Nine current physical tests passed: Excel text/numeric/formula handling, supplied
template parsing, declaration rejection, forged-authorisation denial, database
reopen/unknown-migration preservation, 10,000-row CSV parsing, and locked-workspace
repository denial. The initial dialler test skipped because the handset was locked;
the runner's `OK (10 tests)` is not ten passes. Lint completed with 0 errors and 85
warnings; 47 JVM and 14 Python tests passed. Native archive and APK checks passed.

After owner unlock, the keypad/editing/unprovisioned-call-feedback regression passed
without placing a call. The About/legal test passed navigation, licence search and
portrait/landscape checks. Initial UI assertions failed; bounded waits for
accessibility updates resolved them without production dialler changes. All failed
attempts remain in the evidence folder. Final result: **11 distinct device tests
passed, one authenticated test skipped**, no remaining failure in these final runs.
Five public screenshots were retrieved and visually inspected: About and Legal
information in both orientations, and the licences page in portrait.

The protected test waited 90 seconds without an owner-confirmed administrator
session and skipped; its status messages are not test passes. Authenticated
import/search/profile/undo, encrypted 10,000-record device import performance and
interaction persistence remain pending. Parsing is not importing. A fresh-database
migration test does not prove all existing encrypted customer data upgrades.
Next owner action: open Customers, select Unlock institutional workspace, confirm
normally on the handset and verify that an authorised administrator session is
shown. Do not send the phone PIN or provision credentials in chat. If enrolment
is absent/expired, follow the existing administrator pilot-grant procedure.

At the owner's request, the charging phone was temporarily kept awake. Its original
stay-awake setting (0) was restored and read back; no security lock was disabled.

Actual registration, calls, audio, hold, transfer, background calls and live caller
identification require approved Grandstream tests. MTN calling is a separate test.
No PBX, trunk or network changes were made. Shared CRM, supervisor dashboards and
synchronisation need an authorised backend; no functioning server is claimed.

Host results and APK checksum: `evidence/customer-workflows/verification.json`.
