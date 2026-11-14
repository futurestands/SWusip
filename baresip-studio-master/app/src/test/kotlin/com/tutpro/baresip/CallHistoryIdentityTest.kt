package com.tutpro.baresip

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CallHistoryIdentityTest {
    @Test fun legacyHistoryDoesNotInventAnAgent() {
        // Use the production date serializer's actual representation.
        val current = Json.encodeToString(CallHistory("sip:101@pbx.invalid","sip:102@pbx.invalid","in"))
        assertNull(Json.decodeFromString<CallHistory>(current).agentId)
    }
    @Test fun newAssignedIdentitySurvivesHistoryRoundTrip() {
        val history = CallHistory("sip:101@pbx.invalid","sip:102@pbx.invalid","out","synthetic-agent-01")
        val restored = Json.decodeFromString<CallHistory>(Json.encodeToString(history))
        assertEquals("synthetic-agent-01",restored.agentId)
    }
}
