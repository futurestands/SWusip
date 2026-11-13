# SWUSIP offline directory pilot

## Status and scope

This is an Android development milestone, not a claim of verified PBX calling.
The phone did not have an assigned SIP extension when this work began. No UCM6302A,
production PBX, Windows Server, trunk, extension or firewall settings were changed.
The native JNI source, libraries and build configuration were preserved.

The application now has SWUSIP home identity, neutral water/telephone artwork,
labelled navigation, textual SIP status, recent calls, agent settings, protected
administrator routes, institutional About and bundled third-party notices.
Customer screens provide local search, scheme filtering/browsing, profiles,
interaction notes, authorised name/location updates, CSV/XLSX preview/import,
conflict/duplicate reporting, import totals and latest-batch undo. The call panel
uses the actual Call object's peer URI and performs lookup on Dispatchers.IO.
Opening the customer screen does not terminate the native call.

Use Contacts for staff contacts and SIP speed dials. Use Customers for institutional
customer records and account information; customer spreadsheet import writes to the
protected directory, not to the legacy contact list or Android phone book.

Messaging controls are removed from the ordinary home, contact and history flows.
Underlying SMS/MMS code and manifest components are intentionally retained until
the user's required genuine SIP regression has been completed. Do not interpret
this pilot as the completed SMS/MMS removal milestone. No default phone/SMS role
is necessary for SIP use. Legacy administrator configuration still includes some
upstream mobile options; its further reduction requires that regression gate.

## Authorise a phone without a management server

The local UCM PBX manages telephone extensions. Phone application authorisation
is separate and does not require a central server or Android-management service.

1. Enable a secure PIN/password on the phone.
2. Open SWUSIP Settings, then Phone authorisation. Obtain its device code.
3. On the authorised administrator's offline Windows workstation, run the Java
   issuer from this Android project directory. Use Android Studio's bundled Java:

   `java scripts/IssuePilotGrant.java issue DEVICE_CODE administrator pilot-admin OUTPUT.phone-grant.json`

   For ordinary agents use `agent` instead of `administrator` and a distinct
   institutional agent identifier. Neither command changes PBX configuration.
4. Transfer only the resulting signed authorisation file to its intended phone.
   In Phone authorisation, load that file using Android's document picker.
5. Tap Unlock institutional workspace and enter the phone's PIN/password on the
   phone itself. The workspace session expires after five minutes. Calls continue
   independently of directory/admin access.

The pilot issuer is initialised with `java scripts/IssuePilotGrant.java init`.
It refuses to overwrite an existing authority. Its private key lives in ignored
`.local-pilot-authority/issuer.pk8`. It must remain private, backed up securely
and offline. Do not commit it, distribute it with the APK, or send it in chat.
The public key alone is packaged in the **debug** source set. Signed grants are
device-bound, expire after 30 days, and cannot replace a newer grant with an older
one. A newly issued `revoked` grant can withdraw access on a phone. Offline
revocation requires delivering that file; it is not instantaneous remote revocation.

Release builds require an institution-controlled `institution-authority.der`
public key, or an approved managed-device policy. They do not trust the debug
pilot authority. No production institutional signing authority is invented here.
Where a management service is introduced later, Android managed restrictions
`swusip_role=agent|administrator` take precedence over offline grants.

## Provision the existing SIP extension

After administrator authorisation and workspace unlock, use Extension provisioning
to enter an existing UCM extension and the SIP authentication details supplied by
the PBX administrator. Enter passwords only in the protected application, not in
chat, screenshots, Git or test evidence. Do not create/change PBX extensions as part
of an Android installation without separate approval.

Verify registration and two-way audio with an actual UCM test call. Separately
test answer/reject, outgoing calls, mute, speaker, hold, transfer, keypad, hang-up
and background incoming calls. Successful launch is not proof of any of these.

## Import customer spreadsheets

Use `docs/templates/swusip-customers.xlsx` or the CSV template. Both can be saved
from the administrator's Manage directory controls. The Excel template formats
the seven columns as Text for the first 1,000 blank data rows, including telephone
and account identifiers. When extending the template, copy a formatted blank row
or set the new cells to Text **before** entering identifiers. Formatting an existing
numeric cell cannot restore zeroes that Excel already removed.

Required columns, in order:

`customer_name,phone_number,account_number,scheme_code,scheme_name,connection_type,notes`

The first five fields are mandatory. Category and notes may be empty. Account
numbers retain leading zeroes. Numeric Excel account cells are rejected because
lost identifier zeroes cannot be recovered reliably. Reformat as Text and re-enter
the original identifier. Formula cells are rejected. Import reads the first
standard XLSX worksheet locally, including shared and inline strings. Other Excel
formats, encrypted workbooks and CSV encodings other than UTF-8 are unsupported.
Limits: 5 MB compressed/input document, 20 MB expanded XLSX, 10,000 rows per batch,
2,000 characters per cell. Blank rows are ignored.

Preview shows each proposed action and row errors before mutation. Same scheme
and account with conflicting owner/category, or inconsistent scheme names, are
rejected. Exact duplicates are skipped. An additional number for the same existing
account/customer is an authorised additive update. Shared numbers across different
accounts/customers and repeated account numbers in different schemes are valid.
Names alone never merge different people. An administrator can associate an existing
account with a customer from the profile after reviewing its scheme and current
customer associations. This supports multiple accounts and joint accounts without
using a phone number as customer identity. Existing associations remain. Ambiguous
ownership is never silently reassigned by spreadsheet import.

Import commits atomically and rechecks authorisation. Undo reverses only changes
created by the latest batch, preserving earlier records. If a created record has
subsequent edits or dependent notes/links, undo fails and rolls back its own work
instead of discarding the later information. Existing records are never deleted
automatically during import. Source spreadsheets remain in the chosen document
provider; SWUSIP does not upload or automatically delete those source files.

## Export and future synchronisation

Only administrators can create an encrypted customer spreadsheet export or restore
one. Export contains the seven spreadsheet fields, not interaction-note history or
an entire database backup. It uses a passphrase of at least 16 characters, PBKDF2
HMAC-SHA256 with 600,000 iterations, a random salt and AES-256-GCM with a random nonce.
No plaintext export file is written. Store the passphrase separately and retain it:
there is no recovery backdoor. Restore decrypts locally and shows the normal import
preview; it does not erase the directory. Portable spreadsheet exports are limited
to 5 MB of plaintext fields. The future CustomerSync contract has no server endpoint
or upload implementation; the initial app works offline.

## Customer-data protection and review

SQLite stores generated identifiers and relational links. Sensitive fields are
authenticated AES-GCM ciphertext. Table name and record ID are authenticated as
associated data to prevent ciphertext substitution between records. Android Keystore
holds a non-exportable, authentication-bound AES key with a five-minute validity
window and an unlocked-device requirement. App backups are disabled. Directory
read/write methods enforce the current signed/managed role and workspace session.
Customer/account windows exclude screenshots and recent-task thumbnails. Lookup
never adds account details to lock-screen notifications or logs.

Schema version 1 is a separate customer database, leaving existing SIP configuration
and history storage intact. Unknown migrations fail without destructive recreation.
Keystore loss/invalidation also fails closed. Do not clear the app or remove its
secure device lock expecting local encrypted records to remain recoverable.

The design was checked against Android documentation during development. Android
documents availability/invalidation issues with unlocked-device-required keys on
Android 12–14; validate older devices before deployment. The currently authorised
test handset runs Android 15. This implementation and its operational key custody
still require institutional security/data-protection review before real customer
data is loaded. Debug builds and ADB access are for the synthetic-data pilot.

References: [Android managed configurations](https://developer.android.com/work/managed-configurations),
[Android Keystore parameters](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder),
[OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

## Verification evidence

Use the final build log, unit-test XML and device report recorded for this milestone.
Earlier failed build logs are retained as development evidence. The initial nine
parser/normalisation unit tests passed; additional customer matching, conflict and
encrypted-export tests were added afterward. Device tests separately exercise the
actual Android XLSX parser, template, forged-grant rejection, schema reopen,
non-destructive unknown-migration rejection and foreign-key integrity. Those schema
tests do not claim to prove authenticated customer import or Keystore round trips.

The synthetic lookup unit test asserts customer name, account `000123` and scheme
`Synthetic Water Scheme`. It is explicitly a synthetic directory test, not a SIP
event or a successful PBX call. A complete authenticated import/profile/undo flow
and actual incoming customer identification must also be reviewed on the phone.
