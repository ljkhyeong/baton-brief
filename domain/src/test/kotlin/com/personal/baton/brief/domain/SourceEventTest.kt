package com.personal.baton.brief.domain

import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class SourceEventTest {
    @Test
    fun `지원 버전 미만과 심각도 없는 지원 버전 이벤트는 거부한다`() {
        listOf(0 to SourceEventSeverity.CRITICAL, 1 to SourceEventSeverity.CRITICAL, 2 to null)
            .forEach { (eventVersion, sourceSeverity) ->
                assertThatIllegalArgumentException().isThrownBy {
                    event(eventVersion = eventVersion, sourceSeverity = sourceSeverity)
                }
            }
    }

    @Test
    fun `이후 버전은 심각도 없이도 미지원 이벤트로 받는다`() {
        assertThat(event(eventVersion = 3, sourceSeverity = null).isSupported).isFalse()
        assertThat(event().isSupported).isTrue()
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
        eventVersion: Int = 2,
        sourceSeverity: SourceEventSeverity? = SourceEventSeverity.CRITICAL,
        aggregateRevision: Long = 1,
    ) = SourceEvent(
        eventId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        eventType = SourceEventType.ROLE_UNASSIGNED,
        eventVersion = eventVersion,
        workspaceId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
        seasonId = UUID.fromString("30000000-0000-0000-0000-000000000001"),
        sourceReference = "role:42",
        aggregateRevision = aggregateRevision,
        occurredAt = Instant.parse("2026-08-12T12:00:00Z"),
        state = SourceEventState.ACTIVE,
        sourceSeverity = sourceSeverity,
    )
}
