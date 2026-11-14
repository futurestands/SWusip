package com.tutpro.baresip

enum class InteractionOutcome(val label: String) {
    UNSPECIFIED("Not classified"), RESOLVED("Resolved"), INFORMATION_GIVEN("Information given"),
    REFERRED("Referred"), CALLBACK_REQUIRED("Callback required");
    companion object { fun stored(value: String) = entries.find { it.name == value } ?: UNSPECIFIED }
}

enum class FollowUpState(val label: String) {
    NOT_REQUIRED("No follow-up"), PENDING("Follow-up pending"), COMPLETE("Follow-up complete"), UNKNOWN("Follow-up status unavailable");
    companion object {
        fun stored(value: String) = if(value.isEmpty()) NOT_REQUIRED else entries.find { it.name == value } ?: UNKNOWN
    }
}

data class InteractionDraft(val text: String, val outcome: InteractionOutcome = InteractionOutcome.UNSPECIFIED,
    val followUpRequired: Boolean = false) {
    init {
        require(text.isNotBlank() && text.length <= 2000) { "Enter a note of 1–2,000 characters" }
        require(outcome != InteractionOutcome.CALLBACK_REQUIRED || followUpRequired) { "A callback requires follow-up tracking" }
    }
}

data class CustomerInteraction(val id: String, val customerId: String, val text: String, val author: String,
    val createdAt: Long, val outcome: InteractionOutcome = InteractionOutcome.UNSPECIFIED,
    val followUp: FollowUpState = FollowUpState.NOT_REQUIRED, val completedBy: String = "", val completedAt: Long = 0)
