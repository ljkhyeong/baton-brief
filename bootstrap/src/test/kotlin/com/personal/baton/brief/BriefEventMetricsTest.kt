package com.personal.baton.brief

import com.personal.baton.brief.application.BriefUseCases
import com.personal.baton.brief.application.IngestStatus
import com.personal.baton.brief.web.BriefController
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/** 결과별 증가량은 `BriefMvpIntegrationTest`의 수신 시나리오가 실제 컨텍스트에서 확인한다. */
class BriefEventMetricsTest {
    @Test
    fun `수신 결과 카운터는 첫 요청 전부터 0으로 등록된다`() {
        val registry = SimpleMeterRegistry()
        BriefController(mock(BriefUseCases::class.java), registry)

        IngestStatus.entries.forEach { outcome ->
            assertThat(registry.get("brief.events.received").tag("outcome", outcome.name).counter().count())
                .isZero()
        }
    }
}
