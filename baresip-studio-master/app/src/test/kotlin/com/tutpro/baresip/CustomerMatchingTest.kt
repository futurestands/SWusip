package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test

class CustomerMatchingTest {
    private val person = CustomerRecord("synthetic-id-1","Synthetic Customer","+256772123456","000123","SYN-01","Synthetic Water Scheme","Domestic")
    private fun row(name: String = person.name, phone: String = "0772123456", account: String = person.account,
        scheme: String = person.schemeCode, schemeName: String = person.schemeName) = CustomerImportRow(2,
        listOf(name,phone,account,scheme,schemeName,"Domestic",""))

    @Test fun simulatedLookupReturnsNameAccountAndScheme() {
        val found = CustomerMatching.lookup(listOf(person),"sip:0772123456@synthetic-pbx.example")
        assertEquals("Synthetic Customer",found.single().name)
        assertEquals("000123",found.single().account)
        assertEquals("Synthetic Water Scheme",found.single().schemeName)
    }
    @Test fun sharedCallerNumberReturnsAllCustomersAndSchemes() {
        val other = person.copy(id="synthetic-id-2",name="Synthetic Second Customer",schemeCode="SYN-02",schemeName="Synthetic Other Scheme")
        val found = CustomerMatching.lookup(listOf(person,other),"256772123456")
        assertEquals(2,found.size); assertEquals(setOf("SYN-01","SYN-02"),found.map { it.schemeCode }.toSet())
    }
    @Test fun unknownCallerRemainsUnknown() { assertTrue(CustomerMatching.lookup(listOf(person),"+442079460123").isEmpty()) }
    @Test fun searchSupportsEveryRequiredFieldAndUgandaPhoneFormats() {
        listOf("synthetic customer","0772123456","256772123456","+256 772 123456","000123","SYN-01","Synthetic Water").forEach {
            assertEquals(person,CustomerMatching.search(listOf(person),it).single())
        }
        assertTrue(CustomerMatching.search(listOf(person),"Synthetic","SYN-02").isEmpty())
    }
    @Test fun duplicateImportIsExplicitlySkipped() {
        assertEquals("Skip duplicate",CustomerImportPlanner.preview(listOf(person),listOf(row())).single().action)
    }
    @Test fun conflictingOwnerIsRejected() {
        assertNotNull(CustomerImportPlanner.preview(listOf(person),listOf(row(name="Different Synthetic Person"))).single().error)
    }
    @Test fun sameAccountNumberInDifferentSchemeIsValid() {
        assertNull(CustomerImportPlanner.preview(listOf(person),listOf(row(scheme="SYN-02",schemeName="Synthetic Other Scheme"))).single().error)
    }
    @Test fun additionalTelephoneIsAnAuthorisedUpdateProposal() {
        assertEquals("Add telephone to existing account",CustomerImportPlanner.preview(listOf(person),listOf(row(phone="0772123457"))).single().action)
    }
    @Test fun conflictingSchemeNamesWithinOneFileAreRejected() {
        val preview = CustomerImportPlanner.preview(emptyList(),listOf(row(),row(account="000124",schemeName="Conflicting Synthetic Scheme")))
        assertNull(preview[0].error); assertNotNull(preview[1].error)
    }
}
