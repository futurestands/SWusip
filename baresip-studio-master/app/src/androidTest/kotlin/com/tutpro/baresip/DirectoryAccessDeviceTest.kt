package com.tutpro.baresip

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectoryAccessDeviceTest {
    @Test fun lockedWorkspaceDeniesRepositoryOperations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse("Instrumentation starts without a confirmed workspace session", InstitutionAccess.agent(context))
        CustomerDirectory(context,"swusip-synthetic-denied-test.db").use { directory ->
            val actions = listOf<() -> Unit>(
                { directory.records() }, { directory.preview(emptyList()) }, { directory.import(emptyList()) },
                { directory.undoLatestImport() }, { directory.updateCustomer("missing","Synthetic","") },
                { directory.associateAccount("missing","0001","SYN-01") },
                { directory.addCallNote("missing","Synthetic note") }, { directory.callNotes("missing") })
            actions.forEach { action ->
                try { action(); fail("Locked workspace permitted protected operation") }
                catch (_: IllegalStateException) { }
            }
        }
        assertFalse("Denied operations must not create a database",context.getDatabasePath("swusip-synthetic-denied-test.db").exists())
    }
    @Test fun tenThousandCsvRecordsParseOnAndroid() {
        val csv = CustomerImport.headers.joinToString(",") + "\n" + (1..10000).joinToString("\n") {
            "Synthetic $it,+12025550123,${it.toString().padStart(8,'0')},SYN-01,Synthetic Scheme,Domestic,"
        }
        val parsed = CustomerImport.parse(csv.byteInputStream(),false)
        assertEquals(10000,parsed.size); assertTrue(parsed.all { it.error == null })
        assertEquals("00000001",parsed.first().values[2])
    }
}
