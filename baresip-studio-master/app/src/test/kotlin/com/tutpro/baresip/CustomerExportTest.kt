package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test

class CustomerExportTest {
    private val password = "Synthetic export passphrase".toCharArray()
    private val record = CustomerRecord("synthetic-id","Synthetic Customer","+256772123456","0000123","SYN-01","Synthetic Scheme","Domestic")
    @Test fun encryptedExportRoundTripsTextIdentifiers() {
        val csv = CustomerExport.spreadsheet(listOf(record))
        val encrypted = CustomerExport.seal(csv,password)
        assertFalse(String(encrypted,Charsets.ISO_8859_1).contains("Synthetic Customer"))
        val restored = CustomerImport.parse(CustomerExport.open(encrypted,password).byteInputStream(),false)
        assertEquals("0000123",restored.single().values[2])
    }
    @Test fun wrongPasswordAndTamperingFailAuthentication() {
        val encrypted = CustomerExport.seal(CustomerExport.spreadsheet(listOf(record)),password)
        try { CustomerExport.open(encrypted,"Wrong synthetic passphrase".toCharArray()); fail("Wrong password accepted") } catch (_: javax.crypto.AEADBadTagException) { }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        try { CustomerExport.open(encrypted,password); fail("Tampering accepted") } catch (_: javax.crypto.AEADBadTagException) { }
    }
    @Test(expected = IllegalArgumentException::class) fun shortPassphrasesAreRejected() {
        CustomerExport.seal("synthetic","short".toCharArray())
    }
}
