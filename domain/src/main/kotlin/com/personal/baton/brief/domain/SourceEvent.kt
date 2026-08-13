package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID

enum class SourceEventType {
    HANDOFF_BLOCKED,
    ROUTINE_MISSED,
    DECISION_FOLLOW_UP_OVERDUE,
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
)
