# SWUSIP product redesign

## Inspection and feature inventory (13 November 2026)

Inspected Compose routes, manifest, resources, service integration and repository
phone screenshot `fastlane/metadata/android/en-US/images/phoneScreenshots/shot1.png`.
No new screenshots were attached to this request. The repository screenshot shows
unlabelled recording, microphone, speaker, voicemail, contacts, messaging, history
and keypad icons, an account selector and an active call with hold and transfer.

- Main: preserve genuine dialling, answer/reject and call presentation. Replace
  upstream header and icon-only navigation with SWUSIP identity and labelled destinations.
- Account selector: preserve assigned extension and registration visibility;
  account creation, registration configuration and credentials belong to administrators.
- Call card: preserve answer, reject, mute, speaker, hold, transfer, keypad and hang-up.
  Customer lookup must be asynchronous and must never delay these controls.
- Contacts/contact editor: preserve internal speed dial contacts; customer records
  are a separate institutional directory, not Android device contacts.
- Calls/call details: preserve history, missed-call state and callback.
- Chats/chat/compose, SMS/MMS receivers and default messaging role: remove from
  the ordinary agent workflow; remove underlying components only after call regression.
- Settings: agent audio, notifications, background guidance, help and About remain.
  Server, transport, credentials, TLS, certificates, network, logging, import and
  sync move behind administrator authorisation. A device unlock alone is not an admin role.
- Accounts/account/codecs: administrative provisioning and audio policy.
- Blocking/blocked: policy administration; do not expose undocumented gestures.
- Backup/restore/log export/restart/quit: administrative maintenance. Do not
  expose credentials or customer records through ordinary agent export.
- About: institutional identity and help, with separate original third-party attribution.

## Interface

SWUSIP header and institutional subtitle, extension and textual SIP status,
labelled Dialler, Contacts, Call History, Customers and Settings destinations.
Use neutral water/telephone artwork until approved logos are supplied. Keep all
primary controls in a scrollable layout on small screens. The existing native
call implementation remains the source of truth.

## Customer data model

Use generated immutable customer IDs, never telephone numbers as primary keys.
Customer(id, name, location, createdAt, updatedAt); Phone(id, normalisedNumber);
CustomerPhone(customerId, phoneId, primary); Scheme(id, code, name);
Account(id, accountNumber TEXT, schemeId, category);
CustomerAccount(customerId, accountId); AccountPhone(accountId, phoneId);
Note(id, customerId, authorId, text, createdAt); ImportBatch(id, actorId,
createdAt, status); ImportChange(batchId, entityId, beforeVersion, afterVersion).
Scheme code plus account number identifies an account; shared numbers and joint
accounts are valid. Account ownership conflicts require explicit preview resolution.

## Security and import design

No customer data may enter logs, source control or external AI services. Use only
synthetic fixtures. Authenticated agent access and a separately provisioned admin
role must be enforced by repositories as well as navigation. Fail closed before
role provisioning. Encrypt records with Android Keystore protected keys; exclude
customer storage from backup. Subject the design to review before real-data use.
Import must parse locally, preserve text identifiers, show row errors and proposed
changes, commit atomically and retain versioned undo information. Undo must reject
records changed since import. No automatic deletion. Future sync is an interface,
not an invented server. CSV template is in `docs/templates/swusip-customers.csv`.

## Verification gates

Build and source-integrity checks do not prove PBX calling. Record device launch,
synthetic directory lookup and genuine incoming/outgoing PBX audio tests separately.
Do not publish an acceptance claim until each requirement has evidence. No PBX or
production server configuration changes are authorised by this implementation.
