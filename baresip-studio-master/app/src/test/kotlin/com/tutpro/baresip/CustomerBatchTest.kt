package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CustomerBatchTest {
    private fun csv(count: Int) = CustomerImport.headers.joinToString(",") + "\n" +
        (1..count).joinToString("\n") { "Synthetic $it,+12025550123,${it.toString().padStart(8,'0')},SYN-01,Synthetic Scheme,Domestic," }
    @Test fun tenThousandCsvRecordsPreviewAndRetainTextAccounts() {
        val parsed = CustomerImport.parse(csv(10000).byteInputStream(),false)
        assertEquals(10000,parsed.size)
        assertTrue(parsed.all { it.error == null })
        assertEquals("00000001",parsed.first().values[2])
        val preview = CustomerImportPlanner.preview(emptyList(),parsed)
        assertTrue(preview.all { it.action == "New customer and account" && it.error == null })
        assertTrue(CustomerImportPlanner.preview(emptyList(),parsed + parsed.first()).last().action == "Skip duplicate")
    }
    @Test(expected = IllegalArgumentException::class) fun csvRejectsTenThousandAndOne() {
        CustomerImport.parse(csv(10001).byteInputStream(),false)
    }
    @Test fun tenThousandExcelRecordsRetainLeadingZeroes() {
        val all = listOf(CustomerImport.headers) + CustomerImport.parse(csv(10000).byteInputStream(),false).map { it.values }
        val xml = "<worksheet><sheetData>" + all.mapIndexed { r, values ->
            "<row r=\"${r+1}\">" + values.mapIndexed { c,v -> "<c r=\"${'A'+c}${r+1}\" t=\"inlineStr\"><is><t>$v</t></is></c>" }.joinToString("") + "</row>"
        }.joinToString("") + "</sheetData></worksheet>"
        val bytes = ByteArrayOutputStream().apply { ZipOutputStream(this).use {
            it.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); it.write(xml.toByteArray()); it.closeEntry()
        } }.toByteArray()
        val parsed = CustomerImport.parseWithParser(bytes.inputStream(),true) { KXmlParser() }
        assertEquals(10000,parsed.size); assertEquals("00010000",parsed.last().values[2]); assertTrue(parsed.all { it.error == null })
    }
    @Test fun malformedProgrammaticRowsAreRejectedInPreview() {
        assertNotNull(CustomerImportPlanner.preview(emptyList(),listOf(CustomerImportRow(2,listOf("invalid")))).single().error)
    }
    @Test fun sharedAccountOwnerConflictsMatchTheCommitPolicyRegardlessOfRowOrder() {
        val current = listOf(
            CustomerRecord("one","Synthetic One","+12025550101","0001","SYN-01","Synthetic Scheme","Domestic"),
            CustomerRecord("two","Synthetic Two","+12025550102","0001","SYN-01","Synthetic Scheme","Domestic"))
        val row = CustomerImportRow(2,listOf("Synthetic Two","+12025550103","0001","SYN-01","Synthetic Scheme","Domestic",""))
        listOf(current,current.reversed()).forEach {
            assertNotNull(CustomerImportPlanner.preview(it,listOf(row)).single().error)
        }
    }
}
