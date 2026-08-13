package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AttentionProjectorTest {
    private val projector = AttentionProjector()
    private val projectedAt = Instant.parse("2026-08-13T01:00:00Z")

    @Test
    fun `maps a blocked handoff to a high severity item`() {
        val decision = projector.project(event(), null, projectedAt) as ProjectionDecision.Applied

        assertThat(decision.item.reasonCode).isEqualTo(SourceEventType.HANDOFF_BLOCKED)
        assertThat(decision.item.severity).isEqualTo(Severity.HIGH)
        assertThat(decision.item.status).isEqualTo(AttentionStatus.ACTIVE)
        assertThat(decision.hasRevisionGap).isFalse()
    }

    @Test
    fun `ignores a stale revision and records a revision gap`() {
        val first = projector.project(event(revision = 2), null, projectedAt) as ProjectionDecision.Applied
        val stale = projector.project(event(revision = 1), first.item, projectedAt)

        assertThat(first.hasRevisionGap).isTrue()
        assertThat(stale).isEqualTo(ProjectionDecision.Stale)
    }

    private fun event(revision: Long = 1) = SourceEvent(
        eventId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        eventType = SourceEventType.HANDOFF_BLOCKED,
        eventVersion = 1,
        workspaceId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
        seasonId = UUID.fromString("30000000-0000-0000-0000-000000000001"),
        sourceReference = "handoff:42",
        aggregateRevision = revision,
        occurredAt = Instant.parse("2026-08-12T12:00:00Z"),
        state = SourceEventState.ACTIVE,
    )
}
