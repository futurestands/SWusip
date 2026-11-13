# Security recovery status

Production deployment is blocked. Audit findings are evidence to verify, not a
completed security assessment. Public-repository visibility is an institutional
governance concern; no visibility change or internal configuration publication is
authorised by this recovery work.

Current source review confirms the manifest has no explicit backup prohibition and
retains broad SMS/contact/calling permissions. Credential protection, component
access, app locking, export encryption and permission reduction still require
implementation and tests. Do not destroy or silently migrate existing credentials.

Native sources are pinned in a lock and fetched from upstream. Symbol and ABI guards
are added. OSV `/v1/querybatch` returned zero matching commit advisories for all
13 pinned native source commits; see `evidence/native-osv-results.json`. This commit
query has limited coverage and is not a complete package/version or transitive
dependency scan. Gitleaks 8.30.1 (official download, verified published SHA-256)
scanned the five-commit history and prospective repository files; both exited 0
with zero findings. Reports: `evidence/gitleaks-history.json` and
`evidence/gitleaks-working-tree.json`. The final pre-push prospective-file scan
also exited 0 with no leaks; see `evidence/gitleaks-prepush.json`. Pinning does
not certify dependencies as safe. See the development
OpenSSL snapshot and inherited codec licence risks in `NATIVE_DEPENDENCIES.md`.

No credentials were supplied, placed into the application, or published by recovery.
No authentication bypass, PBX changes or network exposure has been introduced.

Initial recovery lint reported 401 MissingTranslation errors and 100 warnings;
that historical report is retained. The follow-up milestone resolves all translation
errors through documented supported-language resource curation and now passes lint
with **0 errors / 45 warnings**, without baseline or suppression changes.
Eight source/resource/device-privacy unit tests pass; they do not establish Android
authentication, Keystore, exported-component or runtime-permission security.
