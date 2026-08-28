package com.personal.baton.brief.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WeeklyWindowTest {
    @Test
    fun `uses local week boundaries across daylight saving transition`() {
        val window = WeeklyWindow.startingOn(
            LocalDate.parse("2026-03-23"),
            ZoneId.of("Europe/Berlin"),
        )

        assertThat(window.start).isEqualTo(Instant.parse("2026-03-22T23:00:00Z"))
        assertThat(window.end).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"))
    }

    @Test
    fun `uses local week boundaries across daylight saving overlap`() {
        val window = WeeklyWindow.startingOn(
            LocalDate.parse("2026-10-19"),
            ZoneId.of("Europe/Berlin"),
        )

        assertThat(window.start).isEqualTo(Instant.parse("2026-10-18T22:00:00Z"))
        assertThat(window.end).isEqualTo(Instant.parse("2026-10-25T23:00:00Z"))
    }
}
