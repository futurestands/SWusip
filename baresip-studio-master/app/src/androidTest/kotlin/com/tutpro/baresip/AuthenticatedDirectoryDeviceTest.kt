package com.tutpro.baresip

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Opt-in: owner confirms Android credentials through the real SWUSIP UI.
 * No test grant, reflection, session injection or test-only authorisation exists. */
@RunWith(AndroidJUnit4::class)
class AuthenticatedDirectoryDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun clickIfVisible(label: String): Boolean {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
            for(i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        var target = find(instrumentation.uiAutomation.rootInActiveWindow) ?: return false
        while (!target.isClickable) target = target.parent ?: return false
        val clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        SystemClock.sleep(400)
        return clicked
    }
    private fun rows(vararg values: String) = CustomerImport.parse(
        (CustomerImport.headers.joinToString(",") + "\n" + values.joinToString("\n")).byteInputStream(),false)

    @Test fun realAuthorisationEncryptedImportSearchNotesAndUndo() {
        assumeTrue("Opt-in requires the phone owner", InstrumentationRegistry.getArguments().getString("swusipAuthenticated") == "true")
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(1000)
        clickIfVisible("Back to call centre")
        clickIfVisible("Customers")
        clickIfVisible("Unlock institutional workspace")
        instrumentation.sendStatus(0,Bundle().apply { putString("stream","\nOWNER ACTION: confirm Unlock SWUSIP on the handset. Waiting up to 90 seconds.\n") })
        val deadline = SystemClock.elapsedRealtime() + 90_000
        while (!InstitutionAccess.administrator(context) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(500)
        val authenticated = InstitutionAccess.administrator(context)
        if (!authenticated) instrumentation.sendStatus(0,Bundle().apply {
            putString("stream","\nBLOCKED/SKIPPED: no owner-confirmed administrator workspace. No protected workflow ran.\n")
        })
        assumeTrue("BLOCKED: owner did not authenticate an authorised administrator workspace",authenticated)
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
                assertEquals(1,CustomerMatching.search(before,"0001").size)
                assertEquals(2,CustomerMatching.search(before,"","SYN-02").size)
                val person = before.single { it.account == "0001" }
                directory.associateAccount(person.id,"0002","SYN-02")
                assertEquals(2,directory.records().filter { it.id == person.id }.map { it.account }.distinct().size)
                assertEquals(1,directory.import(rows("Synthetic One,+12025550103,0001,SYN-01,Synthetic One Scheme,Domestic,")).updated)
                assertFalse("A phone must not leak to another associated account",directory.lookup("+12025550103").any { it.account == "0002" })
                directory.undoLatestImport()
                assertTrue(directory.lookup("+12025550103").isEmpty())
                directory.addCallNote(person.id,"Synthetic interaction note only")
                assertTrue(directory.callNotes(person.id).single().contains("Synthetic interaction note only"))
                try { directory.undoLatestImport(); fail("Undo discarded later association or note") }
                catch (_: Exception) { }
                assertTrue(directory.records().isNotEmpty())
                directory.readableDatabase.rawQuery("SELECT payload FROM customer",null).use { cursor ->
                    while(cursor.moveToNext()) assertFalse(cursor.getString(0).contains("Synthetic"))
                }
            }
        } finally { context.deleteDatabase(name) }
        val large = "swusip-synthetic-large-${UUID.randomUUID()}.db"
        try {
            CustomerDirectory(context,large).use { directory ->
                val data = rows(*(1..10000).map { "Synthetic Batch $it,+12025550123,${it.toString().padStart(8,'0')},SYN-B,Synthetic Batch Scheme,Domestic," }.toTypedArray())
                assertEquals(10000,directory.import(data).accepted)
                assertEquals(10000,directory.records().size)
                assertEquals("00010000",CustomerMatching.search(directory.records(),"00010000").single().account)
                directory.undoLatestImport()
                assertTrue(directory.records().isEmpty())
            }
        } finally { context.deleteDatabase(large) }
    }
}
