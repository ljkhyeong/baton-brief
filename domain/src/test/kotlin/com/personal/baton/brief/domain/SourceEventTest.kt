package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class SourceEventTest {
    @Test
    fun `양수가 아닌 이벤트 버전은 거부한다`() {
        assertThatIllegalArgumentException().isThrownBy {
            event(eventVersion = 0)
        }
    }

    @Test
    fun `양수가 아닌 집계 리비전은 거부한다`() {
        listOf(0L, -1L).forEach { aggregateRevision ->
            assertThatIllegalArgumentException().isThrownBy {
                event(aggregateRevision = aggregateRevision)
            }
        }
    }

    private fun event(
        eventVersion: Int = 1,
        aggregateRevision: Long = 1,
    ) = SourceEvent(
        eventId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        eventType = SourceEventType.HANDOFF_BLOCKED,
        eventVersion = eventVersion,
        workspaceId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
        seasonId = UUID.fromString("30000000-0000-0000-0000-000000000001"),
        sourceReference = "handoff:42",
        aggregateRevision = aggregateRevision,
        occurredAt = Instant.parse("2026-08-12T12:00:00Z"),
        state = SourceEventState.ACTIVE,
    )
}
