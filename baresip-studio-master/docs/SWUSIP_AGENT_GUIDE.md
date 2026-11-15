# SWUSIP agent guide

Use the handset assigned to you. The institution authorises the phone separately
from configuring its office SIP extension. Enter device credentials only on the
phone; never share passwords in chat, customer notes or screenshots.

## Start a shift

1. Open SWUSIP. Check the assigned extension and SIP connection status on the
   dialler. “Not provisioned” requires administrator setup; “Not registered” does
   not mean the keypad is broken. Check the approved office network and ask the
   administrator if registration does not recover.
2. In Settings, tap **Unlock institutional workspace** and confirm your phone
   credential. Customer/admin access expires after five minutes. Calling remains
   independent of this customer-workspace timer.
3. Settings shows your signed institutional identity when unlocked. Local desk
   status is a temporary phone indicator; it does not sign you into a PBX queue,
   stop calls or inform other agents. Follow the office's PBX shift procedure.

## Dial and handle a call

Use Dialler, enter the approved extension or destination, and tap the green Call
button. Every numeric key, `*` and `#` is available. Backspace removes the final
character; Clear removes the number. A SIP address can also be typed in the number
field. An unconfigured extension produces an explanation instead of silent failure.

Incoming calls use Answer and End call/reject. During a connected call use the
available Mute, Speaker, Hold, Transfer, Keypad and End call controls. Do not use
synthetic directory fixture numbers as live destinations. Real PBX call controls
must be accepted in the institution's genuine SIP test before operational use.

## Find and help a customer

Open Customers after workspace unlock. Search by name, full/partial telephone,
account number or scheme. Use the scheme filter or Browse water schemes to narrow
results. Open a record to see registered numbers, associated accounts, schemes,
category, permitted notes and service location. Multiple matches are expected for
shared phones/accounts; verify which account the caller means.

For an authorised incoming call, SWUSIP looks up the actual SIP caller number
locally. The customer panel offers directory/profile access without ending the
call. A locked workspace, missing directory, hidden caller number or unmatched
number cannot establish customer identity. Do not treat caller ID alone as proof
that the caller is authorised to change an account.

Use **New call note** and **Save call note** for relevant interaction information.
Notes retain the assigned author and time. Never enter SIP passwords, device PINs,
payment credentials or unnecessary personal information. Normal agents cannot
import, export, undo batches, change provisioning or edit customer master details.

## Reliable incoming calls

The [four-column import guide](SWUSIP_IMPORT_GUIDE.md) explains telephone validation
and administrator review. Customer call-back requires an administrator-verified
office dialling profile; a clear message appears while no profile is enabled.
The existing manual dialler remains available. Multiple incoming matches offer
individual account/profile selection; confirm the account with the caller.

For agent-recorded outcomes and local pending follow-ups, see
[Customer workflows](SWUSIP_CUSTOMER_WORKFLOWS.md). These belong to this phone,
not a shared CRM queue or automatically detected SIP call results.

Allow SWUSIP microphone and notification access. In Settings, Android app and
battery settings opens the phone's app details; follow the administrator's approved
background/battery policy. Do not make SWUSIP the default phone or SMS app merely
to use SIP. Phone-vendor battery restrictions and actual office network coverage
must be tested with the app in the background and screen locked.

Call History shows recent calls for the selected extension. New call details can
show an assigned agent ID; older records say Not recorded. This identifies the
device assignment, not verified proof of which person answered.
