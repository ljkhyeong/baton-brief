package com.personal.baton.brief.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WeeklyWindowTest {
    @Test
    fun `DST 전환에서도 지역 주간 경계를 사용한다`() {
        val window = WeeklyWindow.startingOn(
            LocalDate.parse("2026-03-23"),
            ZoneId.of("Europe/Berlin"),
        )

        assertThat(window.start).isEqualTo(Instant.parse("2026-03-22T23:00:00Z"))
        assertThat(window.end).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"))
    }

    @Test
    fun `DST 시간이 겹쳐도 지역 주간 경계를 사용한다`() {
        val window = WeeklyWindow.startingOn(
            LocalDate.parse("2026-10-19"),
            ZoneId.of("Europe/Berlin"),
        )

        assertThat(window.start).isEqualTo(Instant.parse("2026-10-18T22:00:00Z"))
        assertThat(window.end).isEqualTo(Instant.parse("2026-10-25T23:00:00Z"))
    }
}
