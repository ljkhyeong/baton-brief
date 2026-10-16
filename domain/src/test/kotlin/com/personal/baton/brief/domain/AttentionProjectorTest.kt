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

    @Test
    fun `활성 시작 시각은 공백 없이 이어진 활성 갱신에서만 유지한다`() {
        val items = listOf(
            event(revision = 1, occurredAt = "2026-08-10T00:00:00Z"),
            event(revision = 2, occurredAt = "2026-08-11T00:00:00Z", sourceSeverity = SourceEventSeverity.WARNING),
            event(revision = 3, occurredAt = "2026-08-12T00:00:00Z", state = SourceEventState.RESOLVED),
            event(revision = 4, occurredAt = "2026-08-13T00:00:00Z"),
            // 5가 해소였을 수 있으므로 공백 뒤 활성은 새 시작으로 본다.
            event(revision = 6, occurredAt = "2026-08-15T00:00:00Z"),
        ).scan<SourceEvent, AttentionItem?>(null) { current, event ->
            (AttentionProjector.project(event, current) as ProjectionDecision.Applied).item
        }.drop(1)

        assertThat(items.map { it?.activeSince }).containsExactly(
            Instant.parse("2026-08-10T00:00:00Z"),
            Instant.parse("2026-08-10T00:00:00Z"),
            null,
            Instant.parse("2026-08-13T00:00:00Z"),
            Instant.parse("2026-08-15T00:00:00Z"),
        )
    }

    private fun event(
        revision: Long = 1,
        sourceSeverity: SourceEventSeverity = SourceEventSeverity.CRITICAL,
        occurredAt: String = "2026-08-12T12:00:00Z",
        state: SourceEventState = SourceEventState.ACTIVE,
    ) = SourceEvent(
        eventId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        eventType = SourceEventType.ROLE_UNASSIGNED,
        eventVersion = 2,
        sourceSeverity = sourceSeverity,
        workspaceId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
        seasonId = UUID.fromString("30000000-0000-0000-0000-000000000001"),
        sourceReference = "role:42",
        aggregateRevision = revision,
        occurredAt = Instant.parse(occurredAt),
        state = state,
    )
}
