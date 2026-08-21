package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID

enum class Severity {
    MEDIUM,
    HIGH,
}

data class AttentionItem(
    val workspaceId: UUID,
    val seasonId: UUID,
    val eventType: SourceEventType,
    val sourceReference: String,
    val severity: Severity,
    val status: SourceEventState,
    val observedAt: Instant,
    val ruleVersion: Int,
    val lastRevision: Long,
    val revisionGap: Boolean,
) {
    val reasonCode: SourceEventType
        get() = eventType
}

sealed interface ProjectionDecision {
    data class Applied(
        val item: AttentionItem,
        val hasRevisionGap: Boolean,
    ) : ProjectionDecision

    data object Stale : ProjectionDecision
}

object AttentionProjector {
    fun project(
        event: SourceEvent,
        current: AttentionItem?,
    ): ProjectionDecision {
        if (current != null && event.aggregateRevision <= current.lastRevision) {
            return ProjectionDecision.Stale
        }

        val hasGap = event.aggregateRevision > (current?.lastRevision ?: 0) + 1
        val severity = when (event.eventType) {
            SourceEventType.HANDOFF_BLOCKED -> Severity.HIGH
            SourceEventType.ROUTINE_MISSED,
            SourceEventType.DECISION_FOLLOW_UP_OVERDUE,
            -> Severity.MEDIUM
        }

        return ProjectionDecision.Applied(
            item = AttentionItem(
                workspaceId = event.workspaceId,
                seasonId = event.seasonId,
                eventType = event.eventType,
                sourceReference = event.sourceReference,
                severity = severity,
                status = event.state,
                observedAt = event.occurredAt,
                ruleVersion = RULE_VERSION,
                lastRevision = event.aggregateRevision,
                revisionGap = current?.revisionGap == true || hasGap,
            ),
            hasRevisionGap = hasGap,
        )
    }

    const val RULE_VERSION = 1
}
