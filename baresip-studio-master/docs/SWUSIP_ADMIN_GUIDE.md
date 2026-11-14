# SWUSIP administrator setup

The 1.0.5 customer-workflow changes and outstanding device/security acceptance
gates are recorded in [Customer workflows](SWUSIP_CUSTOMER_WORKFLOWS.md).

This pilot is not a production-signed release. Review the security release blockers
in [the Android review](SWUSIP_ANDROID_REVIEW.md) before deployment to 15 agents.

## Authorise individual agents

Use [the existing pilot authorisation procedure](SWUSIP_PILOT_GUIDE.md). Issue one
device-bound grant per phone, with a distinct institutional agent ID and the
`agent` role for ordinary staff. Use `administrator` only on approved management
phones. Keep the private issuer key offline; transfer only signed grants. Debug
grants expire after 30 days. Offline revocation requires delivering a newer signed
revocation file. Do not describe it as remote real-time revocation.

If approved Android device management is introduced, use `swusip_role` plus
`swusip_agent_id`. Managed restrictions take precedence; invalid/unassigned roles
deny directory/admin access. Workspace entry still requires secure-device
credential confirmation. Ordinary agents must not be issued administrator grants
merely to avoid the import/access controls.

## Provision an existing office extension

After workspace authentication, Settings exposes Extension provisioning and
Administrator settings. Use only the UCM administrator's approved test account.
Enter its SIP address/account identity, authentication username/password, PBX
host/domain, configured port and transport. The wireless ADB address is not the PBX
address. Do not invent an IP, port, password, TLS certificate policy or trunk rule.

Use the supplied TLS/SRTP and certificate-verification policy. Do not turn off
verification to conceal a certificate problem. Check microphone/notification
permissions, registration status and office network reachability. Do not create
or alter production UCM extensions, routes, trunks or firewall rules in this task.
Follow [the genuine SIP checklist](SWUSIP_UCM_TEST_CHECKLIST.md).

Saved native SIP/STUN credentials currently remain in app-private native files
under Android device encryption; they are not separately Keystore encrypted. App
backup is disabled. Do not export raw app configuration or enable diagnostic SIP
tracing with real customer data. A reviewed credential-vault migration and real
registration/background-call regression remain production release prerequisites.

## Customer import and reversal

Use Manage directory to save the CSV or Excel template, then Import CSV / XLSX.
Keep account and telephone cells as Text before entering values. The first five
template fields are required. Limits are 10,000 data rows, 5 MB input, 20 MB expanded
XLSX and 2,000 characters per cell. Only UTF-8 CSV and ordinary first-sheet XLSX
are supported. Never send real customer spreadsheets to development/AI services.

Review the entire preview and row errors. Duplicate account/phone rows are skipped;
conflicting ownership/category/scheme names are rejected. Import valid rows shows
accepted/updated/skipped/rejected totals. New namesakes are not automatically
merged. Use the profile's reviewed account-association workflow only after checking
the institutional source of truth. Multiple customers can share a phone or account.

Undo latest import requires confirmation and reverses only the latest batch. It
does not delete the whole directory. Later edits/notes/dependencies can make undo
unsafe; SWUSIP then refuses and retains the data. Do not force it through raw SQL.
Management controls scroll within their panel on smaller screens.

Encrypted export uses a separate passphrase of at least 16 characters. Transfer the
passphrase separately. Restore goes through preview and additive import. Spreadsheet
exports do not include interaction notes or preserve a complete relational/archive
history; they are not a full disaster-recovery backup. Keystore keys are device-bound.

## Server and PBX boundaries

Android provides local authorised records, note authorship, assigned-agent history,
temporary local status and an offline sync interface. UCM queue membership, routing,
simultaneous calls, trunks/MTN capacity, extension policy and shared presence require
PBX administration. Shared customer/interaction history, central audit, remote
revocation and CRM synchronisation require an approved backend and deployment plan.
