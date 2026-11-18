# SWUSIP Prioritised Correction Plan & Technical Roadmap

**Document:** SWUSIP_PRIORISED_CORRECTION_PLAN.md  
**Repository:** `https://github.com/futurestands/SWusip`  
**Branch:** `feature/swusip-call-centre`  
**Commit:** `7dc418ee045581f9d24692c6c5a0ce65a81faa54`  
**Application ID:** `ug.go.mwe.swusip`  
**Version:** `1.0.6-pilot`  
**Auditor:** Independent Lead Software Engineer & Quality Assurance Architect  

---

## 1. Defect & Remediation Backlog

This correction plan prioritises software defects, security vulnerabilities, and operational deployment requirements based on the independent repository audit.

---

### Priority P0: Critical Security & Core Calling Gates

#### Issue P0-01: Exported `TaskReceiver` Permits Arbitrary SIP Service Denial-of-Service
- **Affected Component:** `app/src/main/AndroidManifest.xml:218-227`, `TaskReceiver.kt`
- **Failure Description:** `TaskReceiver` is exported to all applications on the device without requiring a signature permission.
- **Probable Root Cause:** Inherited upstream Baresip integration intended for external automation (e.g. Tasker) without modern Android permission guarding.
- **Operational Impact:** Any malicious or compromised application on the call-centre phone can broadcast `com.tutpro.baresip.QUIT` or `UNREGISTER`, shutting down call-centre communications and dropping active calls.
- **Smallest Reliable Correction:**
  Change `android:exported="true"` to `android:exported="false"` in `AndroidManifest.xml`. If external automation is strictly required, declare an institutional signature-level permission:
  ```xml
  <permission android:name="ug.go.mwe.swusip.permission.MANAGE_CALL_TASKS"
      android:protectionLevel="signature" />
  <receiver android:name=".TaskReceiver"
      android:exported="true"
      android:permission="ug.go.mwe.swusip.permission.MANAGE_CALL_TASKS">
  ```
- **Regression Risks:** None for standard in-app dialling and call operations.
- **Verification Test:** Send broadcast intent via ADB from an untrusted UID; verify intent is rejected with `SecurityException`.
- **Implementation Effort Estimate:** 0.5 developer hours.

#### Issue P0-02: Plaintext Storage of SIP Credentials in Native Configuration
- **Affected Component:** `Account.kt:103-104`, `Account.kt:270-274`
- **Failure Description:** SIP usernames, authentication passwords, and PBX server addresses are written in plaintext ASCII to `/data/data/ug.go.mwe.swusip/files/accounts`.
- **Probable Root Cause:** Upstream Baresip C library reads accounts directly from an ASCII config file on disk.
- **Operational Impact:** If an agent's device is physically extracted, rooted, or dumped, PBX extension credentials can be compromised and used to make unauthorised calls via the PBX.
- **Smallest Reliable Correction:**
  Store the SIP extension password in Android Keystore-backed `EncryptedSharedPreferences`. At application startup, read the password into memory (`BaresipService.aorPasswords`) and strip `;auth_pass="..."` before writing to the disk config file. Inject the password dynamically via JNI or when prompted by the native engine's digest authentication callback.
- **Regression Risks:** Requires careful verification that Baresip digest authentication correctly falls back to in-memory password lookup on all registration renewals.
- **Verification Test:** Inspect `/data/data/ug.go.mwe.swusip/files/accounts` with root/backup shell; confirm no passwords appear on disk.
- **Implementation Effort Estimate:** 6 developer hours.

#### Issue P0-03: Live SIP Call & Audio Validation Against Grandstream UCM
- **Affected Component:** Physical Phone (Xiaomi 25028RN03A) ↔ Grandstream UCM6302A (`192.168.1.200:8089`)
- **Failure Description:** Live SIP registration, incoming/outgoing calls, and two-way RTP audio have not yet been verified against the physical PBX.
- **Probable Root Cause:** Operational testing environment was physically decoupled from the institutional office LAN.
- **Operational Impact:** Until a physical SIP call is completed, the core call-centre functionality cannot be certified for operational deployment.
- **Smallest Reliable Correction:**
  Execute Section 11 of `SWUSIP_MANUAL_TEST_CHECKLIST.md` with an authorized extension (e.g. extension `101`) on the office Wi-Fi network.
- **Regression Risks:** None (verification gate).
- **Verification Test:** Complete two-way extension call to desk telephone `102` with clean bidirectional audio.
- **Implementation Effort Estimate:** 2 technician/engineer hours on-site.

---

### Priority P1: Customer Directory & Import Reliability

#### Issue P1-01: Unformatted User Workbooks Trigger Numeric Account Validation Rejection
- **Affected Component:** User-Supplied Customer Spreadsheets (`custo.xlsx`), `CustomerImport.kt:51`
- **Failure Description:** Spreadsheets created by staff storing water account numbers as Excel numeric cells (rather than Text) fail importer validation (`#NUMERIC#` error).
- **Probable Root Cause:** Default Excel behaviour treats digit-only strings as numbers, discarding leading zeroes.
- **Operational Impact:** Water utility administrators cannot import unformatted raw spreadsheets.
- **Smallest Reliable Correction:**
  Do NOT modify the software to invent lost leading zeroes. Maintain the fail-closed validation policy. Distribute the bundled reference template `Download/CUSTO-SWUSIP-ready.xlsx` and provide a one-page staff instruction sheet explaining how to set the column data type to "Text" in Excel before entering account numbers.
- **Regression Risks:** Zero code regression.
- **Verification Test:** Import `CUSTO-SWUSIP-ready.xlsx`; confirm all 1,208 records import cleanly without `#NUMERIC#` errors.
- **Implementation Effort Estimate:** 1 technical documentation hour.

#### Issue P1-02: Overly Permissive FileProvider Root Hierarchy
- **Affected Component:** `app/src/main/res/xml/file_paths.xml:3`
- **Failure Description:** FileProvider root path is configured as `<files-path name="mms" path="." />`.
- **Probable Root Cause:** Legacy upstream MMS file provider configuration.
- **Operational Impact:** Grants file provider exposure to the root application files directory, which houses the SQLite database and native configuration files.
- **Smallest Reliable Correction:**
  Replace `path="."` with a dedicated subfolder:
  ```xml
  <paths>
      <files-path name="shared" path="shared/" />
  </paths>
  ```
- **Regression Risks:** Ensure export file generation writes to `context.filesDir/shared/`.
- **Verification Test:** Confirm exported customer files remain shareable while internal database files cannot be addressed.
- **Implementation Effort Estimate:** 1 developer hour.

---

### Priority P2: Usability, Attack Surface & Configuration

#### Issue P2-01: Remove Extraneous Telephony & SMS Permissions
- **Affected Component:** `app/src/main/AndroidManifest.xml:40-51`, `SmsReceiver.kt`, `ComposeActivity.kt`
- **Failure Description:** App requests `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `RECEIVE_MMS`.
- **Probable Root Cause:** Retained legacy upstream messaging features.
- **Operational Impact:** Increases device attack surface and prevents deployment via managed enterprise Google Play stores.
- **Smallest Reliable Correction:**
  Remove SMS/MMS receiver and activity declarations from `AndroidManifest.xml` and strip SMS permissions.
- **Regression Risks:** Must ensure no remaining Kotlin code calls `SmsReceiver` or `MmsReceiver`.
- **Verification Test:** Assemble APK; verify with `aapt dump permissions` that SMS permissions are absent.
- **Implementation Effort Estimate:** 2 developer hours.

#### Issue P2-02: Explicit Network Security Configuration
- **Affected Component:** `app/src/main/res/xml/network_security_config.xml`
- **Failure Description:** No explicit network security configuration is defined in the manifest.
- **Probable Root Cause:** Upstream VoIP engine uses raw C sockets.
- **Operational Impact:** Future HTTP endpoints (e.g. database sync or updates) could inadvertently permit unencrypted cleartext transport.
- **Smallest Reliable Correction:**
  Add `network_security_config.xml` setting `cleartextTrafficPermitted="false"` and reference it via `android:networkSecurityConfig` in the `<application>` tag.
- **Regression Risks:** None.
- **Verification Test:** Manifest validation and Android Lint verification.
- **Implementation Effort Estimate:** 0.5 developer hours.

#### Issue P2-03: Enable Code Obfuscation in Release Builds
- **Affected Component:** `app/proguard-rules.pro:12-13`
- **Failure Description:** `-dontobfuscate` and `-dontoptimize` prevent symbol obfuscation.
- **Probable Root Cause:** Disabled during early development to simplify native JNI crash debugging.
- **Operational Impact:** Attackers can decompile the APK to inspect institutional business logic, encryption parameters, and security policies.
- **Smallest Reliable Correction:**
  Remove `-dontobfuscate` while retaining explicit `-keep` rules for JNI native methods in `com.tutpro.baresip.Api` and Room/SQLite models.
- **Regression Risks:** JNI method lookup failure if JNI bridge classes are improperly renamed by R8.
- **Verification Test:** Build release APK; disassemble with `dexdump`/`jadx`; confirm business logic is obfuscated while native calls succeed.
- **Implementation Effort Estimate:** 2 developer hours.

---

### Priority P3: Resource Cleanup & Documentation

#### Issue P3-01: Localization String Formatting Cleanup
- **Affected Component:** `app/src/main/res/values-*.xml`
- **Failure Description:** 73 Android Lint warnings regarding unused or misaligned formatting arguments in Swedish, Czech, and Chinese string files.
- **Probable Root Cause:** Upstream Baresip community translations that became orphaned during the SWUSIP UI redesign.
- **Operational Impact:** Cosmetic build warnings; no functional impact on English operation.
- **Smallest Reliable Correction:**
  Remove unused non-English locales or align string formatting arguments with `values/strings.xml`.
- **Regression Risks:** None.
- **Verification Test:** Re-run `gradlew lintDebug`; verify warning count drops from 84 to under 10.
- **Implementation Effort Estimate:** 1 developer hour.

---

## 2. Total Implementation Effort Summary

| Priority Level | Issues | Estimated Effort |
|---|---|---|
| **P0 (Critical Security & Core Calling)** | 3 issues | ~8.5 hours |
| **P1 (Directory & Data Integrity)** | 2 issues | ~2.0 hours |
| **P2 (Permissions & ProGuard)** | 3 issues | ~4.5 hours |
| **P3 (Resource Cleanup)** | 1 issue | ~1.0 hour |
| **TOTAL REMEDIATION EFFORT** | **9 Issues** | **~16.0 Hours (2 Engineering Days)** |

---

## 3. Recommended Sequential Next Steps

1. **Gate 1 — Close Security Vulnerabilities (P0-01, P1-02):**
   Set `TaskReceiver` `exported="false"` and restrict `file_paths.xml` root path.
2. **Gate 2 — On-Premise PBX Live Test (P0-03):**
   Connect authorized test handset to institutional Wi-Fi; provision extension `101`; execute extension-to-extension test call to desk telephone `102` following `SWUSIP_MANUAL_TEST_CHECKLIST.md`.
3. **Gate 3 — Customer Directory Field Rollout (P1-01):**
   Distribute `Download/CUSTO-SWUSIP-ready.xlsx` and train administrative staff on Excel "Text" column formatting.
4. **Gate 4 — Production Hardening (P0-02, P2-01, P2-03):**
   Migrate SIP credentials to Keystore-backed storage, strip legacy SMS permissions, and enable release R8 obfuscation.
