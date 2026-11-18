# SWUSIP Physical-Device Manual Verification Checklist

**Document:** SWUSIP_MANUAL_TEST_CHECKLIST.md  
**Target:** Authorized Android Test Handset (e.g., Xiaomi 25028RN03A, Android 15)  
**Application ID:** `ug.go.mwe.swusip`  
**Version:** `1.0.6-pilot`  
**Purpose:** Structured manual acceptance testing protocol for the institutional owner and technical leads.  

---

## 1. Branding & Navigation

### Test 1.1: Home Screen Identity & Palette
- **Screen:** Home Screen (Launch SWUSIP)
- **Action:** Open application from Android launcher.
- **Data to Enter:** None.
- **Expected Behaviour:** Header displays "Ministry of Water and Environment, Uganda"; title shows "SWUSIP"; bottom navigation shows Dialler, Contacts, Messages (hidden/inactive), Settings; institutional blue and ink palette is applied.
- **Failure Symptoms:** Upstream "Baresip" logo/text visible on home screen; generic Android styling.
- **Evidence to Capture:** Photo of handset screen.
- **Requires PBX Access:** No.

### Test 1.2: Institutional About & Legal Screen
- **Screen:** Settings → About SWUSIP
- **Action:** Tap "Settings" in top bar, scroll down, tap "About SWUSIP". Tap "Legal information".
- **Data to Enter:** None.
- **Expected Behaviour:** Displays "SWUSIP - South Western Umbrella Call Centre", "South Western Umbrella of Water and Sanitation", "Version 1.0.6-pilot". Legal information displays open-source notices preserving BSD/Apache/GPL copyrights.
- **Failure Symptoms:** Missing institutional name; crash on opening legal text.
- **Evidence to Capture:** Screen photo.
- **Requires PBX Access:** No.

---

## 2. Dialler & Keypad

### Test 2.1: Keypad Input & Editing
- **Screen:** Home Screen (Dialler view)
- **Action:** Tap digits `0`, `7`, `0`, `0`, `1`, `2`, `3`, `4`, `5`, `6`, `*`, `#`. Tap "Backspace" twice. Tap "Clear number".
- **Data to Enter:** `0700123456*#`.
- **Expected Behaviour:** Number input field reflects exact button taps in real time. Backspace deletes characters from right. Clear number clears the field completely.
- **Failure Symptoms:** Buttons unresponsive; digits entered out of order; keypad truncated on small screen.
- **Evidence to Capture:** Video or sequential photos of keypad.
- **Requires PBX Access:** No.

### Test 2.2: Unprovisioned Call Feedback
- **Screen:** Home Screen (Dialler view)
- **Action:** Enter `101` and tap the green Call button (with no SIP account provisioned).
- **Data to Enter:** `101`.
- **Expected Behaviour:** App displays a clear prompt explaining that no SIP extension is configured and prompts the agent to contact their administrator. No crash occurs.
- **Failure Symptoms:** App crashes; silent failure with no user feedback.
- **Evidence to Capture:** Screen photo of toast/dialog.
- **Requires PBX Access:** No.

---

## 3. Settings & Agent Administration

### Test 3.1: Agent Settings vs Administrator Screen Gate
- **Screen:** Settings Screen
- **Action:** Tap "Settings". Observe available options.
- **Data to Enter:** None.
- **Expected Behaviour:** An ordinary agent sees "Microphone and audio", "Notification preferences", "Android app and battery settings". "Administrator settings" and "Extension provisioning" remain hidden or locked behind device credential.
- **Failure Symptoms:** Administrative controls exposed to unauthenticated users.
- **Evidence to Capture:** Screen photo.
- **Requires PBX Access:** No.

### Test 3.2: Audio & Audio Route Settings
- **Screen:** Settings → Microphone and audio
- **Action:** Tap "Microphone and audio", inspect audio codec priorities and ringtone selection. Tap Back.
- **Data to Enter:** None.
- **Expected Behaviour:** Lists available codecs (Opus, PCMA, G.722, G.729); allows setting ringtone. Navigating back preserves settings without error.
- **Failure Symptoms:** Crash on opening audio settings; codec list empty.
- **Evidence to Capture:** Screen photo.
- **Requires PBX Access:** No.

---

## 4. Customer Directory Import

### Test 4.1: Four-Column Template Stream Import
- **Screen:** Customers → Import Customer Directory
- **Action:** Authenticate workspace with device PIN/biometric. Tap "Import Customer Directory". Select `swusip-customers.xlsx` or `Download/CUSTO-SWUSIP-ready.xlsx` from Android file picker.
- **Data to Enter:** Authorised text-formatted four-column spreadsheet.
- **Expected Behaviour:** File picker loads; import preview appears showing total records, proposed telephone normalisations, detected new schemes, and zero account truncation errors. Tapping "Confirm Import" stores records in encrypted SQLite.
- **Failure Symptoms:** File picker crashes; "Format account numbers as Text" error if raw numeric accounts are supplied; Keystore timeout.
- **Evidence to Capture:** Screen photo of preview and post-import summary.
- **Requires PBX Access:** No.

### Test 4.2: Latest-Batch Atomic Undo
- **Screen:** Customers → Import History / Undo
- **Action:** After importing a batch, tap "Undo Latest Batch".
- **Data to Enter:** None.
- **Expected Behaviour:** Reversible batch rollback executes atomically. Customer records, new schemes, and associations introduced in that batch are safely removed; pre-existing records remain untouched.
- **Failure Symptoms:** CursorWindow crash; foreign-key constraint violation; partial data deletion.
- **Evidence to Capture:** Screen photo showing successful rollback toast.
- **Requires PBX Access:** No.

---

## 5. Customer Directory Search

### Test 5.1: Multi-Field Customer Query
- **Screen:** Customers Screen
- **Action:** Tap Search bar. Enter:
  1. A customer name (e.g. "Mwesigye").
  2. A phone number (e.g. "0772123456").
  3. An account number (e.g. "10452").
  4. A water scheme name (e.g. "Kabale Central").
- **Data to Enter:** Test search strings.
- **Expected Behaviour:** Results filter instantly (under 100 ms); matching records display customer name, canonical phone, account number, and scheme name.
- **Failure Symptoms:** Search freezes UI thread; leading zeroes missing from displayed account numbers.
- **Evidence to Capture:** Screen photos of search results for each query type.
- **Requires PBX Access:** No.

---

## 6. Water Scheme Management

### Test 6.1: Automatic Scheme Resolution
- **Screen:** Customers → Scheme Filter
- **Action:** Open scheme filter dropdown.
- **Data to Enter:** None.
- **Expected Behaviour:** Lists all water supply schemes loaded from imported records. Schemes imported without an explicit code display auto-generated code `AUTO-xxxxxxxxxx`. Selecting a scheme filters customer records accurately.
- **Failure Symptoms:** Scheme list empty; duplicate conflicting entries for the same scheme.
- **Evidence to Capture:** Screen photo of scheme dropdown.
- **Requires PBX Access:** No.

---

## 7. Customer Profiles & Interaction Notes

### Test 7.1: Add Call Note & Outcome Classification
- **Screen:** Customer Profile Screen
- **Action:** Tap on a customer record. Tap "Add Interaction Note". Select outcome "Information given". Enter text: "Customer enquired about water tariff in Kabale Central scheme." Tap "Save Note".
- **Data to Enter:** Note text (50 characters), Outcome: `INFORMATION_GIVEN`.
- **Expected Behaviour:** Note is saved, encrypted in SQLite, and rendered in the customer's chronological interaction timeline with timestamp and current agent identity.
- **Failure Symptoms:** Note fails to save; character validation fails; plaintext note visible in SQLite dumps.
- **Evidence to Capture:** Screen photo of customer interaction history.
- **Requires PBX Access:** No.

---

## 8. Agent Authentication & Session Security

### Test 8.1: 5-Minute Session Expiration Gate
- **Screen:** Customers / Protected Workspace
- **Action:** Unlock workspace via fingerprint/PIN. Leave phone idle for 5 minutes and 15 seconds. Tap any customer record.
- **Data to Enter:** None.
- **Expected Behaviour:** Workspace re-locks; screen prompts: "Authenticate to access the institutional workspace" (`KeyguardManager` prompt). Customer data is withheld until re-authentication succeeds.
- **Failure Symptoms:** Customer data accessible after 5 minutes without re-authenticating.
- **Evidence to Capture:** Screen photo showing re-authentication prompt.
- **Requires PBX Access:** No.

### Test 8.2: Screen Capture Blocking (`FLAG_SECURE`)
- **Screen:** Customers Screen (with records displayed)
- **Action:** Attempt to take an Android screenshot (Power + Volume Down) or capture screen via ADB.
- **Data to Enter:** None.
- **Expected Behaviour:** Android OS displays: "Couldn't save screenshot. Taking screenshots isn't allowed by the app or your organization." ADB screencap produces a black/blank image.
- **Failure Symptoms:** Screenshot captures visible customer PII.
- **Evidence to Capture:** OS denial message or blank ADB capture.
- **Requires PBX Access:** No.

---

## 9. Call History & Missed Calls

### Test 9.1: Call History with Agent Identity
- **Screen:** Top bar → Call History
- **Action:** Tap Call History icon. Inspect call rows.
- **Data to Enter:** None.
- **Expected Behaviour:** Lists previous incoming and outgoing calls with timestamp, duration, peer URI, direction icon, and assigned `agentId`.
- **Failure Symptoms:** History empty despite prior calls; agent identity missing.
- **Evidence to Capture:** Screen photo.
- **Requires PBX Access:** No.

---

## 10. Automatic Caller Identification

### Test 10.1: Incoming SIP Call Customer Matching
- **Screen:** Incoming Call Screen / Active Call Card
- **Action:** Trigger an incoming SIP call from an extension or telephone matching a customer in the database (e.g. `+256700123456`).
- **Data to Enter:** Incoming call.
- **Expected Behaviour:** CallCard displays incoming call. Underneath, `CallCustomerPanel` displays:
  - Customer Name
  - Account Number (with leading zeroes intact)
  - Water Scheme Name
  - "Open customer profile" button.
  **Audio ringing and answer buttons remain responsive immediately without lag.**
- **Failure Symptoms:** Caller name does not appear; ringing is delayed; app crashes on lookup.
- **Evidence to Capture:** Photo of handset during active incoming call showing customer panel.
- **Requires PBX Access:** **Yes (Grandstream UCM)**.

### Test 10.2: Unknown Caller Indication
- **Screen:** Incoming Call Screen
- **Action:** Trigger incoming SIP call from an unlisted extension (e.g. `199`).
- **Data to Enter:** Unlisted caller.
- **Expected Behaviour:** Displays "Unknown Customer" and offers a "Search customer directory" button.
- **Failure Symptoms:** App shows blank or displays an incorrect customer record.
- **Evidence to Capture:** Photo of screen.
- **Requires PBX Access:** **Yes**.

---

## 11. Live SIP Calling & PBX Integration

### Test 11.1: SIP Registration against Grandstream UCM
- **Screen:** Home Screen
- **Action:** In Administrator Settings → Extension Provisioning, configure:
  - Display Name: `Agent 1`
  - SIP User / Extension: `101`
  - Authentication Password: `<PBX Extension Secret>`
  - Domain / IP: `192.168.1.200:8089` (or `5060`)
  - Transport: `UDP` (or `TLS`)
  Save and return to Home Screen.
- **Data to Enter:** Grandstream extension credentials.
- **Expected Behaviour:** SIP status indicator changes from yellow ("Connecting") to green ("Connected"). On Grandstream Web GUI, Extension `101` status changes to Registered (green icon).
- **Failure Symptoms:** Indicator stays red ("Registration failed") or yellow; SIP 401 Unauthorized loops.
- **Evidence to Capture:** Screen photo of green "Connected" indicator; PBX web GUI status capture.
- **Requires PBX Access:** **Yes**.

### Test 11.2: Extension-to-Extension Two-Way Audio Call
- **Screen:** Dialler
- **Action:** From SWUSIP extension `101`, enter `102` (Grandstream desk telephone) and tap Call. Answer on desk phone. Speak into handset; listen to earpiece. Tap red Hangup button.
- **Data to Enter:** `102`.
- **Expected Behaviour:** Desk phone rings immediately. Upon answering, clean two-way audio is established with no severe jitter or echo. Tapping Hangup cleanly terminates call on both ends.
- **Failure Symptoms:** One-way audio; silence; immediate call drop; failure to hang up.
- **Evidence to Capture:** Grandstream CDR record; audio call duration confirmation.
- **Requires PBX Access:** **Yes**.

### Test 11.3: Audio Controls (Mute, Speakerphone, Hold, DTMF)
- **Screen:** Active Call Card
- **Action:** During an active call with extension `102`:
  1. Tap Mute icon. Verify desk phone user hears silence. Tap Mute again.
  2. Tap Speaker icon. Verify audio routes to handset loudspeaker.
  3. Tap Hold icon. Verify desk phone hears music-on-hold. Tap Resume.
  4. Tap Keypad, press `5`. Verify desk phone detects DTMF tone.
- **Data to Enter:** Audio control actions.
- **Expected Behaviour:** All four controls function as expected and sync state with Android Telecom.
- **Failure Symptoms:** Audio routes incorrectly; hold drops call; DTMF fails to transmit.
- **Evidence to Capture:** Audio test notes.
- **Requires PBX Access:** **Yes**.

---

## 12. Background Reliability & Network Handoff

### Test 12.1: Background Incoming Call Wakeup
- **Screen:** Handset Locked / Screen Off
- **Action:** Lock the phone screen. Leave in pocket for 5 minutes. Place a call to extension `101` from extension `102`.
- **Data to Enter:** Incoming call while locked.
- **Expected Behaviour:** Phone wakes from standby; screen turns on showing incoming call Telecom heads-up notification with Answer and Decline actions. Ringtone plays.
- **Failure Symptoms:** Phone does not ring until manually unlocked; missed call notification delayed.
- **Evidence to Capture:** Verification that call wakes locked handset.
- **Requires PBX Access:** **Yes**.

### Test 12.2: Wi-Fi to Mobile Data Transition
- **Screen:** Home Screen (Connected)
- **Action:** While connected via office Wi-Fi, turn off Wi-Fi (allowing mobile data / cellular connection to take over).
- **Data to Enter:** None.
- **Expected Behaviour:** `BaresipService` network callback detects link property change; updates local socket addresses via `Api.net_add_address_ifname()`; re-registers extension `101`. Indicator returns to green "Connected" within 5 seconds.
- **Failure Symptoms:** App stays stuck in "Registration failed" until app is restarted.
- **Evidence to Capture:** Time elapsed before re-registration.
- **Requires PBX Access:** **Yes**.
