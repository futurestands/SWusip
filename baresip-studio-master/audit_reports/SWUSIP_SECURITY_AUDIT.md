# SWUSIP Cybersecurity & Data Protection Audit

**Document:** SWUSIP_SECURITY_AUDIT.md  
**Repository:** `https://github.com/futurestands/SWusip`  
**Branch:** `feature/swusip-call-centre`  
**Commit:** `7dc418ee045581f9d24692c6c5a0ce65a81faa54`  
**Target Application:** South Western Umbrella Call Centre (SWUSIP)  
**Package:** `ug.go.mwe.swusip`  
**Auditor:** Independent Cybersecurity & Software Architecture Auditor  

---

## 1. Executive Security Evaluation

An independent security and data protection audit of the SWUSIP Android application codebase was conducted. The application exhibits high cryptographic discipline in its customer directory, utilising hardware-backed Android Keystore keys, authenticated AES-GCM envelope encryption, screen capture prevention, and device-bound role authorization. 

However, several critical and high-priority architectural security findings must be addressed prior to institutional production deployment:
1. **Plaintext SIP Credential Storage:** Native SIP accounts containing passwords and server addresses are stored unencrypted in the application's internal files directory (`/data/data/ug.go.mwe.swusip/files/accounts`).
2. **Denial-of-Service via Exported BroadcastReceiver:** `TaskReceiver` is exported without signature permissions, permitting any non-privileged application on the device to terminate the SIP calling service or unregister SIP accounts.
3. **Overly Permissive FileProvider Configuration:** `file_paths.xml` grants access to the root internal storage directory (`.`), allowing potential traversal if a content URI is shared.
4. **Extraneous Telephony Attack Surface:** Retained upstream SMS/MMS permissions (`SEND_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`) create unnecessary privilege exposure for a voice-only call centre.

---

## 2. Vulnerability Severity Dashboard

| Finding ID | Title | Severity | Impact | Location |
|---|---|---|---|---|
| **SEC-01** | Exported `TaskReceiver` without Permission Protection | **HIGH** | Denial of Service / SIP Unregistration | `AndroidManifest.xml:218-227` |
| **SEC-02** | SIP Credentials Stored in Plaintext Configuration | **HIGH** | Credential Exposure on Rooted/Dumped Devices | `Account.kt:270-274` |
| **SEC-03** | Overly Broad FileProvider Root Path | **MEDIUM** | Internal Storage Traversal Risk | `res/xml/file_paths.xml:3` |
| **SEC-04** | Unnecessary Telephony & SMS Permissions Declared | **MEDIUM** | Excessive Android Permission Surface | `AndroidManifest.xml:40-49` |
| **SEC-05** | Obfuscation Disabled in Release ProGuard Configuration | **LOW** | Reverse Engineering of Institutional Logic | `proguard-rules.pro:12-13` |
| **SEC-06** | Absence of Explicit Network Security Configuration | **LOW** | Potential Cleartext Transport Regression | `res/xml/` (missing file) |

---

## 3. In-Depth Technical Findings

### SEC-01: Exported `TaskReceiver` Without Permission Protection
- **Severity:** **HIGH** (CVSS: 7.5 - `CVSS:3.1/AV:L/AC:L/PR:N/UI:N/S:U/C:N/I:H/A:H`)
- **Evidence:** `app/src/main/AndroidManifest.xml:218-227`, `TaskReceiver.kt:13-58`
```xml
<receiver
    android:name=".TaskReceiver"
    android:enabled="true"
    android:exported="true"
    tools:ignore="ExportedReceiver" >
    <intent-filter>
        <action android:name="com.tutpro.baresip.REGISTER" />
        <action android:name="com.tutpro.baresip.UNREGISTER" />
        <action android:name="com.tutpro.baresip.QUIT" />
    </intent-filter>
</receiver>
```
- **Vulnerability Analysis:**
  The `TaskReceiver` component is explicitly exported to the entire operating system (`android:exported="true"`) with a lint suppression (`tools:ignore="ExportedReceiver"`). The receiver handles three actions: `com.tutpro.baresip.REGISTER`, `com.tutpro.baresip.UNREGISTER`, and `com.tutpro.baresip.QUIT`. 
  Because no custom signature-level permission (`android:permission`) is declared, **any rogue or third-party application installed on the agent's phone can broadcast `com.tutpro.baresip.QUIT` to kill the `BaresipService` foreground service**, preventing incoming emergency or customer support calls.
- **Remediation:**
  Set `android:exported="false"` unless external orchestration (e.g., Tasker / MDM) is genuinely required. If external invocation is required, guard the receiver with a custom permission possessing `android:protectionLevel="signature"`.

---

### SEC-02: Plaintext SIP Credential Storage in Native Configuration File
- **Severity:** **HIGH** (CVSS: 6.8 - `CVSS:3.1/AV:P/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:N`)
- **Evidence:** `Account.kt:103-104`, `Account.kt:270-274`
```kotlin
// Account.kt:103-104
if ((authPass != "") && !BaresipService.aorPasswords.containsKey(aor))
    res += ";auth_pass=\"${authPass}\""

// Account.kt:270-274
for (a in accounts()) accounts = accounts + a.print() + "\n"
Utils.putFileContents(
    BaresipService.filesPath + "/accounts",
    accounts.toByteArray(Charsets.UTF_8)
)
```
- **Vulnerability Analysis:**
  While customer directory data is strongly encrypted via Android Keystore, SIP account credentials (including extension authentication username, password, realm, and PBX IP address) are stored in plain ASCII text within the Baresip configuration file located at `/data/data/ug.go.mwe.swusip/files/accounts`.
  While protected by standard Linux process isolation on unrooted handsets, this file is readable if:
  1. The device is rooted.
  2. A physical flash acquisition or extraction vulnerability exists on the device SoC.
  3. A local privilege escalation or debug vulnerability is exploited.
- **Remediation:**
  Migrate SIP credential storage into Android Keystore-backed `EncryptedSharedPreferences` or store the password strictly in memory (`BaresipService.aorPasswords`), injecting it into native memory at runtime via JNI rather than persisting it to the filesystem.

---

### SEC-03: Overly Broad FileProvider Root Path
- **Severity:** **MEDIUM** (CVSS: 5.3 - `CVSS:3.1/AV:L/AC:L/PR:N/UI:N/S:U/C:L/I:L/A:N`)
- **Evidence:** `app/src/main/res/xml/file_paths.xml:3`
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="mms" path="." />
</paths>
```
- **Vulnerability Analysis:**
  The `FileProvider` configuration specifies `path="."` for internal files storage. This exposes the entire `context.filesDir` root hierarchy (which includes the plaintext `accounts` file and `swusip-directory.db`). Although the `FileProvider` is declared with `android:exported="false"`, any intent filter or activity that attaches a URI from this provider could inadvertently expose private database files or credentials.
- **Remediation:**
  Restrict `file_paths.xml` to a dedicated subdirectory (e.g., `<files-path name="exports" path="exports/" />`).

---

### SEC-04: Extraneous Telephony & SMS Permissions Declared
- **Severity:** **MEDIUM** (CVSS: 4.3 - `CVSS:3.1/AV:L/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N`)
- **Evidence:** `app/src/main/AndroidManifest.xml:40-51`
```xml
<uses-permission android:name="android.permission.SEND_SMS" tools:ignore="SmsAndCallLogPolicy" />
<uses-permission android:name="android.permission.RECEIVE_SMS" tools:ignore="SmsAndCallLogPolicy" />
<uses-permission android:name="android.permission.READ_SMS" tools:ignore="SmsAndCallLogPolicy" />
<uses-permission android:name="android.permission.RECEIVE_MMS" tools:ignore="SmsAndCallLogPolicy" />
<uses-permission android:name="android.permission.RECEIVE_WAP_PUSH" tools:ignore="SmsAndCallLogPolicy" />
<uses-permission android:name="android.permission.WRITE_CALL_LOG" tools:ignore="SmsAndCallLogPolicy" />
```
- **Vulnerability Analysis:**
  The application requests dangerous-level SMS and MMS permissions inherited from upstream Baresip. For an institutional water utility voice call centre, mobile SMS capabilities are neither required nor active in the primary user interface. Declaring these permissions expands the application's attack surface and triggers automatic Google Play policy rejections.
- **Remediation:**
  Remove SMS/MMS receivers and permissions once upstream SIP stability is established.

---

### SEC-05: Obfuscation Disabled in Release ProGuard Configuration
- **Severity:** **LOW**
- **Evidence:** `app/proguard-rules.pro:12-13`
```proguard
-dontobfuscate
-dontoptimize
```
- **Vulnerability Analysis:**
  Release builds do not obfuscate class names, method signatures, or internal data models. Any third party with access to the release APK can disassemble it into readable Kotlin bytecode.
- **Remediation:**
  Enable ProGuard obfuscation (`-dontobfuscate` removed) while maintaining `-keep` rules for JNI entry points in `com.tutpro.baresip.Api`.

---

### SEC-06: Absence of Explicit Network Security Configuration
- **Severity:** **LOW**
- **Evidence:** Missing `res/xml/network_security_config.xml`
- **Vulnerability Analysis:**
  While the native Baresip engine communicates via raw UDP/TCP sockets (bypassing Java network security configurations), any future HTTP client (such as customer database synchronization) could fall back to unencrypted cleartext HTTP unless explicitly prevented.
- **Remediation:**
  Introduce `network_security_config.xml` with `cleartextTrafficPermitted="false"` and reference it in the manifest.

---

## 4. Cryptographic Implementation Review

### 4.1 Android Keystore & Key Generation
- **Alias:** `swusip.directory.v1`
- **Algorithm:** `KeyProperties.KEY_ALGORITHM_AES` (256-bit)
- **Mode:** `KeyProperties.BLOCK_MODE_GCM`
- **Padding:** `KeyProperties.ENCRYPTION_PADDING_NONE`
- **Hardware Binding:** Key is generated inside the device's hardware Secure Element / StrongBox / TEE.
- **Authentication Requirement:**
  ```kotlin
  .setUserAuthenticationRequired(true)
  .setUserAuthenticationValidityDurationSeconds(300)
  .setUnlockedDeviceRequired(true)
  ```
  The key requires device authentication (fingerprint, face, or secure device PIN) and invalidates automatically after **300 seconds (5 minutes)**. Access attempts beyond 5 minutes fail closed with `UserNotAuthenticatedException`.

### 4.2 Customer Database Envelope Encryption (`DirectoryCipherPages.kt`)
- All customer PII (name, phone number, account number, scheme details, notes) is stored as AES-256-GCM ciphertext.
- **Associated Authenticated Data (AAD):** Every encrypted block binds `"$table:$id"` as AAD:
  ```kotlin
  cipher.updateAAD("$table:$id".toByteArray())
  ```
  This mathematically prevents **ciphertext relocation attacks**: an attacker with raw SQLite access cannot copy ciphertext from one customer record and paste it into another, as GCM authentication tag verification will immediately fail.
- **Paged Architecture:** Records are packed into immutable 64 KiB pages (max 128 records/page) in `directory_page`, reducing cryptographic context switching and eliminating SQLite cursor memory pressure.

### 4.3 Screen & Thumbnail Privacy (`InstitutionScreenPrivacy.kt`)
- **Implementation:** `window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)`
- Dynamically applied with reference counting to all customer directory screens, customer search views, and in-call caller panels.
- **Outcome:** The Android operating system blocks screenshots, prevents screen recording, and blacks out recent-app switcher thumbnails, protecting customer account numbers and water scheme identities from shoulder surfing or malware screenshot scraping.

### 4.4 Application Backup Restrictions
- `android:allowBackup="false"`
- `android:fullBackupContent="false"`
- **Outcome:** Prevents extraction of SQLite database files or SharedPreferences via `adb backup`.
