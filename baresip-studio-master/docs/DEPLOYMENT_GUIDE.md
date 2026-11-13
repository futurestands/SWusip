# Deployment status and boundaries

**NOT APPROVED FOR PRODUCTION.** Gate 1 passed locally; later acceptance gates
remain open and lint has 401 unresolved translation errors.
Use only genuine verified APKs on authorised lab devices. Keep SIP calling independent
of the future backend. No CRM implementation has begun.

Use unique non-production SIP credentials delivered out of band. Do not put secrets,
signing keys, customer records or raw SIP authentication traces in this public repository.
No PBX management interface may be exposed publicly. Remote access requires an approved
VPN/equivalent design and an authorised network test; no endpoint has been activated.

Future rollout requires secure credential storage, backup/component controls, app
locking, handset reliability evidence, provisioning, pilot approval and handover.
Windows Server 2022/PostgreSQL backend planning remains future work after calling gates.

## Separate telecom dependency

MTN incoming-only/outbound failure and the existing UTL analogue service are reported
by the project lead, not verified here. The institution/operator must establish trunk
status, outbound routing, lawful official caller ID and simultaneous external channel
capacity. Android code cannot independently resolve that operator/PBX issue.
