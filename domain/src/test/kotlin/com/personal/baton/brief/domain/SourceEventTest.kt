package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class SourceEventTest {
    @Test
    fun `양수가 아닌 이벤트 버전은 거부한다`() {
        assertThatIllegalArgumentException().isThrownBy {
            SourceEvent(
                eventId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
                eventType = SourceEventType.HANDOFF_BLOCKED,
                eventVersion = 0,
                workspaceId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                seasonId = UUID.fromString("30000000-0000-0000-0000-000000000001"),
                sourceReference = "handoff:42",
                aggregateRevision = 1,
                occurredAt = Instant.parse("2026-08-12T12:00:00Z"),
                state = SourceEventState.ACTIVE,
            )
        }
    }
}
