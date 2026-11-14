package com.tutpro.baresip

import android.app.KeyguardManager
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AboutLegalDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            result.add(node)
            for(i in 0 until node.childCount) node.getChild(i)?.let { walk(it) }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let { walk(it) }
        return result
    }
    private fun find(label: String) = nodes().firstOrNull {
        it.packageName?.toString() == instrumentation.targetContext.packageName && it.isVisibleToUser &&
            (it.text?.toString() == label || it.contentDescription?.toString() == label)
    }
    private fun awaitLabel(label: String): AccessibilityNodeInfo? {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while(find(label) == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        return find(label)
    }
    private fun assertForeground(wait: Boolean = true) {
        val packageName = instrumentation.targetContext.packageName
        val deadline = SystemClock.elapsedRealtime() + if(wait) 5000 else 0
        while(wait && instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != packageName &&
            SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertEquals("Keep SWUSIP open and the handset idle during public-screen tests",
            packageName,instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
    }
    private fun scroll(forward: Boolean) {
        assertForeground()
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val start = metrics.heightPixels * if(forward) 0.78f else 0.36f
        val end = metrics.heightPixels * if(forward) 0.36f else 0.78f
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float) {
            val motion = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
            motion.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion,true)) }
            finally { motion.recycle() }
        }
        event(MotionEvent.ACTION_DOWN,start)
        for(i in 1..10) { SystemClock.sleep(16); event(MotionEvent.ACTION_MOVE,start+(end-start)*i/10f) }
        event(MotionEvent.ACTION_UP,end)
        settle()
    }
    private fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(350) }
    private fun click(label: String) {
        assertForeground()
        var node = find(label)
        if(node == null) {
            repeat(4) { scroll(false) }
            repeat(8) {
                if(node == null) {
                    node = find(label)
                    if(node == null) scroll(true)
                }
            }
        }
        var target = node ?: error("Missing public control: $label")
        while(!target.isClickable) target = target.parent ?: error("Unusable public control: $label")
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)); settle()
    }
    private fun screenshot(name: String) {
        settle()
        assertForeground()
        val expected = when {
            name.startsWith("about-") -> "About SWUSIP"
            name.startsWith("legal-information-") -> "Software information"
            else -> "Find in licences"
        }
        assertNotNull("Capture only the expected public information page",find(expected))
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Normal public-screen capture must be available; never bypass secure windows",bitmap)
        try {
            // A user may switch apps while the system captures a frame. Discard it
            // unless the same public SWUSIP page is still foreground afterwards.
            assertForeground(wait = false)
            assertNotNull("Discard frames when the public page changed",find(expected))
            val folder = File(instrumentation.targetContext.getExternalFilesDir(null),"legal-review").apply { mkdirs() }
            val file = File(folder,name)
            file.outputStream().use { assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it)) }
            assertTrue("Screenshot file must be saved",file.length() > 0)
            instrumentation.sendStatus(0,Bundle().apply { putString("stream","\nPublic screenshot saved: ${file.absolutePath}\n") })
        } finally { bitmap!!.recycle() }
    }
    @Test fun institutionalAboutPublicLegalNavigationSearchAndShortViewport() {
        val context = instrumentation.targetContext
        assumeTrue("Owner must unlock the handset",!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        assumeTrue("Do not interrupt any call for screen review",!Call.inCall())
        assertFalse("Public legal access must not require a workspace session",InstitutionAccess.agent(context))
        val automation = instrumentation.uiAutomation
        try {
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SystemClock.sleep(1500)
            click("Settings"); click("About SWUSIP")
            val ready = SystemClock.elapsedRealtime() + 5000
            while(find("SWUSIP") == null && SystemClock.elapsedRealtime() < ready) SystemClock.sleep(100)
            assertNotNull(find("SWUSIP"))
            assertNotNull(find("South Western Umbrella of Water and Sanitation"))
            assertNotNull(find("Ministry of Water and Environment"))
            val ordinary = nodes().filter { it.packageName?.toString() == context.packageName }.joinToString { it.text?.toString().orEmpty() }
            assertFalse(Regex("Baresip|TutPro|GitHub|github\\.com",RegexOption.IGNORE_CASE).containsMatchIn(ordinary))
            screenshot("about-portrait.png")
            click("Legal information")
            assertNotNull("Legal information must expose licences",awaitLabel("Open-source licences"))
            screenshot("legal-information-portrait.png")
            click("Open-source licences")
            repeat(10) { if(nodes().none { it.text?.contains("BSD 3-Clause License") == true }) SystemClock.sleep(300) }
            assertTrue(nodes().any { it.text?.contains("BSD 3-Clause License") == true })
            screenshot("open-source-licences-portrait.png")
            val field = nodes().first { it.isEditable }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"TutPro")
            }))
            settle()
            val searched = SystemClock.elapsedRealtime() + 5000
            while(nodes().none { it.text?.toString()?.matches(Regex("[1-9][0-9]* matches")) == true } &&
                SystemClock.elapsedRealtime() < searched) SystemClock.sleep(100)
            assertTrue(nodes().any { it.text?.toString()?.matches(Regex("[1-9][0-9]* matches")) == true })
            click("Back"); click("Back")
            automation.setRotation(UiAutomation.ROTATION_FREEZE_90); settle()
            repeat(5) { scroll(false) }
            assertNotNull(find("SWUSIP"))
            screenshot("about-landscape.png")
            click("Legal information")
            assertNotNull("Legal information must expose licences in landscape",awaitLabel("Open-source licences"))
            screenshot("legal-information-landscape.png")
        } finally { automation.setRotation(UiAutomation.ROTATION_UNFREEZE) }
    }
}
