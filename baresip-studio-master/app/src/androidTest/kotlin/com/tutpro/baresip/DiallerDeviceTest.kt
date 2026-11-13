package com.tutpro.baresip

import android.app.KeyguardManager
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real activity and accessibility actions. Never provisions or places a SIP call. */
@RunWith(AndroidJUnit4::class)
class DiallerDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun nodes(): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            found.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it) }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let { walk(it) }
        return found
    }
    private fun locate(label: String): AccessibilityNodeInfo? = nodes().firstOrNull {
        it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
    }
    private fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(200) }
    private fun click(label: String) {
        var node = locate(label)
        if (node == null) {
            repeat(3) {
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                settle()
            }
            repeat(5) {
                if (node == null) {
                    node = locate(label)
                    if (node == null) {
                        nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        settle()
                    }
                }
            }
        }
        var target = node ?: error("Missing labelled control: $label")
        while (!target.isClickable) target = target.parent ?: error("Control is not clickable: $label")
        assertTrue("Click $label", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        settle()
    }
    private fun assertNumber(value: String) {
        assertTrue("Number field must contain the exact entered sequence", nodes().any {
            it.isEditable && it.text?.toString() == value
        })
    }
    @Test fun keypadEditingAndUnprovisionedCallFeedback() {
        val context = instrumentation.targetContext
        assumeTrue("Owner must unlock handset", !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(1500)
        // Safety: do not press Call on an account that could reach the PBX.
        assumeTrue("This non-calling test requires an unprovisioned handset", BaresipService.uas.value.isEmpty())
        click("Dialler")
        click("Dialler")
        click("Clear number")
        val digits = "123456789*0#"
        digits.forEachIndexed { index, digit ->
            click(digit.toString())
            assertNumber(digits.take(index + 1))
        }
        click("Backspace")
        assertNumber(digits.dropLast(1))
        click("Clear number")
        assertTrue(nodes().any { it.isEditable && it.text.isNullOrEmpty() })
        click("1"); click("0"); click("1")
        assertNumber("101")
        click("Call")
        assertNotNull(locate("Not connected to the office telephone system. Contact your administrator to configure your extension."))
        assertTrue("No synthetic call may be allocated", Call.calls().isEmpty())
        click(context.getString(R.string.ok))
        click("Clear number")
    }
}
