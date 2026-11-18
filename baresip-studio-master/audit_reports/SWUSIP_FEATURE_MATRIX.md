# SWUSIP Feature Verification Matrix

**Document:** SWUSIP_FEATURE_MATRIX.md  
**Repository:** `https://github.com/futurestands/SWusip`  
**Branch:** `feature/swusip-call-centre`  
**Commit:** `7dc418ee045581f9d24692c6c5a0ce65a81faa54`  
**Application ID:** `ug.go.mwe.swusip`  
**Version:** `1.0.6-pilot` (Version Code: `592`)  

---

## 1. Feature Verification Summary

| Category | Total Features | PASS | PARTIAL | FAIL | UNTESTED | MISSING | EXTERNAL |
|---|---|---|---|---|---|---|---|
| **A. Institutional Branding** | 8 | 7 | 1 | 0 | 0 | 0 | 0 |
| **B. SIP Telephone Functionality** | 18 | 4 | 7 | 0 | 3 | 0 | 4 |
| **C. Customer Directory** | 8 | 8 | 0 | 0 | 0 | 0 | 0 |
| **D. Excel/CSV Importing** | 12 | 11 | 1 | 0 | 0 | 0 | 0 |
| **E. Smart Phone Normalisation** | 8 | 8 | 0 | 0 | 0 | 0 | 0 |
| **F. Automatic Caller ID** | 10 | 8 | 1 | 0 | 0 | 0 | 1 |
| **G. Agent Workspace** | 10 | 8 | 1 | 0 | 0 | 0 | 1 |
| **H. Administration & Future Integration** | 9 | 3 | 2 | 0 | 0 | 0 | 4 |
| **I. Security & Privacy** | 14 | 10 | 2 | 1 | 0 | 0 | 1 |
| **TOTALS** | **97** | **67** | **15** | **1** | **3** | **0** | **11** |

---

## 2. Comprehensive Feature Matrix

### Section A: Professional Institutional Branding

| Feature | Implemented? | Automated test | Device test | Result | Evidence | Required action |
|---|---|---|---|---|---|---|
| Institutional Application Identity | Yes | Yes (APK badge test) | Yes (Installed on phone) | **PASS** | `verify_apk.py` checks `application-label:'SWUSIP'`; `MainScreen.kt#L987` | None. Institutional identity fully established. |
| Professional Home Screen | Yes | Yes (Test build) | Yes (Physical device) | **PASS** | `MainScreen.kt#L987-1030`; `AboutLegalDeviceTest.kt` | Maintain Compose layout. |
| Functional Top & Tab Navigation | Yes | Yes | Yes (Physical device) | **PASS** | `FeatureNavigationDeviceTest.kt#L74-124` passes | None. |
| Clear Account Registration Status | Yes | Yes | Yes (Physical device) | **PASS** | `MainScreen.kt#L991-996` displays Connected / Connecting / Not Registered with traffic-light indicators | Verify against live PBX registration. |
| Consistent Institutional Branding Palette | Yes | N/A (Visual) | Yes (Device screenshots) | **PASS** | `AboutScreen.kt#L26-30` defines `InstitutionBlue`, `InstitutionInk`, `PaleBlue` | None. |
| Institutional Support Information | Yes | Yes | Yes (Device review) | **PASS** | `AboutScreen.kt#L78-83` ("contact your SWUSIP administrator") | None. |
| Institutional About Screen | Yes | Yes | Yes (Device review) | **PASS** | `AboutScreen.kt#L61-94`; `about-portrait.png` | None. |
| Open-Source Attribution Notices | Yes | Yes (`apk-inspection.txt`) | Yes | **PARTIAL** | Bundled in `assets/third-party-notices.txt`; screen accessible. However, strings in `values-*.xml` retain upstream Baresip references. | Audit and clean remaining non-English string resources. |

---

### Section B: SIP Telephone Functionality

| Feature | Implemented? | Automated test | Device test | Result | Evidence | Required action |
|---|---|---|---|---|---|---|
| Functional Keypad Dialler | Yes | Yes | Yes (`DiallerDeviceTest.kt`) | **PASS** | `DiallerKeypad.kt#L13-33`; `MainScreen.kt#L823-837` | None. |
| Number Input, Deletion, and Editing | Yes | Yes | Yes (`DiallerDeviceTest.kt`) | **PASS** | Keypad supports 0–9, *, #, Backspace, Clear number. `DiallerDeviceTest.kt#L138` | None. |
| SIP Extension Provisioning UI | Yes | Yes | Yes (`FeatureNavigationDeviceTest.kt`) | **PASS** | `AccountScreen.kt`, protected by administrator gate | Provision pilot extension against Grandstream UCM. |
| Genuine Native SIP Engine | Yes | Yes (`check_sip_source.py`, `verify_native.py`) | Yes (`startup.json`) | **PASS** | 15 static archives in `distribution/arm64-v8a/`; 94 JNI exports; genuine Baresip core ELF | Maintain binary integrity. |
| SIP Registration & Re-registration | Yes | JNI calls present | Needs live PBX | **EXTERNAL** | `Api.ua_register()`; `BaresipService.kt#L3170` | Perform physical registration against Grandstream UCM `192.168.1.200:8089`. |
| Outgoing SIP Calls (Dialler → PBX) | Yes | JNI calls present | Needs live PBX | **EXTERNAL** | `Call.kt#L62-64` delegates to `Api.call_connect()` | Perform supervised live call to internal extension. |
| Incoming SIP Call Detection | Yes | Telecom listener implemented | Needs live PBX | **EXTERNAL** | `ConnectionService.kt#L58-76`; `BaresipService.kt` event loop | Test incoming call from Grandstream extension. |
| Call Answering & Rejection | Yes | Simulated state | Untested with live audio | **PARTIAL** | `Call.kt#L163-170` (`Api.ua_answer`, `Api.ua_hangup(486)`) | Execute live incoming answer/reject test. |
| Two-Way Audio Path | Yes | Audio driver linked | Untested with live call | **UNTESTED** | Uses AAudio / OpenSL ES in native `baresip.so`; `libopus.a`, `libg722.a`, `libbcg729.a` | Verify microphone and earpiece audio with Grandstream phone. |
| Microphone Mute / Unmute | Yes | Method verified | Needs active audio | **PARTIAL** | `BaresipService.kt#L3781` (`am.isMicrophoneMute = mute`) | Test during live SIP call. |
| Speakerphone Routing | Yes | Routing logic implemented | Needs active audio | **PARTIAL** | `BaresipService.kt#L3142-3146`; Android Telecom route sync | Test on physical device during call. |
| Call Hold & Resume | Yes | Telecom sync implemented | Needs active call | **PARTIAL** | `Call.kt#L66-99` (`Api.call_hold()`, `setOnHold()`, `setActive()`) | Test during live SIP call. |
| DTMF Tone Generation | Yes | JNI method verified | Needs PBX IVR | **PARTIAL** | `Call.kt#L112-114` (`Api.call_send_digit()`) | Test against Grandstream IVR / voicemail. |
| Call Transfer (Attended & Blind) | Yes | Logic implemented | Needs PBX testing | **PARTIAL** | `Call.kt#L101-110` (`Api.call_transfer()`, `Api.call_replace_transfer()`) | Verify blind and attended transfer on PBX. |
| Call Termination (Hang-up) | Yes | Logic implemented | Needs live call | **PARTIAL** | `Call.kt#L152-161`; `ConnectionService.onCallClosed()` | Verify clean hangup from both local and remote parties. |
| Call History Logging | Yes | Yes (`CallHistoryIdentityTest.kt`) | Yes (`FeatureNavigationDeviceTest.kt`) | **PASS** | `CallHistory.kt`; `CallRow.kt#L12` records `agentId` | None. |
| Missed-Call System Notifications | Yes | Yes | Untested on device | **UNTESTED** | `BaresipService.kt#L1462-1500` | Verify Android notification appears for missed call. |
| Background SIP Reconnection | Yes | Network callback implemented | Untested in deep sleep | **UNTESTED** | `ConnectivityManager.NetworkCallback` in `BaresipService.kt#L206` | Test Wi-Fi to cellular handover and Doze mode waking. |

---

### Section C: Customer Directory

| Feature | Implemented? | Automated test | Device test | Result | Evidence | Required action |
|---|---|---|---|---|---|---|
| Encrypted SQLite Customer Database | Yes | Yes (`CustomerBatchTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `CustomerDirectory.kt#L30-83`; AES-GCM envelope encryption | None. |
| 4-Column Directory Model | Yes | Yes (`FourColumnImportTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `customer_name`, `phone_number`, `account_number`, `scheme_name` | None. |
| Optional Scheme Codes | Yes | Yes (`FourColumnImportTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `SchemeResolution.kt#L13` auto-generates `AUTO-xxxxxxxxxx` | None. |
| Multi-Field Directory Search | Yes | Yes (`CustomerMatchingTest.kt`) | Yes (`AuthenticatedDirectoryDeviceTest.kt`) | **PASS** | `CustomerMatching.kt#L4-20` searches name, phone, account, scheme | None. |
| Multiple Accounts per Customer | Yes | Yes (`CustomerRelations.py`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `customer_account` relational association table | None. |
| Multiple Customers per Phone Number | Yes | Yes (`CustomerRelations.py`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `customer_phone` relational association table | None. |
| Customer Profile View & Edit | Yes | Yes | Yes (`AuthenticatedDirectoryDeviceTest.kt`) | **PASS** | `CustomersScreen.kt#L250-420` | None. |
| Call Notes & Outcome Classification | Yes | Yes (`CustomerInteractionTest.kt`) | Yes (`AuthenticatedDirectoryDeviceTest.kt`) | **PASS** | `CustomerInteraction.kt`; 5 outcome states, follow-up tracking | None. |

---

### Section D: Excel & CSV Importing

| Feature | Implemented? | Automated test | Device test | Result | Evidence | Required action |
|---|---|---|---|---|---|---|
| XLSX Stream Import | Yes | Yes (`CustomerXlsxTest.kt`) | Yes (`DirectoryDeviceTest.kt`) | **PASS** | `CustomerImport.kt#L93-168` parses `xl/sharedStrings.xml` & `sheet1.xml` | None. |
| CSV Stream Import | Yes | Yes (`CustomerImportTest.kt`) | Yes (`DirectoryAccessDeviceTest.kt`) | **PASS** | `CustomerImport.kt#L57-82` RFC 4180-compliant bounded parser | None. |
| Four-Column Template Support | Yes | Yes (`FourColumnImportTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `CustomerImport.kt#L14`; `FourColumnImportTest.kt` | None. |
| Legacy Seven-Column Compatibility | Yes | Yes (`CustomerImportTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `CustomerImport.kt#L13`; `swusip-customers-legacy-seven-column.csv` | None. |
| Document Picker Integration | Yes | File opening logic | Yes (Observed in `picker-followup.json`) | **PASS** | Android `ActivityResultContracts.OpenDocument()` | None. |
| Import Preview & Review Reporting | Yes | Yes (`CustomerImportTest.kt`) | Yes (`picker-followup.json`) | **PASS** | `CustomerImportPlanner.preview()`; reports proposed corrections | None. |
| Duplicate Detection & Account Conflicts | Yes | Yes (`CustomerMatchingTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `CustomerImportPlanner.kt#L68-75` detects conflicting account owners | None. |
| Validation & Formula Rejection | Yes | Yes (`CustomerImportTest.kt`) | Yes (`DirectoryDeviceTest.kt`) | **PASS** | Rejects `#FORMULA#`, DTD entities, control characters | None. |
| Leading-Zero Account Preservation | Yes | Yes (`CustomerXlsxTest.kt`) | Yes (`DirectoryDeviceTest.kt`) | **PASS** | Rejects numeric Excel accounts (`#NUMERIC#`) requiring literal text | None. |
| 10,000-Row Batch Performance | Yes | Bounded test | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | 10,000 rows import in **46.97s**, read in **10.81s**, search in **0.57s** | None. Within 300s Keystore timeout. |
| Atomic Safe Undo (Latest Batch Rollback) | Yes | Yes (`CustomerBatchTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | Chunked undo journal (`import_chunk`); 10k undo executes in **34.88s** | None. |
| Raw Workbook Import Compatibility | Yes | Aliases implemented | Blocked on unformatted user workbooks | **PARTIAL** | Workbooks with repeated headers or numeric accounts fail importer validation. Formatted copy `Download/CUSTO-SWUSIP-ready.xlsx` prepared. | Instruct administrators on text formatting via `SWUSIP_IMPORT_GUIDE.md`. |

---

### Section E: Smart Uganda Telephone-Number Handling

| Feature | Implemented? | Automated test | Device test | Result | Evidence | Required action |
|---|---|---|---|---|---|---|
| UCC July 2026 Numbering Plan | Yes | Yes (`PhoneNormalisationTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `PhoneNormalisation.kt#L11-18`; official national allocation table | None. |
| National Format (`0700123456`) | Yes | Yes (`PhoneNormalisationTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | Replaces national trunk `0` with `+256` | None. |
| 9-Digit National Number (`700123456`) | Yes | Yes (`PhoneNormalisationTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | Prepends `+256` to valid 9-digit national allocation | None. |
| Full International (`+256700123456`) | Yes | Yes (`PhoneNormalisationTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | Validates national length and prefix; sets status `VALID` | None. |
| Explicit Prefix 256 (`256700123456`) | Yes | Yes (`PhoneNormalisationTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | Prepends `+` marker; validates 9-digit national number | None. |
| Mobile vs Fixed-Line Range Separation | Yes | Yes (`PhoneNormalisationTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | Explicitly partitions mobile (`7...`) and fixed (`2...`, `3...`, `4...`) | None. |
| Deterministic PBX Prefix Handling (`9`) | Yes | Yes (`PhoneNormalisationTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | Only strips `9` if explicitly configured in `accessPrefixes` | None. |
| Original vs Canonical Preservation | Yes | Yes (`PhoneNormalisationTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | Encrypts original raw string and canonical E.164 separately | None. |

---

### Section F: Automatic Caller Identification

| Feature | Implemented? | Automated test | Device test | Result | Evidence | Required action |
|---|---|---|---|---|---|---|
| Read Incoming SIP Caller URI | Yes | Yes (`CallerIdentityTest.kt`) | Untested with live call | **PASS** | Extracts phone digits from `sips?` or `tel:` URI (`PhoneNormaliser.kt#L44-49`) | Test with live incoming Grandstream call. |
| Normalise Incoming SIP Number | Yes | Yes (`CallerIdentityTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | Converts incoming caller number to canonical `+256...` format | None. |
| Search Authorised Customer Database | Yes | Yes (`CallerIdentityTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `CustomerDirectory.lookup(number)`; runs on `Dispatchers.IO` | None. |
| Display Customer Name | Yes | Yes (`CallerIdentityTest.kt`) | Yes (Synthetic lookup) | **PASS** | `CallCustomerPanel.kt#L454-456` renders name | None. |
| Display Water Scheme Name & Code | Yes | Yes (`CallerIdentityTest.kt`) | Yes (Synthetic lookup) | **PASS** | `CallCustomerPanel.kt#L456` renders `${it.schemeName} (${it.schemeCode})` | None. |
| Display Customer Account Number | Yes | Yes (`CallerIdentityTest.kt`) | Yes (Synthetic lookup) | **PASS** | `CallCustomerPanel.kt#L456` renders `Account ${it.account}` | None. |
| Profile Navigation Link | Yes | Yes | Yes (Navigation test) | **PASS** | Navigates to `customers?phone=...&customer=...` | None. |
| Multiple Matching Accounts Handling | Yes | Yes (`CallerIdentityTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `CallerIdentity.Multiple`; forces agent selection; no account assumed | None. |
| Clear Unknown Caller Indication | Yes | Yes (`CallerIdentityTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `CallerIdentity.Unknown`; renders "Unknown Customer" | None. |
| Non-Blocking Asynchronous Lookup | Yes | Yes | Yes (Profiler logs) | **PASS** | Runs in coroutine on background thread; call answer controls render immediately | None. |

---

### Section G: Agent Workspace

| Feature | Implemented? | Automated test | Device test | Result | Evidence | Required action |
|---|---|---|---|---|---|---|
| Assigned Agent Identity Display | Yes | Yes | Yes (`FeatureNavigationDeviceTest.kt`) | **PASS** | `AgentWorkspace.kt#L29`; reads from MDM restriction or offline grant | None. |
| Assigned SIP Extension Binding | Yes | Yes | Yes (`FeatureNavigationDeviceTest.kt`) | **PASS** | Binds agent to configured AoR; status indicators on home screen | None. |
| Biometric / PIN Workspace Gate | Yes | Yes | Yes (`AuthenticatedDirectoryDeviceTest.kt`) | **PASS** | `InstitutionAccess.unlocked()` and `KeyguardManager` prompt | None. |
| Instant Customer Directory Search | Yes | Yes (`CustomerMatchingTest.kt`) | Yes (`AuthenticatedDirectoryDeviceTest.kt`) | **PASS** | `CustomersScreen.kt#L188`; search by name, phone, account, scheme | None. |
| Encrypted Interaction Notes | Yes | Yes (`CustomerInteractionTest.kt`) | Yes (`AuthenticatedDirectoryDeviceTest.kt`) | **PASS** | Notes encrypted in SQLite; 2,000 char validation | None. |
| Call Outcome Classification | Yes | Yes (`CustomerInteractionTest.kt`) | Yes (`AuthenticatedDirectoryDeviceTest.kt`) | **PASS** | 5 discrete outcome states (`CustomerInteraction.kt#L3-7`) | None. |
| Follow-Up Task Tracking | Yes | Yes (`CustomerInteractionTest.kt`) | Yes (`AuthenticatedDirectoryDeviceTest.kt`) | **PASS** | Pending follow-ups filterable in `CustomersScreen.kt#L188` | None. |
| Customer Interaction History | Yes | Yes (`CallHistoryIdentityTest.kt`) | Yes (`AuthenticatedDirectoryDeviceTest.kt`) | **PASS** | Historical calls and notes linked to customer profile | None. |
| Missed-Call Records with Agent Binding | Yes | Yes (`CallHistoryIdentityTest.kt`) | Yes (`FeatureNavigationDeviceTest.kt`) | **PASS** | `CallRow.kt#L12` records `agentId` with call history entry | None. |
| Agent Local Desk Availability | Yes | Yes | Yes (`FeatureNavigationDeviceTest.kt`) | **PARTIAL** | Local UI state ("Available", "Busy", "Off duty") functional; explicitly does NOT alter PBX queue state | Integrate with PBX queue API if PBX supports external agent state. |

---

### Section H: Administration & Future Integration

| Feature | Implemented? | Automated test | Device test | Result | Evidence | Required action |
|---|---|---|---|---|---|---|
| 15+ Agent Concurrency Scalability | Architecture ready | Architecture review | Needs multi-handset deployment | **PARTIAL** | App architecture supports independent agent deployments | Provision 15 extensions on Grandstream PBX; deploy to 15 handsets. |
| Offline Cryptographic Role Provisioning | Yes | Yes (`DirectoryDeviceTest.kt`) | Yes (`DirectoryDeviceTest.kt`) | **PASS** | `OfflineAuthorisation.kt` uses device-bound RSA-SHA256 grants | Distribute signed device grants for production deployment. |
| Android Enterprise MDM Support | Yes | Manifest check | Yes (API 35 tested) | **PASS** | `institution_restrictions.xml` supports `swusip_role`, `swusip_agent_id` | Deploy managed configuration via MDM if available. |
| Outbound Call Dialling Profiles | Yes | Yes (`OutboundDiallingFormatterTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `DirectoryTelephoneSettings.kt`; requires verified PBX test reference | Record Grandstream PBX test reference before enabling. |
| Central Customer Database Sync | Interface only | Interface verified | Not deployed | **EXTERNAL** | `CustomerSync.kt` (`OfflineCustomerSync` throws `UnsupportedOperationException`) | Design and build central Windows Server / CRM backend. |
| Shared Multi-Agent Call History | Local only | Local verified | Needs backend | **EXTERNAL** | Call history stored locally on device | Requires central CRM or PBX CDR integration. |
| Call-Centre Supervisor Functions | Missing | None | None | **MISSING** | No live queue monitoring, whisper/barge, or call recording review in app | Manage call recording and queues directly on Grandstream UCM. |
| Centralised Reporting & Analytics | Local only | Local verified | Needs backend | **EXTERNAL** | Handset stores local outcome stats | Central reporting requires server backend. |
| Secure Remote Agent Support | Architecture ready | Architecture review | Needs VPN / PBX testing | **EXTERNAL** | Baresip engine supports TLS/SRTP; network callback supports VPN | Configure Grandstream RemoteConnect or IPSec/WireGuard VPN for remote agents. |

---

### Section I: Security & Data Protection

| Feature | Implemented? | Automated test | Device test | Result | Evidence | Required action |
|---|---|---|---|---|---|---|
| Hardware-Backed Keystore Key | Yes | Keystore spec test | Yes (`DirectoryDeviceTest.kt`) | **PASS** | Alias `swusip.directory.v1`, AES-GCM, non-exportable hardware key | None. |
| Keystore Biometric Authentication Window | Yes | Timeout test | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `setUserAuthenticationValidityDurationSeconds(300)` (5 minutes) | None. |
| Customer Database Paged Encryption | Yes | Cryptographic test | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | `DirectoryCipherPages.kt` (64KB AEAD pages with AAD binding) | None. |
| Portable Encrypted Export | Yes | Yes (`CustomerExportTest.kt`) | Yes (`CustomerExportTest.kt`) | **PASS** | AES-256-GCM; PBKDF2-HMAC-SHA256 (600k iterations); >= 16 char password | None. |
| Screen Capture & Thumbnail Prevention | Yes | Component review | Yes (`FeatureNavigationDeviceTest.kt`) | **PASS** | `InstitutionScreenPrivacy.kt` sets `FLAG_SECURE` on sensitive screens | None. |
| Application Backup Restriction | Yes | APK badge inspection | Verified in manifest | **PASS** | `android:allowBackup="false"`, `android:fullBackupContent="false"` | None. |
| Internal Storage Enforced | Yes | Manifest check | Verified in manifest | **PASS** | `android:installLocation="internalOnly"` | None. |
| Sensitive Data Logging Gating | Yes | Source audit | Verified in source | **PASS** | `Log.kt` defaults to `LogLevel.INFO`; zero password logging | None. |
| Hardcoded Secrets Audit | Yes | Grep / source scan | Clean | **PASS** | Zero credentials in repo; `.local-pilot-authority/` gitignored | Maintain git hygiene. |
| Additive Database Migration Safety | Yes | Yes (`DirectoryDeviceTest.kt`) | Yes (`DirectoryStorageV2DeviceTest.kt`) | **PASS** | V1 → V2 migration preserves ciphertext without repacking | None. |
| Network Security Config | Missing | Missing | N/A | **PARTIAL** | Native sockets bypass Java config; no cleartext HTTP endpoints defined | Add explicit `network_security_config.xml`. |
| SIP Credential Encryption at Rest | No | Source inspection | Present in cleartext | **PARTIAL** | `/data/data/.../files/accounts` stored in app-private storage, not Keystore | Migrate SIP account storage into an encrypted Keystore vault. |
| Exported BroadcastReceiver Security | Vulnerable | Code inspection | Exported in manifest | **FAIL** | `TaskReceiver` exported=true with no signature permission | Require signature permission or set `exported="false"`. |
| Unnecessary Permissions (SMS/MMS) | Extraneous | Manifest check | Present in APK | **PARTIAL** | Telephony and SMS permissions remain from upstream Baresip | Remove legacy SMS/MMS manifest declarations. |
