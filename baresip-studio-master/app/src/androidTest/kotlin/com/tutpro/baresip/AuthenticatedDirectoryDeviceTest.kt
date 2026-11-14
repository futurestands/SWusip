package com.tutpro.baresip

import android.content.Intent
import android.app.Activity
import android.view.WindowManager
import android.view.MotionEvent
import android.view.InputDevice
import android.graphics.Rect
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.After
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.runner.RunWith
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Opt-in: owner confirms Android credentials through the real SWUSIP UI.
 * No test grant, reflection, session injection or test-only authorisation exists. */
@RunWith(AndroidJUnit4::class)
class AuthenticatedDirectoryDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var awakeActivity: Activity? = null
    private var previousAutomationFlags: Int? = null
    @After fun restoreDisplayFlag() {
        previousAutomationFlags?.let { flags ->
            val info = instrumentation.uiAutomation.serviceInfo
            info.flags = flags; instrumentation.uiAutomation.serviceInfo = info
        }
        awakeActivity?.let { activity -> instrumentation.runOnMainSync {
            if(!activity.isDestroyed) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } }
    }
    private fun findLabel(label: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.packageName?.toString() == context.packageName && node.isVisibleToUser &&
                (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
            for(i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        return find(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun awaitLabel(label: String): AccessibilityNodeInfo? {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while(findLabel(label) == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        return findLabel(label)
    }
    private fun clickIfVisible(label: String): Boolean {
        var target = awaitLabel(label) ?: return false
        while (!target.isClickable) target = target.parent ?: return false
        val clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        SystemClock.sleep(400)
        return clicked
    }
    private fun selectSyntheticDocument(fileName: String) {
        require(fileName in setOf("swusip-auth-preview.csv","swusip-auth-preview.xlsx"))
        fun node(label: String): AccessibilityNodeInfo? {
            fun walk(item: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                item ?: return null
                if(item.packageName?.toString() in setOf("com.google.android.documentsui","com.android.documentsui","com.google.android.go.documentsui") &&
                    item.isVisibleToUser && (item.text?.toString() == label || item.contentDescription?.toString() == label)) {
                    if(label == fileName) return item
                    var target: AccessibilityNodeInfo = item
                    while(!target.isClickable) target = target.parent ?: break
                    if(target.isClickable) return item
                }
                for(i in 0 until item.childCount) walk(item.getChild(i))?.let { return it }
                return null
            }
            return walk(instrumentation.uiAutomation.rootInActiveWindow)
        }
        fun waitFor(label: String): AccessibilityNodeInfo? {
            val deadline = SystemClock.elapsedRealtime() + 5000
            while(node(label) == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            return node(label)
        }
        fun tap(item: AccessibilityNodeInfo) {
            var target = item
            while(!target.isClickable) target = target.parent ?: break
            if(target.isClickable) assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            else {
                // Android Go's file rows handle touch without exposing ACTION_CLICK.
                // Restrict fallback to the exact synthetic file in the system picker.
                check(item.text?.toString() == fileName && item.isVisibleToUser && item.isEnabled)
                check(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() in
                    setOf("com.google.android.documentsui","com.android.documentsui","com.google.android.go.documentsui"))
                val bounds = Rect(); item.getBoundsInScreen(bounds); check(!bounds.isEmpty)
                val down = SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)) }
                    finally { event.recycle() }
                }
            }
            SystemClock.sleep(400)
        }
        if(waitFor(fileName) == null) {
            tap(checkNotNull(waitFor("Show roots")) { "Open Downloads in Android document picker" })
            tap(checkNotNull(waitFor("Downloads")))
        }
        tap(checkNotNull(waitFor(fileName)) { "Synthetic fixture must be visible in Downloads" })
    }
    private fun rows(vararg values: String) = CustomerImport.parse(
        (CustomerImport.headers.joinToString(",") + "\n" + values.joinToString("\n")).byteInputStream(),false)

    /** Populate a copy of the actual supplied XLSX template; all cells remain text. */
    private fun excelRows(): List<CustomerImportRow> {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { output ->
            ZipInputStream(context.assets.open("swusip-customers.xlsx")).use { input ->
                while(true) {
                    val entry = input.nextEntry ?: break
                    var contents = input.readBytes()
                    if(entry.name == "xl/worksheets/sheet1.xml") {
                        val values = listOf("Synthetic Excel","0772123456","0000456","SYN-X","Synthetic Excel Scheme","Domestic","Synthetic spreadsheet note")
                        val cells = values.mapIndexed { i,v -> "<c r=\"${'A'+i}2\" t=\"inlineStr\"><is><t>$v</t></is></c>" }.joinToString("")
                        val text = contents.toString(Charsets.UTF_8).replace("xmlns:x=","xmlns=")
                            .replace("<x:","<").replace("</x:","</")
                        val secondRow = Regex("<row\\b[^>]*\\br=\"2\"[^>]*>.*?</row>",RegexOption.DOT_MATCHES_ALL)
                        check(secondRow.containsMatchIn(text))
                        contents = text.replace(secondRow,"<row r=\"2\">$cells</row>").toByteArray()
                    }
                    output.putNextEntry(ZipEntry(entry.name)); output.write(contents); output.closeEntry()
                }
            }
        }
        return CustomerImport.parse(bytes.toByteArray().inputStream(),true)
    }

    private fun authenticateAdministrator() {
        assumeTrue("Opt-in requires the phone owner", InstrumentationRegistry.getArguments().getString("swusipAuthenticated") == "true")
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(1000)
        clickIfVisible("Back to call centre")
        clickIfVisible("Customers")
        if(!InstitutionAccess.administrator(context)) {
            clickIfVisible("Unlock institutional workspace")
            instrumentation.sendStatus(0,Bundle().apply { putString("stream","\nOWNER ACTION: confirm Unlock SWUSIP on the handset. Waiting up to 90 seconds.\n") })
        }
        val deadline = SystemClock.elapsedRealtime() + 90_000
        while (!InstitutionAccess.administrator(context) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(500)
        val authenticated = InstitutionAccess.administrator(context)
        if (!authenticated) instrumentation.sendStatus(0,Bundle().apply {
            putString("stream","\nBLOCKED/SKIPPED: no owner-confirmed administrator workspace. No protected workflow ran.\n")
        })
        assumeTrue("BLOCKED: owner did not authenticate an authorised administrator workspace",authenticated)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
            checkNotNull(activity) { "Keep SWUSIP foreground for protected tests" }
            if(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                awakeActivity = activity
            }
        }
        instrumentation.sendStatus(0,Bundle().apply { putString("stream","\nCONFIRMED: owner-authenticated administrator session; starting isolated synthetic workflows.\n") })
    }

    @Test fun authenticatedCustomerAndSchemeNavigation() {
        authenticateAdministrator()
        assertNotNull("Customer search must be accessible",awaitLabel("Name, telephone, account or scheme"))
        assertTrue(clickIfVisible("Browse water schemes"))
        assertNotNull("Scheme search must be accessible",awaitLabel("Search scheme name or code"))
        assertTrue(clickIfVisible("Back to customers"))
        assertNotNull(awaitLabel("Filter by scheme code or name"))
        assertTrue(clickIfVisible("Manage directory"))
        assertNotNull(awaitLabel("Import CSV / XLSX"))
        assertNotNull(awaitLabel("Save Excel template"))
        assertTrue(clickIfVisible("Close directory management"))
        assertTrue(clickIfVisible("Back to call centre"))
    }

    @Test fun syntheticDocumentPickerPreviews() {
        authenticateAdministrator()
        val info = instrumentation.uiAutomation.serviceInfo
        previousAutomationFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        instrumentation.uiAutomation.serviceInfo = info
        assertTrue(clickIfVisible("Manage directory"))
        for(file in listOf("swusip-auth-preview.csv","swusip-auth-preview.xlsx")) {
            assertTrue(clickIfVisible("Import CSV / XLSX"))
            selectSyntheticDocument(file)
            assertNotNull("The synthetic spreadsheet must reach preview",awaitLabel("Review 3 rows before import"))
            // Never confirm an import into the existing production directory.
            assertTrue(clickIfVisible("Cancel"))
        }
        assertTrue(clickIfVisible("Close directory management"))
        assertTrue(clickIfVisible("Back to call centre"))
    }

    @Test fun realAuthorisationEncryptedImportSearchNotesAndUndo() {
        authenticateAdministrator()
        val name = "swusip-synthetic-auth-${UUID.randomUUID()}.db"
        try {
            CustomerDirectory(context,name).use { directory ->
                val initial = rows(
                    "Synthetic One,+12025550101,0001,SYN-01,Synthetic One Scheme,Domestic,",
                    "Synthetic One,+12025550102,0002,SYN-02,Synthetic Two Scheme,Domestic,",
                    "Synthetic Two,+12025550101,0003,SYN-02,Synthetic Two Scheme,Commercial,")
                assertEquals(3,directory.preview(initial).count { it.error == null })
                assertEquals(3,directory.import(initial).accepted)
                assertEquals(3,directory.import(initial).skipped)
                directory.undoLatestImport() // Duplicate-only batch leaves earlier records intact.
                val before = directory.records()
                assertEquals(2,directory.lookup("+12025550101").size)
                assertEquals(2,CustomerMatching.search(before,"Synthetic One").size)
                assertEquals(2,CustomerMatching.search(before,"+1 (202) 555-0101").size)
                assertEquals(1,CustomerMatching.search(before,"0001").size)
                assertEquals(2,CustomerMatching.search(before,"","SYN-02").size)
                assertEquals(2,CustomerMatching.search(before,"Synthetic Two Scheme").size)
                assertTrue(CustomerMatching.identify(before,"+12025550101") is CallerIdentity.Multiple)
                val person = before.single { it.account == "0001" }
                directory.associateAccount(person.id,"0002","SYN-02")
                assertEquals(2,directory.records().filter { it.id == person.id }.map { it.account }.distinct().size)
                assertEquals(1,directory.import(rows("Synthetic One,+12025550103,0001,SYN-01,Synthetic One Scheme,Domestic,")).updated)
                assertFalse("A phone must not leak to another associated account",directory.lookup("+12025550103").any { it.account == "0002" })
                directory.undoLatestImport()
                assertTrue(directory.lookup("+12025550103").isEmpty())
                directory.addCallNote(person.id,"Synthetic interaction note only")
                assertTrue(directory.callNotes(person.id).single().contains("Synthetic interaction note only"))
                directory.saveInteraction(person.id,InteractionDraft("Synthetic callback outcome",InteractionOutcome.CALLBACK_REQUIRED,true))
                val interaction = directory.interactions(person.id).first { it.followUp == FollowUpState.PENDING }
                assertEquals(1,directory.pendingFollowUps()[person.id])
                directory.completeFollowUp(person.id,interaction.id)
                val completed = directory.interactions(person.id).first { it.id == interaction.id }
                assertEquals(FollowUpState.COMPLETE,completed.followUp)
                assertEquals(interaction.author,completed.author)
                assertEquals(interaction.createdAt,completed.createdAt)
                assertTrue(completed.completedBy.isNotBlank())
                assertFalse(directory.pendingFollowUps().containsKey(person.id))
                try { directory.undoLatestImport(); fail("Undo discarded later association or note") }
                catch (_: Exception) { }
                assertTrue(directory.records().isNotEmpty())
                directory.updateCustomer(person.id,"Synthetic Edited","Synthetic Location")
                assertTrue(directory.records().filter { it.id == person.id }.all { it.name == "Synthetic Edited" && it.location == "Synthetic Location" })
                val excel = excelRows()
                assertEquals("0000456",excel.single().values[2])
                assertNull(directory.preview(excel).single().error)
                assertEquals(1,directory.import(excel).accepted)
                val identified = CustomerMatching.identify(directory.records(),"sip:256772123456@pbx.invalid") as CallerIdentity.Single
                assertEquals("Synthetic Excel",identified.record.name)
                assertEquals("0000456",identified.record.account)
                assertEquals("Synthetic Excel Scheme",identified.record.schemeName)
                directory.undoLatestImport()
                assertTrue(directory.lookup("0772123456").isEmpty())
                directory.readableDatabase.rawQuery("SELECT payload FROM customer",null).use { cursor ->
                    while(cursor.moveToNext()) assertFalse(cursor.getString(0).contains("Synthetic"))
                }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun tenThousandEncryptedRecordsAndUndo() {
        assumeTrue("Large encrypted batch is an explicit performance test",
            InstrumentationRegistry.getArguments().getString("swusipLargeBatch") == "true")
        authenticateAdministrator()
        val large = "swusip-synthetic-large-${UUID.randomUUID()}.db"
        instrumentation.sendStatus(0,Bundle().apply { putString("stream","\nStarting isolated 10,000-record encrypted performance test.\n") })
        try {
            CustomerDirectory(context,large).use { directory ->
                val data = rows(*(1..10000).map { "Synthetic Batch $it,+12025550123,${it.toString().padStart(8,'0')},SYN-B,Synthetic Batch Scheme,Domestic," }.toTypedArray())
                val started = SystemClock.elapsedRealtime()
                assertEquals(10000,directory.import(data).accepted)
                instrumentation.sendStatus(0,Bundle().apply { putString("stream","\nEncrypted 10,000-record import committed in ${SystemClock.elapsedRealtime()-started} ms; reading directory.\n") })
                val loaded = directory.records()
                assertEquals(10000,loaded.size)
                assertEquals("00010000",CustomerMatching.search(loaded,"00010000").single().account)
                instrumentation.sendStatus(0,Bundle().apply { putString("stream","\n10,000-record search passed; reversing the synthetic batch.\n") })
                directory.undoLatestImport()
                assertTrue(directory.records().isEmpty())
            }
        } finally { context.deleteDatabase(large) }
    }
}
