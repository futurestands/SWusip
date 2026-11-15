# Customer import — 1.0.6 pilot

Use Settings/Customers → Manage directory with a normally authenticated
administrator workspace. Ordinary agents can search authorised records but cannot
import, review rejected source rows, change telephone policy or undo a batch.

## Four required columns

`customer_name,phone_number,account_number,scheme_name`

Download [Excel](templates/swusip-customers.xlsx) or
[CSV](templates/swusip-customers.csv). The Excel input cells are formatted as Text.
Enter account numbers as literal text, including every leading zero. Numeric Excel
account cells are rejected: changing their format after entry cannot recover lost
zeroes. Obtain the original account value before re-entering it. Formula cells are
rejected. Use the first worksheet; CSV must be UTF-8.

The existing seven-column format remains supported. `scheme_code`,
`connection_type` and `notes` are optional; existing database fields are retained.
Copies of the old [CSV](templates/swusip-customers-legacy-seven-column.csv) and
[Excel](templates/swusip-customers-legacy-seven-column.xlsx) templates are available.
Blank optional category/notes do not erase existing information. Filling an empty
category is shown as an update in preview and can be undone.

Without a scheme code, SWUSIP resolves an existing scheme using Unicode-normalised,
case-insensitive names with repeated whitespace collapsed. Punctuation and accents
remain significant. New names receive a stable internal identifier; the displayed
scheme name is preserved. Multiple existing codes with the same normalised name
are ambiguous and require administrator review. Supply the correct existing code
after checking the institutional source. A name alone never merges customers.

## Review telephone corrections

Recognised Uganda forms `0700123456`, `700123456`, `256700123456` and
`+256700123456` resolve to `+256700123456`. Fixed-line allocations, including
03-series numbers, are classified separately. The maintained default configuration
is the [UCC numbering plan communicated 13 July 2026](https://www.itu.int/dms_pub/itu-t/oth/02/02/T02020000F10016PDFE.pdf).
Allocation validation does not establish that a number is assigned or reachable.
Unknown allocations, service/short numbers and malformed values are never repaired
by guessing. Explicit international numbers are preserved; foreign national
allocations are not verified.

An administrator may configure known incoming/import access prefixes under
Manage directory → Telephone policy. With `9` explicitly configured,
`90700123456` resolves to the same Uganda number. Prefixes default to empty and
are removed at most once. Conflicting valid interpretations require review.
Incoming SIP/tel URI caller identities use this same service and exact canonical
matching. Shared numbers show all matching customer/accounts without choosing one.

Preview shows the original number, proposed canonical number, validation status,
correction reason, scheme resolution and row action. Confirm only after reviewing
the changes. Totals distinguish new, updated, duplicate, corrected, ambiguous and
rejected rows; corrected is a subset of valid rows, ambiguous a subset of rejected.
Rejected/ambiguous rows go to an encrypted administrator review list and never
participate in caller identification. Very long rejected fields are truncated in
that review copy; retain the authorised source file for correction. Reimport a
corrected file after reviewing duplicates. Original accepted phone forms are
retained separately for each customer/account/phone association and import batch.
Legacy records with no recorded original are explicitly marked legacy/unverified.

## Undo and limits

Undo latest import requires administrator confirmation. It reverses that batch
atomically, including its provenance/review rows, and refuses if later edits,
notes or relationships would be lost. It never automatically deletes pre-existing
customers. Keep an authorised backup process; undo is not a substitute for backup.

Limits remain 10,000 data rows, 5 MB input, 20 MB expanded XLSX and 2,000 characters
per cell. These parser limits do not by themselves prove device throughput.
See the delivery test report for the largest device-validated batch.

New imports use bounded authenticated encrypted pages and undo chunks with the
existing non-exportable Keystore key. The normal five-minute authentication window,
device-unlock requirement and role checks remain. Schema 1 → 2 is additive; legacy
ciphertexts are not rewritten. Old large datasets can retain their previous
per-field read cost. Authentication expiry rolls back an unfinished import/undo;
authenticate normally and retry an appropriately sized batch.

## Customer call-back profiles

Stored canonical numbers are separate from outgoing dial strings. Customer
call-back stays disabled until an administrator records an approved genuine PBX
test reference for the exact national, country-code, +international or single
access-digit profile. The application does not verify that claim or change the
PBX. Configuration/numbering-plan changes invalidate the stored profile proof.
Never enable a profile merely to make a test button work. The existing manual
dialler and native SIP engine are unchanged; genuine calling needs separate
approved Grandstream testing.

Never upload customer workbooks, credentials or raw application files to GitHub,
AI services or development logs. Use synthetic fixtures for development.
