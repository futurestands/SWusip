# PBX diagnostics workspace

This folder is separate from SWUSIP Android development. It contains only
non-sensitive diagnostic scripts and redacted reports. Android source, Gradle,
native libraries and Git configuration are outside this audit's scope.

Initial gate: test TCP reachability from this Windows computer to the authorised
endpoint `192.168.1.200:8089`. A successful TCP connection does not establish PBX
identity, administrative access, SIP registration or MTN trunk functionality.

No login attempts, configuration changes, port scans or packet captures are part
of the initial check. Further review requires the owner's audit scope and, where
needed, authorised redacted configuration exports or supervised browser access.

Do not place passwords, API keys, configuration backups, subscriber/customer data,
or unredacted captures here. Keep raw exports outside the repository. Review any
report manually for sensitive information before sharing. Nothing in this folder
is automatically staged, committed or pushed to GitHub.

Run `Test-PbxReachability.ps1` for a bounded TCP-only check. Its report contains
only the target endpoint, UTC timestamp, connection outcome and a generic failure
category; it does not collect local interface addresses, credentials or traffic.
