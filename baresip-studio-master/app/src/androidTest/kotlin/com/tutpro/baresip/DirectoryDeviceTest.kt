package com.tutpro.baresip

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DirectoryDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun spreadsheet(accountCell: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val header = CustomerImport.headers.mapIndexed { i,v -> "<c r=\"${'A'+i}1\" t=\"inlineStr\"><is><t>$v</t></is></c>" }.joinToString("")
            val values = listOf("Synthetic Customer","0772123456","000123","SYN-01","Synthetic Scheme","Domestic","")
            val cells = values.mapIndexed { i,v -> if(i == 2) accountCell else "<c r=\"${'A'+i}2\" t=\"inlineStr\"><is><t>$v</t></is></c>" }.joinToString("")
            zip.write(("<worksheet><sheetData><row r=\"1\">$header</row><row r=\"2\">$cells</row></sheetData></worksheet>").toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }
    @Test fun excelTextAccountsKeepLeadingZeroes() {
        val rows = CustomerImport.parse(spreadsheet("<c r=\"C2\" t=\"inlineStr\"><is><t>000123</t></is></c>").inputStream(),true)
        assertEquals("000123",rows.single().values[2]); assertNull(rows.single().error)
    }
    @Test fun excelNumericAccountsAreRejectedRatherThanInventingLostZeroes() {
        val rows = CustomerImport.parse(spreadsheet("<c r=\"C2\"><v>123</v></c>").inputStream(),true)
        assertTrue(rows.single().error!!.contains("Text"))
    }
    @Test fun excelFormulaAccountsAreRejected() {
        val rows = CustomerImport.parse(spreadsheet("<c r=\"C2\"><f>1+1</f><v>2</v></c>").inputStream(),true)
        assertTrue(rows.single().error!!.contains("Formula"))
    }
    @Test fun suppliedExcelTemplateHasCorrectColumnsAndNoCustomers() {
        assertTrue(CustomerImport.parse(context.assets.open("swusip-customers.xlsx"),true).isEmpty())
    }
    @Test fun forgedPhoneAuthorisationCannotGrantAdministrator() {
        val before = OfflineAuthorisation.role(context)
        try { OfflineAuthorisation.install(context,"{\"payload\":\"Zm9yZ2Vk\",\"signature\":\"Zm9yZ2Vk\"}"); fail("Forged grant accepted") }
        catch (_: Exception) { }
        assertEquals(before,OfflineAuthorisation.role(context))
    }
    @Test fun freshDatabaseReopensAndUnknownMigrationPreservesRecords() {
        val name = "swusip-synthetic-schema-test.db"
        context.deleteDatabase(name)
        try {
            CustomerDirectory(context,name).use { directory ->
                val db = directory.writableDatabase
                assertEquals(1,db.version)
                // A schema marker is deliberately not a customer or encryption test.
                db.insertOrThrow("customer",null,ContentValues().apply { put("id","synthetic-schema-marker"); put("payload","schema-marker") })
                try { directory.onUpgrade(db,1,2); fail("Unknown migration accepted") } catch (_: IllegalStateException) { }
            }
            CustomerDirectory(context,name).use { directory ->
                directory.readableDatabase.rawQuery("SELECT COUNT(*) FROM customer",null).use { c -> c.moveToFirst(); assertEquals(1,c.getInt(0)) }
                try {
                    directory.writableDatabase.execSQL("INSERT INTO customer_phone VALUES('missing-customer','missing-phone')")
                    fail("Foreign key violation accepted")
                } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            }
        } finally { context.deleteDatabase(name) }
    }
}
