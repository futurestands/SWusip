package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test

class CustomerImportTest {
    private val header = CustomerImport.headers.joinToString(",") + "\r\n"
    private fun parse(row: String) = CustomerImport.parse((header + row).byteInputStream(), false)

    @Test fun ugandaVariantsMatchTheSameCaller() {
        listOf("0772123456", "+256772123456", "256772123456", "sip:+256772123456@pbx.example;user=phone")
            .forEach { assertEquals("+256772123456",CustomerNumbers.normalise(it)) }
    }
    @Test fun preserveInternationalNumbers() {
        assertEquals("+442079460123", CustomerNumbers.normalise("0044 20 7946 0123"))
        assertEquals("+12025550123", CustomerNumbers.normalise("+1 (202) 555-0123"))
    }
    @Test fun rejectAmbiguousOrInvalidNumbers() {
        listOf("", "1001", "+256+772123456", "0772abc123456", "07721234567890").forEach {
            assertNull(CustomerNumbers.normalise(it))
        }
    }
    @Test fun preserveLeadingZeroAccountAndQuotedNotes() {
        val rows = parse("Synthetic Customer,0772123456,0000123,SYN-01,Synthetic Scheme,Domestic,\"Line one, with comma\nLine two \"\"quoted\"\"\"\r\n")
        assertNull(rows.single().error)
        assertEquals("0000123", rows.single().values[2])
        assertEquals("Line one, with comma\nLine two \"quoted\"", rows.single().values[6])
    }
    @Test fun mandatoryFieldsProduceRowErrors() {
        val rows = parse(",0772123456,0000123,SYN-01,Synthetic Scheme,Domestic,\nSynthetic Customer,bad,0000124,SYN-01,Synthetic Scheme,Domestic,")
        assertEquals(listOf(2,3),rows.map { it.row })
        assertTrue(rows.all { it.error != null })
    }
    @Test fun blankRowsDoNotShiftValidationRowNumbers() {
        val rows = parse("\n\nSynthetic Customer,bad,000123,SYN-01,Synthetic Scheme,Domestic,")
        assertEquals(4,rows.single().row)
        assertNotNull(rows.single().error)
    }
    @Test fun sharedPhoneAcrossAccountsIsNotDiscardedByParser() {
        val rows = parse("Synthetic One,0772123456,0001,SYN-01,Synthetic Scheme,Domestic,\nSynthetic Two,0772123456,0002,SYN-02,Another Synthetic Scheme,Domestic,")
        assertEquals(2,rows.size)
        assertTrue(rows.all { it.error == null })
        assertNotEquals(rows[0].values[2],rows[1].values[2])
    }
    @Test(expected = IllegalArgumentException::class) fun wrongHeaderIsRejected() {
        CustomerImport.parse("name,number\nSynthetic,0772123456".byteInputStream(),false)
    }
    @Test(expected = IllegalArgumentException::class) fun unclosedQuoteIsRejected() {
        parse("\"Synthetic Customer,0772123456,0001,SYN-01,Synthetic Scheme,Domestic,")
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedDocumentIsRejected() {
        CustomerImport.parse(ByteArray(CustomerImport.MAX_BYTES+1).inputStream(),false)
    }
}
