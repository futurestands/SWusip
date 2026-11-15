package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FourColumnImportTest {
    private fun csv(body:String,policy:PhoneNormalisationPolicy=PhoneNormalisationPolicy()) = CustomerImport.parse(
        (CustomerImport.requiredHeaders.joinToString(",")+"\n"+body).byteInputStream(),false,policy)
    @Test fun fourCompulsoryFieldsKeepTextAccountsAndPhoneProposals() {
        val row=csv("Synthetic One,700123456,0000123,Synthetic Scheme").single()
        assertNull(row.error); assertEquals("0000123",row.values[2]); assertEquals("",row.values[3])
        assertEquals("",row.values[5]); assertEquals("+256700123456",row.phone?.canonical)
        assertEquals("700123456",row.phone?.original)
        val preview=CustomerImportPlanner.preview(emptyList(),listOf(row)).single()
        assertNull(preview.error); assertTrue(preview.scheme!!.generated)
    }
    @Test fun originalTelephoneWhitespaceIsNotOverwritten() {
        val row=csv("Synthetic One, 0700 123456 ,0001,Synthetic Scheme").single()
        assertEquals(" 0700 123456 ",row.phone!!.original)
        assertEquals("+256700123456",row.phone.canonical)
    }
    @Test fun existingSevenColumnFieldsRemainAvailable() {
        val row=CustomerImport.parse((CustomerImport.headers.joinToString(",")+"\nSynthetic One,0700123456,0001,SYN,Synthetic Scheme,Domestic,Original note").byteInputStream(),false).single()
        assertNull(row.error); assertEquals("Domestic",row.values[5]); assertEquals("Original note",row.values[6])
    }
    @Test fun referenceStyleAliasesAreMappedWithoutRequiringSchemeCodes() {
        val row=CustomerImport.parse("NAME,CONTACT,ACCOUNT NO,SCHEME\nSynthetic One,0700123456,0001,Synthetic Scheme".byteInputStream(),false).single()
        assertNull(row.error); assertEquals("0001",row.values[2])
    }
    @Test fun numericExcelAccountIsFlaggedEvenWhenTheColumnsAreReordered() {
        fun cell(address:String,value:String,type:String="inlineStr") = if(type=="n") "<c r=\"$address\"><v>$value</v></c>" else "<c r=\"$address\" t=\"inlineStr\"><is><t>$value</t></is></c>"
        val xml="<worksheet><sheetData><row r=\"1\">"+cell("A1","account_number")+cell("B1","scheme_name")+cell("C1","customer_name")+cell("D1","phone_number")+
            "</row><row r=\"2\">"+cell("A2","123","n")+cell("B2","Synthetic Scheme")+cell("C2","Synthetic One")+cell("D2","700123456","n")+"</row></sheetData></worksheet>"
        val bytes=ByteArrayOutputStream().apply { ZipOutputStream(this).use { it.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"));it.write(xml.toByteArray());it.closeEntry() } }.toByteArray()
        val row=CustomerImport.parseWithParser(bytes.inputStream(),true) { KXmlParser() }.single()
        assertTrue(row.error!!.contains("Text")); assertEquals("700123456",row.values[1])
    }
    @Test fun aSchemeNameCollisionNeverSelectsTheFirstExistingScheme() {
        val row=csv("Synthetic One,0700123456,0001, SAME  Scheme ").single()
        val catalog=listOf(SchemeEntry("A","Same Scheme"),SchemeEntry("B","same scheme"))
        assertTrue(CustomerImportPlanner.preview(emptyList(),listOf(row),catalog).single().error!!.contains("Ambiguous scheme"))
    }
    @Test fun stableGeneratedIdentifiersAndExistingNamesResolveExactly() {
        assertEquals(SchemeNames.generatedCode("Synthetic Scheme"),SchemeNames.generatedCode(" synthetic  SCHEME "))
        assertNotEquals(SchemeNames.generatedCode("Synthetic-Scheme"),SchemeNames.generatedCode("Synthetic Scheme"))
        val row=csv("Synthetic One,0700123456,0001, synthetic scheme ").single()
        val preview=CustomerImportPlanner.preview(emptyList(),listOf(row),listOf(SchemeEntry("SYN","Synthetic Scheme"))).single()
        assertEquals("SYN",preview.scheme?.code); assertEquals("Synthetic Scheme",preview.scheme?.name)
    }
    @Test fun allExplicitSchemesAreConsideredBeforeResolvingAnImplicitName() {
        val parsed=CustomerImport.parse((CustomerImport.headers.joinToString(",")+"\nSynthetic One,0700123456,0001,,Shared,,\nSynthetic Two,0700123457,0002,A,Shared,,\nSynthetic Three,0700123458,0003,B,Shared,,").byteInputStream(),false)
        assertTrue(CustomerImportPlanner.preview(emptyList(),parsed).first().error!!.contains("Ambiguous"))
    }
    @Test fun configuredCorrectionIsShownAndDuplicateAccountsUseCanonicalNumbers() {
        val policy=PhoneNormalisationPolicy(setOf("9"))
        val rows=csv("Synthetic One,0700123456,0001,Synthetic Scheme\nSynthetic One,90700123456,0001,Synthetic Scheme",policy)
        val preview=CustomerImportPlanner.preview(emptyList(),rows,policy=policy)
        assertEquals("Skip duplicate",preview.last().action)
        assertTrue(preview.last().phone!!.correctionReason.contains("prefix 9"))
    }
}
