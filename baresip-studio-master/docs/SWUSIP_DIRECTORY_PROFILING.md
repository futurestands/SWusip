# SWUSIP 1.0.5 bounded directory investigation

Baseline: d2a74a0, unchanged production APK 1.0.5-pilot / 591. The owner confirmed
normal Android authentication. No 10,000-record encrypted batch was repeated.
No existing customer data, SIP configuration, native code or PBX settings changed.

## Picker result

The owner manually selected both synthetic CSV and XLSX files on the handset.
Both displayed **Review 3 rows before import**; Cancel returned to Customers.
This is owner-reported manual evidence, not a passing automation trace. It confirms
that selection/preview/cancellation work for these files on this installation;
the previous Android Go picker-selector failure was an automation limitation for
this workflow. No import into the existing directory was confirmed.

## Measurements

An opt-in test used isolated 5/20-row databases and a separate temporary Keystore
key with the same AES-GCM, 300-second authentication and unlocked-device policy.
The key was deleted and temporary display flags restored. The test passed in
15.152 seconds including owner interaction. Metrics contain no record contents.

For 20 rows: CSV parse 8.1 ms; protected import 921.1 ms; protected read 553.0 ms;
search of the loaded records 10.7 ms; SQL join alone 1.1 ms; guarded undo 52.4 ms.
Mean single small encryption init/final: 7.6/4.7 ms; decryption init/final: 6.4/4.7 ms.
The 256-KiB encryption/decryption final operations took 55.9/57.8 ms. These small
measurements identify per-record cryptography/decryption as a dominant read cost;
they are not measured 10,000-record durations or an exact breakdown of the old run.
Binder counters returned zero throughout and are not useful for attribution.

For the earlier 10,000-row input shape, source-derived counts are 20,003 import
cipher initialisations, 20,002 per full read, 10,001 assigned-identity lookups and
50,002 journal entries. The test formerly read the full directory twice. The
host journal model is 7,390,294 plaintext bytes, approximately 9,853,764 bytes after
encryption/Base64. It uses synthetic IDs/actor; it is not the historical blob.

The phone reports `config_cursorWindowSize=2048` KiB. A separate in-memory SQLite
probe rejected a single 2,097,216-byte synthetic text row with
`SQLiteBlobTooBigException`. The current undo query selects a whole journal payload
in one row, so its size is an independent confirmed design limit. This did **not**
cause the historical 929-second exception, which occurred earlier during customer
decryption. Android's [CursorWindow documentation](https://developer.android.com/reference/android/database/CursorWindow)
describes the bounded allocation; the [platform implementation](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/database/CursorWindow.java)
reads the configured size in KiB.

The old run's confirmed failure remains `UserNotAuthenticatedException` during a
directory read, after its accepted-count assertion. Its exact stage durations are
unknown. Slow per-record processing is consistent with exhausting the normal
authentication window; no authentication period was extended or disabled.

Host SQLite query plans also show a `customer_account` scan when checking account
deletion foreign keys. This is a secondary undo scaling risk, not the historical
read failure. Host timings are explicitly not Android performance evidence.

## Engineering direction

Use immutable bounded encrypted pages for new records and bounded encrypted undo
journal chunks under the existing authenticated Keystore key. Bind page contents
to table/record IDs and chunks to an authenticated ordered header; preserve legacy
payload reads and journal reads. Add indexes for foreign-key reversal checks,
cache assigned identity per authorised batch, and recheck authorisation immediately
before commit. Do not introduce plaintext directory data or cached plaintext keys.
Test additive migration, tampering, lock/expiry denial and guarded undo on isolated
synthetic databases before installing an updated production APK.

Evidence: `evidence/directory-profiling/device-profile.json`, the raw bounded-test
statuses, and `structure.json`. Full bulk acceptance was outstanding at this
historical milestone. The subsequent [1.0.6 delivery](SWUSIP_IMPORT_V2_DELIVERY.md)
records the new paged implementation and successful 10,000-row device cycle.
The legacy-only host model now refuses to apply its old operation counts to v2.
