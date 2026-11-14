# Android continuation review

Baseline: `df3c713`, SWUSIP 1.0.2-pilot, branch `feature/swusip-call-centre`.
The remote branch matched this baseline at the start of this review.

## Prioritised verified findings

1. **Release blocker: SIP credential persistence.** `Account.print` can write
   saved SIP and STUN secrets into the native engine's private configuration.
   Android sandbox/device encryption and disabled backup protect access, but this
   is not a separate Keystore credential vault. Native diagnostic tracing can
   contain SIP addresses or message content. Do not distribute a production
   release or collect real-data traces until a reviewed storage/migration design
   and real registration/background-call regression have passed. This update
   does not silently migrate or delete existing credentials.
2. **Fixed: incorrect phone/account combinations.** The directory read query
   joined all customer phones to all associated accounts, ignoring `account_phone`.
   Reads now require the actual account-phone relationship. No database contents
   or schema were automatically deleted or rewritten.
3. **Fixed: large import scaling.** Import repeatedly searched growing customer,
   account, scheme and phone collections. It now builds indexes once and reuses
   the helper's Keystore key handle; AES-GCM encryption/authentication remains.
   Parsing still enforces 10,000 rows, 5 MB input, 20 MB expanded XLSX and cell
   limits. Large-batch parser and preview regressions were added.
   Shared-account preview now uses all associated owners and agrees with the
   conservative commit conflict check instead of depending on record order.
4. **Fixed: password disclosure in validation UI.** Invalid SIP/STUN password
   errors and the password dialog included the entered value. They now provide
   generic guidance. Direct-reply debug logging no longer includes message text.
5. **Fixed: silent Call History navigation without an extension.** It now explains
   that provisioning is required. Dialler handlers/keypad are retained.
6. **Improved: directory management usability.** Management controls scroll within
   a bounded area, leaving room for results. Undo requires an explicit confirmation
   and continues to refuse reversal of records with dependent later edits/notes.
7. **Added: local agent infrastructure.** Signed grants already contain individual
   IDs. Managed deployments can supply `swusip_agent_id`. Settings display the
   authorised identity and a local Available/Busy/Off duty selector. New history
   entries record the assigned device identity when available; legacy entries
   remain unattributed. Call details label this as assignment, not proof of who
   answered. Customer notes already include author and time.

## Existing features retained

- Real Baresip JNI calling, Telecom integration, foreground service and call
  controls. No mock engine, native changes or PBX configuration changes.
- Protected administrator routes, signed device grants, secure device lock and
  five-minute credential-confirmed workspace sessions.
- AES-GCM customer/account/scheme/phone/import-journal/note payloads, authenticated
  Android Keystore keys, additive import and fingerprint/dependency-protected undo.
- Search by name, telephone, account or scheme; visible scheme filter and directory;
  explicit administrator association of multiple accounts without merging namesakes.
- Actual `Call.peerUri` lookup on an IO coroutine and profile navigation independent
  of call termination. No customer account details added to lock-screen notifications.
- Institutional About and dedicated third-party acknowledgements. Upstream class,
  package, JNI and licence identifiers remain. Unused upstream help resources are
  not evidence of visible agent branding; active About uses the SWUSIP resource.
- SMS/MMS internals remain until genuine SIP regression permits removal.

## Outstanding verification and development

- Owner authentication is required for real document-picker import, encrypted
  repository performance, searches, notes, profiles, multi-account associations,
  reversal and agent-versus-administrator UI review. Do not count a skipped or
  unauthenticated test as a pass of these workflows.
- The opt-in `AuthenticatedDirectoryDeviceTest` uses the normal workspace unlock
  callback, then exercises encrypted repositories in uniquely named synthetic
  databases. It never injects session state or changes the real directory. This
  is separate from document-picker/end-user UI verification.
- Registration, incoming/outgoing calls, audio, transfer, hold, hang-up, caller
  identification during a real call, background delivery and battery reliability
  need the approved UCM test arrangements in the SIP checklist.
- Review native credential persistence, legacy contact/history/recording storage,
  native trace privacy and authenticated backup/restore before production use.
- Local availability is a temporary screen/session indication; navigating away
  can reset it. It does not set PBX DND/queue state, reject calls, publish presence
  or provide shared staffing information.
- `CustomerSync` remains an offline contract. No backend, upload, queue capacity,
  MTN trunk capacity or central CRM has been invented. Agree scoped identities,
  transport/authentication, conflict rules, audit/retention and revocation before
  introducing a real synchronisation implementation.

See the continuation delivery record for measured test outcomes. These remaining
items are explicit release gates, not assertions of completed acceptance.
