package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID

enum class SourceEventType {
    ROLE_UNASSIGNED,
    ROLE_SUCCESSOR_MISSING,
    ROLE_PREPARATION_INCOMPLETE,
    ROUTINE_REPEATEDLY_OVERDUE,
    HANDOFF_INCOMPLETE,
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
        require(aggregateRevision > 0) { "aggregateRevision은 양수여야 합니다" }
        require(isReceivable(eventVersion, sourceSeverity)) { "eventVersion과 sourceSeverity 조합이 올바르지 않습니다" }
    }

    /** 지원 버전만 투영한다. 이후 버전은 미지원 수신 기록으로만 보존한다. */
    val isSupported: Boolean
        get() = eventVersion == SUPPORTED_VERSION

    companion object {
        const val SUPPORTED_VERSION = 2

        fun isReceivable(
            eventVersion: Int,
            sourceSeverity: SourceEventSeverity?,
        ): Boolean = eventVersion > SUPPORTED_VERSION || (eventVersion == SUPPORTED_VERSION && sourceSeverity != null)
    }
}
