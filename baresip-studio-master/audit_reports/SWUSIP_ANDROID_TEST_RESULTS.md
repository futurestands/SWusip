# SWUSIP Automated Test Results & Build Verification

**Document:** SWUSIP_ANDROID_TEST_RESULTS.md  
**Repository:** `https://github.com/futurestands/SWusip`  
**Branch:** `feature/swusip-call-centre`  
**Commit:** `7dc418ee045581f9d24692c6c5a0ce65a81faa54`  
**Application ID:** `ug.go.mwe.swusip`  
**Version:** `1.0.6-pilot` (Version Code: `592`)  
**Auditor:** Independent Software Quality Assurance Specialist  

---

## 1. Executive Test Summary

| Test Domain | Executed | Passed | Failed | Skipped | Status | Evidence Source |
|---|---|---|---|---|---|---|
| **JVM Unit Tests** | 69 | 69 | 0 | 0 | **100% PASS** | `app/build/test-results/testDebugUnitTest/*.xml` |
| **Python Integrity & Privacy** | 14 | 14 | 0 | 0 | **100% PASS** | Direct CLI execution in environment |
| **Native Library Symbols & ABI** | 15 | 15 | 0 | 0 | **100% PASS** | `scripts/verify_native.py` output |
| **Native JNI Source Surface** | 94 exports | 94 | 0 | 0 | **100% PASS** | `scripts/check_sip_source.py` output |
| **Packaged APK Inspection** | 1 APK | 1 | 0 | 0 | **100% PASS** | `scripts/verify_apk.py` output |
| **Android Lint Analysis** | Full Project | 0 errors | 0 errors | 84 warnings | **PASS** | `app/build/reports/lint-results-debug.txt` |
| **Physical-Device Tests (Xiaomi 25028RN03A)** | 17 cases | 17 | 0 | 0 | **100% PASS** | `docs/evidence/import-v2/verification.json` |
| **Live SIP Audio / Grandstream PBX Call** | 0 | 0 | 0 | 0 | **EXTERNAL** | Requires on-premise PBX connection |

---

## 2. JVM Unit Test Suite Breakdown

All 69 unit tests run host-side on the JVM without requiring an Android emulator or physical device. All 69 tests executed with **0 failures, 0 errors, and 0 skipped**:

| Unit Test Suite | Class Name | Tests | Failures | Errors | Execution Time | Functional Coverage |
|---|---|---|---|---|---|---|
| **Caller Identity** | `CallerIdentityTest` | 5 | 0 | 0 | 0.052 s | Incoming caller matching, single match, multiple matches, unknown caller, phone number extraction from SIP URI. |
| **Call History Identity** | `CallHistoryIdentityTest` | 2 | 0 | 0 | 0.262 s | Call history records retain assigned `agentId` attribution; multi-account history partitioning. |
| **Customer Batch** | `CustomerBatchTest` | 5 | 0 | 0 | 3.192 s | Bulk customer insertion, relational scheme and account resolution, batch commit performance. |
| **Customer Export** | `CustomerExportTest` | 3 | 0 | 0 | 4.828 s | Portable spreadsheet export encryption (AES-256-GCM, PBKDF2 600k iterations), password validation, tamper detection. |
| **Customer Import** | `CustomerImportTest` | 10 | 0 | 0 | 0.020 s | CSV stream parser RFC 4180 compliance, column aliases, row limits, control character rejection, formula rejection. |
| **Customer Interaction** | `CustomerInteractionTest` | 7 | 0 | 0 | 0.011 s | Call notes validation (1–2000 chars), 5 outcome classification states, callback follow-up enforcement. |
| **Customer Matching** | `CustomerMatchingTest` | 9 | 0 | 0 | 0.015 s | Multi-field search (name, phone, account, scheme), account conflicts, duplicate skipping, category backfilling. |
| **Customer XLSX** | `CustomerXlsxTest` | 6 | 0 | 0 | 0.208 s | Excel XML streaming, text cell leading-zero preservation, rejection of numeric-formatted account cells (`#NUMERIC#`). |
| **Four-Column Import** | `FourColumnImportTest` | 9 | 0 | 0 | 0.062 s | 4-column simplified template parsing (`customer_name`, `phone_number`, `account_number`, `scheme_name`), automatic scheme code generation. |
| **Outbound Dialling** | `OutboundDiallingFormatterTest` | 4 | 0 | 0 | 0.012 s | Outbound number formatting (Uganda National, E.164, Country Code, PBX Access Prefix 9). |
| **Phone Normalisation** | `PhoneNormalisationTest` | 9 | 0 | 0 | 0.028 s | UCC 2026 numbering plan compliance, mobile vs fixed ranges, trunk 0 replacement, international prefix handling. |
| **TOTALS** | **11 Suites** | **69** | **0** | **0** | **8.690 s** | **100% Pass Rate** |

---

## 3. Python Test & Verification Script Results

Executed host-side during this audit turn:

```powershell
python scripts/check_sip_source.py
# PASS: 94 JNI exports; all essential calls delegate to the native engine.

python scripts/test_customer_relations.py
# Ran 2 tests in 0.011s -> OK

python scripts/test_device_privacy.py
# Ran 6 tests in 0.003s -> OK

python scripts/test_source_integrity.py
# Ran 2 tests in 0.215s -> OK

python scripts/test_translations.py
# Ran 4 tests in 2.691s -> OK

python scripts/verify_native.py --tools "<NDK>/llvm/prebuilt/windows-x86_64/bin"
# PASS openssl/lib/arm64-v8a/libcrypto.a members=976
# PASS openssl/lib/arm64-v8a/libssl.a members=97
# PASS re/lib/arm64-v8a/libre.a members=239
# PASS baresip/lib/arm64-v8a/libbaresip.a members=95
# PASS opus/lib/arm64-v8a/libopus.a members=142
# PASS g722/lib/arm64-v8a/libg722.a members=2
# PASS g7221/lib/arm64-v8a/libg722_1.a members=16
# PASS g729/lib/arm64-v8a/libbcg729.a members=26
# PASS ilbc/lib/arm64-v8a/libilbc.a members=23
# PASS codec2/lib/arm64-v8a/libcodec2.a members=63
# PASS amr/lib/arm64-v8a/libamrnb.a members=153
# PASS amr/lib/arm64-v8a/libamrwb.a members=40
# PASS amr/lib/arm64-v8a/libamrwbenc.a members=49
# PASS gzrtp/lib/arm64-v8a/libzrtpcppcore.a members=71
# PASS sndfile/lib/arm64-v8a/libsndfile.a members=71

python scripts/verify_apk.py artifacts/SWUSIP-1.0.6-pilot-debug.apk --aapt "<SDK>/build-tools/35.0.0/aapt.exe" --tools "<NDK>/llvm/prebuilt/windows-x86_64/bin"
# PASS APK identity, arm64 ELF and required dynamic JNI exports
# APK SHA256: 77fd8abf77f75bf74eb02327d91507cf829f37061a7a57106f80d4e106573ac1
```

---

## 4. Android Lint Report Analysis

The Android Lint run produced **0 Errors and 84 Warnings**.

### Warning Distribution:
1. **Target API Notice (1):** `targetSdk = 36` (Advises evaluating newer Android API levels).
2. **Gradle / Dependency Version Updates (2):** Advises updating Gradle wrapper (9.6.0 → 9.9.1) and Kotlinx serialization (1.12.0 → 1.13.0).
3. **Hardcoded Text Attributes (8):** Hardcoded titles in XML resources (e.g., `institution_restrictions.xml`).
4. **Localization Formatting Strings (73):** String formatting warnings in secondary language translations (`values-cs/`, `values-sv/`, `values-zh-rCN/`) inherited from upstream Baresip. English base strings (`values/strings.xml`) are completely clean.

**Conclusion:** Zero blocking lint errors exist. No lint rules were suppressed or disabled.

---

## 5. Physical-Device Test Evidence (Xiaomi 25028RN03A, Android 15)

The physical device validation records in `docs/evidence/import-v2/verification.json` establish that **17 distinct on-device test cases passed**:

### 5.1 Distinct Passed Device Test Cases
1. `DirectoryDeviceTest.forgedPhoneAuthorisationCannotGrantAdministrator` — **PASS**
2. `DirectoryDeviceTest.freshDatabaseReopensAndUnknownMigrationPreservesRecords` — **PASS**
3. `DirectoryDeviceTest.excelFormulaAccountsAreRejected` — **PASS**
4. `DirectoryDeviceTest.excelNumericAccountsAreRejectedRatherThanInventingLostZeroes` — **PASS**
5. `DirectoryDeviceTest.excelTextAccountsKeepLeadingZeroes` — **PASS**
6. `DirectoryDeviceTest.documentDeclarationsRemainRejectedOnAndroid` — **PASS**
7. `DirectoryDeviceTest.suppliedExcelTemplateHasCorrectColumnsAndNoCustomers` — **PASS**
8. `DirectoryAccessDeviceTest.tenThousandCsvRecordsParseOnAndroid` — **PASS**
9. `DirectoryAccessDeviceTest.lockedWorkspaceDeniesRepositoryOperations` — **PASS**
10. `DiallerDeviceTest.keypadEditingAndUnprovisionedCallFeedback` — **PASS**
11. `DirectoryStorageV2DeviceTest.legacySchemaPayloadsAndOversizedJournalRemainReadable` — **PASS**
12. `DirectoryStorageV2DeviceTest.fourColumnsKeepOriginalsResolveSchemesReviewInvalidAndUndo` — **PASS**
13. `DirectoryStorageV2DeviceTest.pagedCipherAndReferenceTamperingNeverReturnAnotherCustomer` — **PASS**
14. `DirectoryStorageV2DeviceTest.journalTamperingIsAtomicAndOptionalCategoryFillCanBeReversed` — **PASS**
15. `DirectoryStorageV2DeviceTest.boundedPagedImportReadSearchAndUndoProfile` — **PASS**
16. `AuthenticatedDirectoryDeviceTest.realAuthorisationEncryptedImportSearchNotesAndUndo` — **PASS**
17. `AuthenticatedDirectoryDeviceTest.authenticatedCustomerAndSchemeNavigation` — **PASS**

### 5.2 Measured Encrypted Storage Timings on Android 15 Device

| Dataset Size | Import Time | Read Time | Caller Lookup Time | Account Search Time | Undo Rollback Time |
|---|---|---|---|---|---|
| **1,000 Records** | 5.004 s | 1.169 s | **0.006 s** (6 ms) | 0.138 s | 3.272 s |
| **10,000 Records** | 46.972 s | 10.814 s | **0.058 s** (58 ms) | 0.573 s | 34.876 s |

Both benchmark runs completed comfortably within the 300-second hardware Android Keystore biometric session window without triggering authentication expiration.

---

## 6. Environmental Testing Limitations

The following tests could not be executed during this autonomous audit turn:
1. **Live SIP Registration & Call Audio against Grandstream UCM:**
   - **Reason:** No Android physical phone is currently attached to ADB (`adb devices` returned 0 devices), and the build environment is outside the institutional local area network (`192.168.1.0/24`).
2. **Outbound Calling to MTN Public Mobile Numbers:**
   - **Reason:** Requires physical extension provisioning on the live Grandstream PBX and active MTN SIP trunk routing.
