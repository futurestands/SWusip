package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test

class CallerIdentityTest {
    private val first = CustomerRecord("synthetic-1","Synthetic One","+256772123456","0001","SYN-01","Synthetic Scheme One","Domestic")
    @Test fun oneMatchContainsTheCorrectCustomerAccountAndScheme() {
        val identity = CustomerMatching.identify(listOf(first),"sip:0772123456@pbx.invalid") as CallerIdentity.Single
        assertEquals(first,identity.record)
    }
    @Test fun sharedNumberNeverBecomesAnArbitrarilySelectedAccount() {
        val second = first.copy(account="0002",schemeCode="SYN-02")
        val other = first.copy(id="synthetic-2",name="Synthetic Two",account="0003")
        listOf(listOf(first,second,other),listOf(other,second,first)).forEach { records ->
            val identity = CustomerMatching.identify(records,"256772123456") as CallerIdentity.Multiple
            assertEquals(setOf("0001","0002","0003"),identity.matches.map { it.account }.toSet())
        }
    }
    @Test fun unmatchedAndInvalidNumbersRemainUnknown() {
        listOf("anonymous","101","+12025550123").forEach {
            assertEquals(CallerIdentity.Unknown,CustomerMatching.identify(listOf(first),it))
        }
    }
    @Test fun partialUgandaAndFormattedNumbersAreSearchable() {
        listOf("0772","0772 123","+256 772","256772").forEach {
            assertEquals(first,CustomerMatching.search(listOf(first),it).single())
        }
    }
    @Test fun duplicateAssociationsDoNotCreateFalseMultipleMatches() {
        assertTrue(CustomerMatching.identify(listOf(first,first),"0772123456") is CallerIdentity.Single)
    }
}
