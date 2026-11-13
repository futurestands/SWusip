# Physical-device startup evidence

`observed-result.json` is the corrected, authoritative 30-second startup observation.
`installation.json` records the successful data-preserving installation and launch.
`permissions.json` records only app-specific permission booleans.

`initial-harness-result.json` is retained for diagnosis, **not acceptance**: its zero
Logcat-line count and negative library-filename check were invalidated by padded
epoch formatting and direct APK native mappings. See `../../DEVICE_STARTUP_REPORT.md`.
`prior-blocked.json` preserves the preceding no-device milestone.

No raw device logs, endpoints, serial numbers, installed paths, SIP identifiers,
authentication material or customer data are included. Raw diagnostics remain in
the ignored local cache. Text streams are normalised as in the parent evidence README.
