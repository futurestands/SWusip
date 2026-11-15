package com.tutpro.baresip

import android.app.KeyguardManager
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only screen checks: never saves contacts/settings, provisions accounts or places calls. */
@RunWith(AndroidJUnit4::class)
class FeatureNavigationDeviceTest {
    private val session=AuthenticatedDirectoryDeviceTest()
    @After fun restore() {session.restoreDisplayFlag()}
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=i.targetContext
    private fun nodes():List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun walk(n:AccessibilityNodeInfo?) {n?:return;result.add(n);for(x in 0 until n.childCount) walk(n.getChild(x))}
        walk(i.uiAutomation.rootInActiveWindow);return result
    }
    private fun find(label:String)=nodes().firstOrNull {it.packageName?.toString()==context.packageName && it.isVisibleToUser && (it.text?.toString()==label || it.contentDescription?.toString()==label)}
    private fun await(label:String):AccessibilityNodeInfo? {
        val end=SystemClock.elapsedRealtime()+5000
        while(find(label)==null && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
        return find(label)
    }
    private fun click(label:String) {
        var n=await(label)
        repeat(6) {
            if(n==null) {
                scroll()
                SystemClock.sleep(250);n=find(label)
            }
        }
        var target=checkNotNull(n) {"Missing labelled control: $label"}
        while(!target.isClickable) target=checkNotNull(target.parent) {"Unusable labelled control: $label"}
        assertTrue("Control did not respond: $label",target.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(400)
    }
    private fun scroll() {
        check(i.uiAutomation.rootInActiveWindow?.packageName?.toString()==context.packageName) {"Scroll only SWUSIP"}
        val m=context.resources.displayMetrics;val x=m.widthPixels*.5f
        val start=m.heightPixels*.75f;val end=m.heightPixels*.35f;val down=SystemClock.uptimeMillis()
        fun event(action:Int,y:Float) {
            val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
            e.source=InputDevice.SOURCE_TOUCHSCREEN
            try {assertTrue(i.uiAutomation.injectInputEvent(e,true))} finally {e.recycle()}
        }
        event(MotionEvent.ACTION_DOWN,start)
        for(step in 1..10) {SystemClock.sleep(16);event(MotionEvent.ACTION_MOVE,start+(end-start)*step/10f)}
        event(MotionEvent.ACTION_UP,end)
    }
    private fun back() {i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400)}
    private fun home() {
        // Reset only the activity navigation stack, never app data or the SIP service.
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        SystemClock.sleep(1000)
        assertNotNull(await("Dialler"));assertNotNull(await("Customers"))
    }
    @Before fun safeStart() {
        assumeTrue("Owner must normally unlock phone",!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        assumeTrue("Do not interrupt an actual call",!Call.inCall())
        home()
    }
    @Test fun homeContactsEditorCancellationAndUnprovisionedHistory() {
        assertTrue(nodes().any {it.text?.toString()?.startsWith("SIP connection: ")==true})
        val before=BaresipService.baresipContacts.value.size
        click("Contacts")
        assertNotNull(await(context.getString(R.string.contacts)))
        click(context.getString(R.string.add))
        assertNotNull(await(context.getString(R.string.new_contact)))
        assertNotNull(await(context.getString(R.string.contact_name)))
        // No field is filled, save button pressed or existing contact selected.
        click("Back")
        assertNotNull(await(context.getString(R.string.contacts)))
        assertEquals("Opening/cancelling the editor must preserve contacts",before,BaresipService.baresipContacts.value.size)
        home()
        if(BaresipService.uas.value.isEmpty()) {
            click("Call History")
            assertNotNull(await("No call history is available until your extension is configured. Contact your administrator."))
            click(context.getString(R.string.ok))
        }
    }
    @Test fun agentSettingsAudioAndAndroidSettingsLinks() {
        assertFalse("Run this public navigation check before workspace authentication",InstitutionAccess.agent(context))
        click("Settings")
        assertNotNull(await("SWUSIP · Agent Settings"))
        assertNull(find("Administrator settings"));assertNull(find("Extension provisioning"))
        click("Microphone and audio")
        assertNotNull(await(context.getString(R.string.audio_settings)))
        assertNotNull(await(context.getString(R.string.ringtone)))
        // Back leaves all existing audio values unchanged; never press Check/Save.
        back();assertNotNull(await("SWUSIP · Agent Settings"))
        for(label in listOf("Notification preferences","Android app and battery settings")) {
            click(label)
            val end=SystemClock.elapsedRealtime()+5000
            while(i.uiAutomation.rootInActiveWindow?.packageName?.toString()==context.packageName && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
            assertTrue("Android settings link must open a system settings surface",i.uiAutomation.rootInActiveWindow?.packageName?.toString() in setOf("com.android.settings","com.miui.securitycenter","com.google.android.permissioncontroller","com.android.permissioncontroller"))
            back();assertNotNull(await("SWUSIP · Agent Settings"))
        }
        assertNotNull(await("About SWUSIP"))
        home()
    }
    @Test fun authenticatedLocalAvailabilityAndAdministratorScreensReadOnly() {
        assumeTrue("Normal owner authentication opt-in",InstrumentationRegistry.getArguments().getString("swusipAuthenticated")=="true")
        session.authenticateAdministrator(openDirectory=false)
        click("Available");assertNotNull(await("Local desk status: Available"))
        click("Busy");assertNotNull(await("Local desk status: Busy"))
        click("Off duty");assertNotNull(await("Local desk status: Off duty"))
        assertNotNull(await("Administrator settings"))
        click("Extension provisioning")
        assertNotNull(await(context.getString(R.string.accounts)))
        back();assertNotNull(await("SWUSIP · Agent Settings"))
        // Do not save provisioning or advanced settings; those require separate SIP regression.
    }
}
