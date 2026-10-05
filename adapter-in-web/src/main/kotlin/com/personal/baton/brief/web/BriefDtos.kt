package com.personal.baton.brief.web

import com.personal.baton.brief.application.AttentionItemCursor
import com.personal.baton.brief.application.CurrentAttentionItemPage
import com.personal.baton.brief.application.GenerateEditionCommand
import com.personal.baton.brief.application.IngestResult
import com.personal.baton.brief.application.IngestStatus
import com.personal.baton.brief.domain.AttentionItem
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.BriefEditionItem
import com.personal.baton.brief.domain.Severity
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.SourceEventState
import com.personal.baton.brief.domain.SourceEventSeverity
import com.personal.baton.brief.domain.SourceEventType
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.hibernate.validator.constraints.CodePointLength

internal const val SOURCE_REFERENCE_PATTERN =
    "(?s)(?=.*\\P{javaWhitespace})[^\\u0000\\uD800-\\uDFFF]*"

/** PostgreSQL 시각 범위 안에서 계약이 쓰는 네 자리 연도 */
private val SUPPORTED_YEARS = 0..9999

data class SourceEventRequest(
    val eventId: UUID,
    val eventType: SourceEventType,
    @field:Min(SourceEvent.SUPPORTED_VERSION.toLong())
    val eventVersion: Int,
    val sourceSeverity: SourceEventSeverity? = null,
    val workspaceId: UUID,
    val seasonId: UUID,
    @field:CodePointLength(max = 128)
    @field:Pattern(regexp = SOURCE_REFERENCE_PATTERN)
    val sourceReference: String,
    @field:Positive
    val aggregateRevision: Long,
    val occurredAt: Instant,
    val state: SourceEventState,
) {
    @get:AssertTrue(message = "occurredAt은 0000~9999년이어야 합니다")
    val supportedOccurredAt: Boolean
        get() = occurredAt.atOffset(ZoneOffset.UTC).year in SUPPORTED_YEARS

    @get:AssertTrue(message = "eventVersion 2에는 sourceSeverity가 필요합니다")
    val validVersionContract: Boolean
        get() = eventVersion < SourceEvent.SUPPORTED_VERSION || SourceEvent.isReceivable(eventVersion, sourceSeverity)

    fun toDomain(): SourceEvent = SourceEvent(
        eventId = eventId,
        eventType = eventType,
        eventVersion = eventVersion,
        workspaceId = workspaceId,
        seasonId = seasonId,
        sourceReference = sourceReference,
        aggregateRevision = aggregateRevision,
        occurredAt = occurredAt,
        state = state,
        sourceSeverity = sourceSeverity,
    )
}

data class EditionWeekRequest(
    val weekStart: LocalDate,
    val zoneId: ZoneId,
) {
    @get:AssertTrue(message = "weekStart는 0000~9999년의 월요일이어야 합니다")
    val validWeekStart: Boolean
        get() = weekStart.dayOfWeek == DayOfWeek.MONDAY && weekStart.year in SUPPORTED_YEARS

    @get:AssertTrue(message = "zoneId는 IANA 시간대 ID여야 합니다")
    val validIanaZone: Boolean
        get() = zoneId.id in ZoneId.getAvailableZoneIds()

    fun toCommand(
        workspaceId: UUID,
        seasonId: UUID,
    ): GenerateEditionCommand = GenerateEditionCommand(workspaceId, seasonId, weekStart, zoneId)
}

data class AttentionItemCursorRequest(
    val afterEventType: SourceEventType? = null,
    @field:CodePointLength(max = 128)
    @field:Pattern(regexp = SOURCE_REFERENCE_PATTERN)
    val afterSourceReference: String? = null,
) {
    @get:AssertTrue(message = "afterEventType과 afterSourceReference는 함께 제공하거나 생략해야 합니다")
    val validCursor: Boolean
        get() = (afterEventType == null) == (afterSourceReference == null)

    fun toCursor(): AttentionItemCursor? = afterEventType?.let {
        AttentionItemCursor(it, checkNotNull(afterSourceReference))
    }
}

data class IngestResponse(
    val eventId: UUID,
    val status: IngestStatus,
    val item: AttentionItemResponse?,
) {
    companion object {
        fun from(result: IngestResult): IngestResponse = IngestResponse(
            eventId = result.eventId,
            status = result.status,
            item = result.item?.let(AttentionItemResponse::from),
        )
    }
}

data class AttentionItemResponse(
    val reasonCode: SourceEventType,
    val severity: Severity,
    val sourceReference: String,
    val status: SourceEventState,
    val observedAt: Instant,
    val aggregateRevision: Long,
    val ruleVersion: Int,
    val revisionGap: Boolean,
) {
    companion object {
        fun from(item: AttentionItem): AttentionItemResponse = AttentionItemResponse(
            reasonCode = item.eventType,
            severity = item.severity,
            sourceReference = item.sourceReference,
            status = item.status,
            observedAt = item.observedAt,
            aggregateRevision = item.lastRevision,
            ruleVersion = item.ruleVersion,
            revisionGap = item.revisionGap,
        )
    }
}

data class CurrentAttentionItemPageResponse(
    val items: List<AttentionItemResponse>,
    val nextCursor: AttentionItemCursor?,
) {
    companion object {
        fun from(page: CurrentAttentionItemPage): CurrentAttentionItemPageResponse =
            CurrentAttentionItemPageResponse(
                items = page.items.map(AttentionItemResponse::from),
                nextCursor = page.nextCursor,
            )
    }
}

data class BriefEditionResponse(
    val editionId: UUID,
    val workspaceId: UUID,
    val seasonId: UUID,
    val generation: Long,
    val weekStart: LocalDate,
    val zoneId: ZoneId,
    val windowStart: Instant,
    val windowEnd: Instant,
    val sourceCursor: Long,
    val generatedAt: Instant,
    val ruleVersion: Int,
    val items: List<BriefEditionItem>,
) {
    companion object {
        fun from(edition: BriefEdition): BriefEditionResponse = BriefEditionResponse(
            editionId = edition.editionId,
            workspaceId = edition.workspaceId,
            seasonId = edition.seasonId,
            generation = edition.generation,
            weekStart = edition.window.weekStart,
            zoneId = edition.window.zoneId,
            windowStart = edition.window.start,
            windowEnd = edition.window.end,
            sourceCursor = edition.sourceCursor,
            generatedAt = edition.generatedAt,
            ruleVersion = edition.ruleVersion,
            items = edition.items,
        )
    }
}
