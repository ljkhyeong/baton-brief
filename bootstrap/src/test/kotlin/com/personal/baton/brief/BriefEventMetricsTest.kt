package com.personal.baton.brief

import com.personal.baton.brief.application.BriefUseCases
import com.personal.baton.brief.application.IngestResult
import com.personal.baton.brief.application.IngestStatus
import com.personal.baton.brief.domain.SourceEventState
import com.personal.baton.brief.domain.SourceEventType
import com.personal.baton.brief.web.BriefController
import com.personal.baton.brief.web.SourceEventRequest
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class BriefEventMetricsTest {
    @Test
    fun `요청 전의 영점에서 첫 충돌과 미지원 결과의 증가량을 확인할 수 있다`() {
        val registry = SimpleMeterRegistry()
        try {
            val brief = mock(BriefUseCases::class.java)
            val controller = BriefController(brief, registry)
            IngestStatus.entries.forEach { outcome ->
                assertThat(registry.get("brief.events.received").tag("outcome", outcome.name).counter().count())
                    .isZero()
            }

            val request = SourceEventRequest(
                eventId = UUID.randomUUID().toString(),
                eventType = SourceEventType.HANDOFF_BLOCKED,
                eventVersion = 1,
                workspaceId = UUID.randomUUID().toString(),
                seasonId = UUID.randomUUID().toString(),
                sourceReference = "handoff-metrics",
                aggregateRevision = 1,
                occurredAt = "2026-09-08T00:00:00Z",
                state = SourceEventState.ACTIVE,
            )
            listOf(IngestStatus.CONFLICT, IngestStatus.UNSUPPORTED).forEach { outcome ->
                val input = if (outcome == IngestStatus.UNSUPPORTED) request.copy(eventVersion = 99) else request
                given(brief.ingest(input.toDomain())).willReturn(IngestResult(UUID.fromString(input.eventId), outcome))

                controller.ingest(input)

                assertThat(registry.get("brief.events.received").tag("outcome", outcome.name).counter().count())
                    .isEqualTo(1.0)
            }
        } finally {
            registry.close()
        }
    }
}
