package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test

class PhoneNormalisationTest {
    @Test fun recognisedUgandaMobileRepresentationsHaveOneCanonicalIdentity() {
        listOf("0700123456","700123456","256700123456","+256700123456").forEach {
            val result = PhoneNormaliser.normalise(it)
            assertTrue(result.reason,result.accepted); assertEquals(it,result.original)
            assertEquals("+256700123456",result.canonical); assertEquals(TelephoneKind.MOBILE,result.kind)
        }
    }
    @Test fun accessPrefixRequiresExplicitConfigurationAndIsRemovedOnlyOnce() {
        assertFalse(PhoneNormaliser.normalise("90700123456").accepted)
        val configured = PhoneNormalisationPolicy(setOf("9"))
        val result = PhoneNormaliser.normalise("90700123456",configured)
        assertEquals("+256700123456",result.canonical)
        assertTrue(result.correctionReason.contains("configured PBX access prefix 9"))
        listOf("990700123456","99123","9123456789").forEach { assertFalse(PhoneNormaliser.normalise(it,configured).accepted) }
    }
    @Test fun fixedLinesAreValidatedAsFixedRatherThanMobile() {
        listOf("0312123456","312123456","+256312123456","0414123456","0202401234").forEach {
            val result=PhoneNormaliser.normalise(it); assertTrue(it,result.accepted); assertEquals(TelephoneKind.FIXED,result.kind)
        }
    }
    @Test fun allocationBoundariesAreExplicitAndVersioned() {
        listOf("0721000000","0721099999","0728000000","0737199999","0795123456").forEach { assertTrue(it,PhoneNormaliser.normalise(it).accepted) }
        listOf("0721100000","0728100000","0737200000","0796123456","0720123456").forEach {
            assertEquals(it,PhoneValidationStatus.AMBIGUOUS,PhoneNormaliser.normalise(it).status)
        }
        assertEquals("UCC-2026-07-13|access=",PhoneNormalisationPolicy().version)
    }
    @Test fun malformedValuesNeverReceiveInventedDigits() {
        listOf("","+256+256700123456","+256256700123456","0700123","0772abc123456","１２３４５６７８９","+0123456789").forEach {
            val result=PhoneNormaliser.normalise(it); assertFalse(it,result.accepted); assertNull(result.canonical)
        }
    }
    @Test fun unknownServiceAndExtensionNumbersRequireReview() {
        listOf("101","112","999","0800123456","123456789").forEach {
            val result=PhoneNormaliser.normalise(it); assertFalse(it,result.accepted); assertNull(result.canonical)
        }
    }
    @Test fun sipAddressesUseTheSameExactService() {
        listOf("sip:0700123456@pbx.invalid","SIPS:+256700123456@pbx.invalid;user=phone","Caller <sip:700123456@pbx.invalid>","sip:%2B256700123456@pbx.invalid").forEach {
            assertEquals("+256700123456",PhoneNormaliser.normalise(it).canonical)
        }
        assertNull(PhoneNormaliser.normalise("anonymous <sip:anonymous@pbx.invalid>").canonical)
    }
    @Test fun internationalFormatAndOriginalTextArePreserved() {
        val raw=" 0044 20 7946 0123 "
        val result=PhoneNormaliser.normalise(raw)
        assertEquals(raw,result.original); assertEquals("+442079460123",result.canonical)
        assertEquals(PhoneValidationStatus.CORRECTED,result.status)
        assertEquals(PhoneValidationStatus.VALID,PhoneNormaliser.normalise("+12025550123").status)
    }
    @Test fun incomingLookupIsExactAndRetainsEveryAccount() {
        val first=CustomerRecord("one","Synthetic One","+256700123456","0001","SYN","Synthetic Scheme","")
        val second=first.copy(id="two",account="0002")
        val records=listOf(first,second)
        val matched=CustomerMatching.lookup(records,"sip:90700123456@pbx.invalid",PhoneNormalisationPolicy(setOf("9")))
        assertEquals(records,matched)
        assertTrue(CustomerMatching.lookup(records,"0700123457").isEmpty())
        assertTrue(CustomerMatching.lookup(records,"70012345").isEmpty())
        assertEquals("+256700123456",first.phone)
    }
}

class OutboundDiallingFormatterTest {
    private fun verified(style: OutboundNumberStyle,prefix:String="") = OutboundDiallingProfile(style,prefix,true,"Approved synthetic test reference")
    @Test fun profilesAreDisabledUntilExplicitlyVerified() {
        assertTrue(OutboundDiallingFormatter.format("+256700123456",OutboundDiallingProfile()) is OutboundDestination.Blocked)
        assertTrue(OutboundDiallingFormatter.format("+256700123456",OutboundDiallingProfile(verified=true)) is OutboundDestination.Blocked)
    }
    @Test fun verifiedProfilesProduceOnlyTheRequiredDestination() {
        mapOf(OutboundNumberStyle.UGANDA_NATIONAL to "0700123456",OutboundNumberStyle.UGANDA_COUNTRY_CODE to "256700123456",
            OutboundNumberStyle.E164_PLUS to "+256700123456",OutboundNumberStyle.PBX_ACCESS_NATIONAL to "90700123456").forEach { (style,value) ->
            assertEquals(OutboundDestination.Ready(value),OutboundDiallingFormatter.format("+256700123456",verified(style,"9")))
        }
    }
    @Test fun repeatedAndAlreadyFormattedPrefixesAreBlocked() {
        listOf("90700123456","990700123456","+256+256700123456","0700123456").forEach {
            assertTrue(OutboundDiallingFormatter.format(it,verified(OutboundNumberStyle.PBX_ACCESS_NATIONAL,"9")) is OutboundDestination.Blocked)
        }
        assertTrue(OutboundDiallingFormatter.format("+256700123456",verified(OutboundNumberStyle.PBX_ACCESS_NATIONAL,"99")) is OutboundDestination.Blocked)
    }
    @Test fun internationalNumbersCannotBeForcedIntoUgandaNationalFormat() {
        assertTrue(OutboundDiallingFormatter.format("+442079460123",verified(OutboundNumberStyle.UGANDA_NATIONAL)) is OutboundDestination.Blocked)
        assertEquals(OutboundDestination.Ready("+442079460123"),OutboundDiallingFormatter.format("+442079460123",verified(OutboundNumberStyle.E164_PLUS)))
    }
}
