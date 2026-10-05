package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AttentionProjectorTest {
    @Test
    fun `원본 심각도 CRITICAL은 HIGH, WARNING은 MEDIUM으로 투영한다`() {
        mapOf(SourceEventSeverity.CRITICAL to Severity.HIGH, SourceEventSeverity.WARNING to Severity.MEDIUM)
            .forEach { (sourceSeverity, severity) ->
                val decision = AttentionProjector.project(event(sourceSeverity = sourceSeverity), null)
                    as ProjectionDecision.Applied
                assertThat(decision.item.severity).isEqualTo(severity)
                assertThat(decision.hasRevisionGap).isFalse()
            }
    }

    @Test
    fun `오래된 리비전은 무시하고 리비전 공백은 기록한다`() {
        val first = AttentionProjector.project(event(revision = 2), null) as ProjectionDecision.Applied
        val stale = AttentionProjector.project(event(revision = 1), first.item)

        assertThat(first.hasRevisionGap).isTrue()
        assertThat(stale).isEqualTo(ProjectionDecision.Stale)
    }

    private fun event(
        revision: Long = 1,
        sourceSeverity: SourceEventSeverity = SourceEventSeverity.CRITICAL,
    ) = SourceEvent(
        eventId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        eventType = SourceEventType.ROLE_UNASSIGNED,
        eventVersion = 2,
        sourceSeverity = sourceSeverity,
        workspaceId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
        seasonId = UUID.fromString("30000000-0000-0000-0000-000000000001"),
        sourceReference = "role:42",
        aggregateRevision = revision,
        occurredAt = Instant.parse("2026-08-12T12:00:00Z"),
        state = SourceEventState.ACTIVE,
    )
}
