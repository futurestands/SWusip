package com.tutpro.baresip

import org.junit.Assert.*
import org.junit.Test

class CustomerInteractionTest {
    @Test fun legacyNotesDoNotAcquireInventedOutcomesOrFollowUps() {
        assertEquals(InteractionOutcome.UNSPECIFIED,InteractionOutcome.stored(""))
        assertEquals(FollowUpState.NOT_REQUIRED,FollowUpState.stored(""))
    }
    @Test fun unknownFutureFollowUpStatesAreExplicit() {
        assertEquals(FollowUpState.UNKNOWN,FollowUpState.stored("future-state"))
    }
    @Test fun callbackDraftTracksFollowUp() {
        val draft = InteractionDraft("Synthetic callback",InteractionOutcome.CALLBACK_REQUIRED,true)
        assertTrue(draft.followUpRequired)
    }
    @Test(expected = IllegalArgumentException::class) fun callbackCannotSilentlyLoseItsFollowUp() {
        InteractionDraft("Synthetic callback",InteractionOutcome.CALLBACK_REQUIRED,false)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyNotesAreRejected() { InteractionDraft(" ") }
    @Test(expected = IllegalArgumentException::class) fun excessiveNotesAreRejected() { InteractionDraft("x".repeat(2001)) }
    @Test fun allRecordedOutcomesRoundTrip() {
        InteractionOutcome.entries.forEach { assertEquals(it,InteractionOutcome.stored(it.name)) }
    }
}
