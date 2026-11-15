# SWUSIP 1.0.6-pilot — import and telephone identity

Package `ug.go.mwe.swusip`, version code **592**. APK:
`artifacts/SWUSIP-1.0.6-pilot-debug.apk`.

SHA-256: `77fd8abf77f75bf74eb02327d91507cf829f37061a7a57106f80d4e106573ac1`.
This is a debug-signed pilot, not a production release.
Implementation commit: `ac47df6da0537baafe3b6da5d1aafd83f9d059c3`.

## Delivered behaviour

- Four required CSV/XLSX fields: customer_name, phone_number, account_number,
  scheme_name. Optional legacy fields and existing encrypted records remain.
  Both blank templates are bundled/documented; older seven-column copies remain.
- Normalised scheme-name lookup, deterministic internal codes for new names and
  explicit collision rejection. No name-only customer merging.
- One versioned Uganda numbering service for import and exact incoming SIP caller
  lookup. Original numbers, canonical values, statuses, corrections and policy
  versions are encrypted separately per association/batch. Invalid/ambiguous rows
  remain in an administrator review list and cannot identify callers.
- Preview includes every row and its proposed correction; duplicate/account
  handling and atomic guarded undo retain pre-existing data. Numeric Excel account
  cells are rejected; literal account text retains leading zeroes.
- Separate administrator-controlled customer call-back formatter. New profiles
  default to disabled; a recorded genuine PBX test reference is required for
  enabling. No profile was enabled or PBX configuration changed in this work.
- Incoming multiple matches offer individual account/profile selection. Lookup
  remains asynchronous; answer/hang-up controls and the native engine are unchanged.

The user-supplied workbook was examined read-only for structure. It has four
recognised headers and 1,208 data rows with numeric account cells. No record values
were copied into fixtures, reports or Git. Lost account zeroes cannot be inferred;
the authorised source must supply the original text account values.

## Storage and the prior 929-second failure

The old run failed with UserNotAuthenticatedException during a read after importing
10,000 rows. Its precise historical stage timings are unavailable. Bounded
[profiling](SWUSIP_DIRECTORY_PROFILING.md) measured per-field Keystore overhead and
separately reproduced an oversized single-row undo-journal cursor failure.

Schema 2 adds immutable encrypted pages (64 KiB/128-entry bounds), encrypted undo
chunks (32 KiB/128-change bounds), phone provenance, rejected-row review and
foreign-key indexes. The existing non-exportable Keystore key, AES-GCM, 300-second
authentication policy, device-unlock requirement and role checks remain. Page AAD
and encrypted entry keys bind page/table/record identities; an authenticated
ordered header binds undo chunks. No plaintext key cache, key export or security
timeout extension was introduced. Plaintext page caches are operation-local.

Migration 1 → 2 is additive and does not rewrite old ciphertext. Legacy oversized
Base64 journals are read through bounded SQL cells. Old large per-field datasets
can retain their original read cost; no automatic repacking was attempted.
The local design/tests are not an independent cryptographic security audit.

## Confirmed checks

The latest result for each of **17 distinct physical-device cases is passed**,
with no remaining failed/skipped automated case. Historical fixture failures and
their successful corrected reruns are retained separately in evidence.

- Final main/test APK assembly; **69 JVM tests**, no failures.
- **14 Python source/relationship/privacy checks**, no failures.
- Android lint: **0 errors, 84 warnings** (existing application warnings remain).
- Native archives, arm64 ELF, dynamic JNI exports and APK identity verification.
  Packaged native SIP library and mandatory notices retain their prior hashes.
- In-place update on authorised Xiaomi 25028RN03A, Android 15/API 35, from
  1.0.5/591 to 1.0.6/592; no uninstall or app-data clear.
- **10 core device tests passed**, including all keypad characters, editing,
  unprovisioned Call feedback without a SIP call, CSV 10,000-row parsing, bundled
  XLSX parsing, numeric/formula/DTD rejection, forged-grant rejection, locked
  repository denial, foreign keys and unknown-migration preservation.
- **Five protected storage tests passed** using normal owner authentication and
  isolated synthetic databases: original/canonical phone preservation; scheme
  resolution and quarantine; cross-page/ciphertext tampering rejection; atomic
  journal-tampering rejection and reversible category fill; additive legacy
  migration/unchanged ciphertext; oversized legacy journal; new paged bulk cycle.
- Customer/scheme navigation and the corrected small workflow regression passed.
  The small workflow checks exercise
  duplicate-only undo, searches, multiple accounts/shared phones, notes, outcomes,
  follow-ups, protected edits, new Excel template parsing/import and simulated
  SIP-identity matching. See final case statuses in the evidence JSON.
- Startup observation verified the installed APK hash, mapped native library and
  foreground service. Scoped Logcat recorded no crash, native-loading, security
  exception or foreground-service failure during the ten-second window.

The first migration fixture omitted legacy `schemeId`; the first small workflow
fixture incorrectly put seven values under the new four-column header. Both
failures are retained in evidence, identified as fixture defects and corrected.
Neither justified relaxing production validation. The large cycle was not repeated
after its success.

### Measured encrypted operations

The 1,000-row cycle measured import 5.004 s, read 1.169 s, exact loaded caller lookup
0.006 s, account search 0.138 s and undo 3.272 s. The subsequent single 10,000-row
cycle measured import **46.972 s**, read **10.814 s**, loaded exact caller lookup
**0.058 s**, account search **0.573 s**, undo **34.876 s**. Both completed under
the unchanged authentication policy. Input shape: distinct synthetic customers
and accounts sharing one phone and scheme. These are device measurements for that
shape, not a guarantee for every workbook, existing dataset or phone. Full lookup
of an unloaded 10,000-row directory also incurs its read cost.

## Remaining acceptance limits

- Genuine Grandstream incoming customer identification, registration, audio,
  background calls and call controls need approved live SIP tests. Simulated SIP
  identity lookup is not a successful call. No MTN call was attempted.
- The earlier manual Android Go picker check passed for both three-row CSV/XLSX
  preview/cancellation (owner-reported). New four-column picker fixtures are supplied
  separately; their manual confirmation is recorded with the final evidence.
  The known picker automation failure does not establish an application defect.
- After automated testing, the owner reported an import failure without yet
  identifying the file, exact message or stage. This remains unresolved pending
  those details. The directory is local and does not require a hosted database.
  Numeric account cells in the referenced workbook are a confirmed validation
  issue, but are not established as the cause of that reported failure. A bounded
  app-UID Logcat check found zero listed crash, Keystore-authentication, SQLite,
  JSON, native-loading or out-of-memory error markers in the last 500 entries;
  caught errors need not appear there. No raw/customer logs were published.
- The phone has an administrator grant. Authenticated ordinary-agent UI denial
  has not been re-tested by changing that device's assigned role; locked denial
  and production role gates remain. No authentication bypass was used.
- SIP credentials still use app-private native configuration under Android device
  encryption; a reviewed vault migration and production signing remain release
  prerequisites. Existing lint warnings and long-term background reliability
  require follow-up. No SMS/MMS removal or dependency replacement was performed.

Use the [import guide](SWUSIP_IMPORT_GUIDE.md), [administrator guide](SWUSIP_ADMIN_GUIDE.md)
and existing [SIP checklist](SWUSIP_UCM_TEST_CHECKLIST.md). Redacted evidence is under
`docs/evidence/import-v2/`; raw startup logs stay in ignored local build storage.
