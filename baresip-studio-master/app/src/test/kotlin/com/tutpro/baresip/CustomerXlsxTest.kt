package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Runs the production XLSX import logic with an actual JVM XML pull parser.
 * The Android platform parser has separate compiled device tests. */
class CustomerXlsxTest {
    private fun parse(bytes: ByteArray) = CustomerImport.parseWithParser(bytes.inputStream(),true) { KXmlParser() }
    private fun book(account: String, number: Int = 2, headerPrefix: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        val header = CustomerImport.headers.mapIndexed { i,v -> "<c r=\"${'A'+i}1\" t=\"inlineStr\"><is><t>$v</t></is></c>" }.joinToString("")
        val fields = listOf("Synthetic Customer","0772123456","000123","SYN-01","Synthetic Scheme","Domestic","")
        val row = fields.mapIndexed { i,v -> if(i == 2) account else "<c r=\"${'A'+i}$number\" t=\"inlineStr\"><is><t>$v</t></is></c>" }.joinToString("")
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write((headerPrefix + "<worksheet><sheetData><row r=\"1\">$header</row><row r=\"$number\">$row</row></sheetData></worksheet>").toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }
    @Test fun generatedTemplateIgnoresAllStyledEmptyCells() {
        val template = listOf(File("src/main/assets/swusip-customers.xlsx"),File("app/src/main/assets/swusip-customers.xlsx")).first { it.isFile }
        assertTrue(parse(template.readBytes()).isEmpty())
    }
    @Test fun textAccountsRetainLeadingZeroes() {
        val row = parse(book("<c r=\"C2\" t=\"inlineStr\"><is><t>000123</t></is></c>")).single()
        assertNull(row.error); assertEquals("000123",row.values[2])
    }
    @Test fun numericAccountsAreRejected() {
        assertTrue(parse(book("<c r=\"C2\"><v>123</v></c>")).single().error!!.contains("Text"))
    }
    @Test fun formulaAccountsAreRejected() {
        assertTrue(parse(book("<c r=\"C2\"><f>1+1</f><v>2</v></c>")).single().error!!.contains("Formula"))
    }
    @Test fun missingWorksheetRowsDoNotShiftErrorLocations() {
        val row = parse(book("<c r=\"C8\"><v>123</v></c>",8)).single()
        assertEquals(8,row.row); assertNotNull(row.error)
    }
    @Test fun documentEntitiesCannotSupplyCustomerData() {
        try {
            parse(book("<c r=\"C2\" t=\"inlineStr\"><is><t>&injected;</t></is></c>",
                headerPrefix = "<!DOCTYPE worksheet [<!ENTITY injected 'Synthetic injected value'>]>"))
            fail("Document entity processing was enabled")
        } catch (error: org.xmlpull.v1.XmlPullParserException) { assertTrue(error.message!!.contains("DTD")) }
    }
}
