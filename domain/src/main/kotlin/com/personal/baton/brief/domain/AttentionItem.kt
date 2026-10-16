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
    /** 공백 없이 이어진 현재 활성 구간의 첫 원본 시각. `RESOLVED`이면 `null`이다. */
    val activeSince: Instant?,
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

object AttentionProjector {
    fun project(
        event: SourceEvent,
        current: AttentionItem?,
    ): ProjectionDecision {
        if (current != null && event.aggregateRevision <= current.lastRevision) {
            return ProjectionDecision.Stale
        }

        val hasGap = event.aggregateRevision > (current?.lastRevision ?: 0) + 1
        val severity = when (checkNotNull(event.sourceSeverity) { "지원 버전 이벤트에는 sourceSeverity가 필요합니다" }) {
            SourceEventSeverity.CRITICAL -> Severity.HIGH
            SourceEventSeverity.WARNING -> Severity.MEDIUM
        }
        // 공백 사이에 해소가 있었을 수 있으므로 이어진 활성 갱신만 기존 시작 시각을 유지한다.
        val activeSince = when {
            event.state == SourceEventState.RESOLVED -> null
            current != null && current.status == SourceEventState.ACTIVE && !hasGap -> current.activeSince
            else -> event.occurredAt
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
                activeSince = activeSince,
                ruleVersion = RULE_VERSION,
                lastRevision = event.aggregateRevision,
                revisionGap = current?.revisionGap == true || hasGap,
            ),
            hasRevisionGap = hasGap,
        )
    }

    const val RULE_VERSION = 1
}
