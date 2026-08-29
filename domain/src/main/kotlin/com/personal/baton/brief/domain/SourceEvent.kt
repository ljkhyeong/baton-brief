package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID

enum class SourceEventType(val contractVersion: Int) {
    HANDOFF_BLOCKED(1),
    ROUTINE_MISSED(1),
    DECISION_FOLLOW_UP_OVERDUE(1),
    ROLE_UNASSIGNED(2),
    ROLE_SUCCESSOR_MISSING(2),
    ROLE_PREPARATION_INCOMPLETE(2),
    ROUTINE_REPEATEDLY_OVERDUE(2),
    HANDOFF_INCOMPLETE(2),
}

enum class SourceEventSeverity {
    CRITICAL,
    WARNING,
}

enum class SourceEventState {
    ACTIVE,
    RESOLVED,
}

data class SourceEvent(
    val eventId: UUID,
    val eventType: SourceEventType,
    val eventVersion: Int,
    val workspaceId: UUID,
    val seasonId: UUID,
    val sourceReference: String,
    val aggregateRevision: Long,
    val occurredAt: Instant,
    val state: SourceEventState,
    val sourceSeverity: SourceEventSeverity? = null,
) {
    init {
        require(isReceivable(eventVersion, eventType, sourceSeverity)) {
            "eventVersion, eventType and sourceSeverity must match"
        }
    }

    val isSupported: Boolean
        get() = isSupportedContract(eventVersion, eventType, sourceSeverity)

    companion object {
        fun isReceivable(
            eventVersion: Int,
            eventType: SourceEventType,
            sourceSeverity: SourceEventSeverity?,
        ): Boolean = isSupportedContract(eventVersion, eventType, sourceSeverity) ||
            eventVersion > 2 ||
            (eventVersion == 2 && eventType.contractVersion == 1 && sourceSeverity == null)

        private fun isSupportedContract(
            eventVersion: Int,
            eventType: SourceEventType,
            sourceSeverity: SourceEventSeverity?,
        ): Boolean = eventType.contractVersion == eventVersion &&
            if (eventVersion == 1) sourceSeverity == null else sourceSeverity != null
    }
}
