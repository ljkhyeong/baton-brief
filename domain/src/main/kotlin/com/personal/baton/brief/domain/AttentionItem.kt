package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID

enum class Severity {
    MEDIUM,
    HIGH,
}

enum class AttentionStatus {
    ACTIVE,
    RESOLVED,
}

data class AttentionItem(
    val workspaceId: UUID,
    val seasonId: UUID,
    val eventType: SourceEventType,
    val sourceReference: String,
    val reasonCode: SourceEventType,
    val severity: Severity,
    val status: AttentionStatus,
    val observedAt: Instant,
    val projectedAt: Instant,
    val ruleVersion: Int,
    val lastRevision: Long,
    val revisionGap: Boolean,
)

sealed interface ProjectionDecision {
    data class Applied(
        val item: AttentionItem,
        val hasRevisionGap: Boolean,
    ) : ProjectionDecision

    data object Stale : ProjectionDecision
}

class AttentionProjector {
    fun project(
        event: SourceEvent,
        current: AttentionItem?,
        projectedAt: Instant,
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
                reasonCode = event.eventType,
                severity = severity,
                status = when (event.state) {
                    SourceEventState.ACTIVE -> AttentionStatus.ACTIVE
                    SourceEventState.RESOLVED -> AttentionStatus.RESOLVED
                },
                observedAt = event.occurredAt,
                projectedAt = projectedAt,
                ruleVersion = RULE_VERSION,
                lastRevision = event.aggregateRevision,
                revisionGap = current?.revisionGap == true || hasGap,
            ),
            hasRevisionGap = hasGap,
        )
    }

    companion object {
        const val RULE_VERSION = 1
    }
}
