package com.personal.baton.brief.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class WeeklyWindow(
    val weekStart: LocalDate,
    val zoneId: ZoneId,
    val start: Instant,
    val end: Instant,
) {
    companion object {
        fun startingOn(
            weekStart: LocalDate,
            zoneId: ZoneId,
        ): WeeklyWindow {
            require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "weekStart는 월요일이어야 합니다" }
            return WeeklyWindow(
                weekStart = weekStart,
                zoneId = zoneId,
                start = weekStart.atStartOfDay(zoneId).toInstant(),
                end = weekStart.plusWeeks(1).atStartOfDay(zoneId).toInstant(),
            )
        }
    }
}

data class BriefEditionItem(
    val sourceReference: String,
    val reasonCode: SourceEventType,
    val severity: Severity,
    val status: SourceEventState,
    val observedAt: Instant,
    val ruleVersion: Int,
    val aggregateRevision: Long?,
    val revisionGap: Boolean?,
)

data class BriefEdition(
    val editionId: UUID,
    val workspaceId: UUID,
    val seasonId: UUID,
    val generation: Long,
    val window: WeeklyWindow,
    val ruleVersion: Int,
    val sourceCursor: Long,
    val generatedAt: Instant,
    val items: List<BriefEditionItem>,
)
