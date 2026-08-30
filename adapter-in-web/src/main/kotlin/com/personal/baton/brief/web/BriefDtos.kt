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
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.UUID
import org.hibernate.validator.constraints.CodePointLength

private const val UUID_PATTERN =
    "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
internal const val SOURCE_REFERENCE_PATTERN = "[^\\u0000]*"

data class SourceEventRequest(
    @field:Pattern(regexp = UUID_PATTERN)
    val eventId: String,
    val eventType: SourceEventType,
    @field:Positive
    val eventVersion: Int,
    val sourceSeverity: SourceEventSeverity? = null,
    @field:Pattern(regexp = UUID_PATTERN)
    val workspaceId: String,
    @field:Pattern(regexp = UUID_PATTERN)
    val seasonId: String,
    @field:NotBlank
    @field:CodePointLength(max = 128)
    @field:Pattern(regexp = SOURCE_REFERENCE_PATTERN)
    val sourceReference: String,
    @field:Positive
    val aggregateRevision: Long,
    val occurredAt: String,
    val state: SourceEventState,
) {
    private val occurredAtInstant = runCatching {
        Instant.from(OCCURRED_AT_FORMATTER.parse(occurredAt))
    }.getOrNull()

    @get:AssertTrue(message = "occurredAt must be an ISO-8601 instant")
    val validOccurredAt: Boolean
        get() = occurredAtInstant != null

    @get:AssertTrue(message = "eventVersion, eventType and sourceSeverity must match")
    val validVersionContract: Boolean
        get() = eventVersion <= 0 ||
            SourceEvent.isReceivable(eventVersion, eventType, sourceSeverity)

    fun toDomain(): SourceEvent = SourceEvent(
        eventId = UUID.fromString(eventId),
        eventType = eventType,
        eventVersion = eventVersion,
        workspaceId = UUID.fromString(workspaceId),
        seasonId = UUID.fromString(seasonId),
        sourceReference = sourceReference,
        aggregateRevision = aggregateRevision,
        occurredAt = checkNotNull(occurredAtInstant),
        state = state,
        sourceSeverity = sourceSeverity,
    )

    companion object {
        private val OCCURRED_AT_FORMATTER = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(ChronoField.YEAR, 4)
            .appendPattern("-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .appendOffset("+HH:MM", "Z")
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)
    }
}

data class EditionWeekRequest(
    val weekStart: String,
    val zoneId: ZoneId,
) {
    private val weekStartDate = runCatching { LocalDate.parse(weekStart) }.getOrNull()

    @get:AssertTrue(message = "weekStart must be a Monday")
    val validWeekStart: Boolean
        get() = weekStartDate?.dayOfWeek == DayOfWeek.MONDAY

    @get:AssertTrue(message = "zoneId must be an IANA timezone ID")
    val validIanaZone: Boolean
        get() = zoneId.id in ZoneId.getAvailableZoneIds()

    fun toCommand(
        workspaceId: UUID,
        seasonId: UUID,
    ): GenerateEditionCommand = GenerateEditionCommand(
        workspaceId,
        seasonId,
        checkNotNull(weekStartDate),
        zoneId,
    )
}

data class CurrentAttentionItemPageRequest(
    val status: SourceEventState = SourceEventState.ACTIVE,
    val afterEventType: SourceEventType? = null,
    @field:CodePointLength(max = 128)
    @field:Pattern(regexp = SOURCE_REFERENCE_PATTERN)
    val afterSourceReference: String? = null,
) {
    @get:AssertTrue(message = "afterEventType and afterSourceReference must be provided together")
    val validCursor: Boolean
        get() = if (afterEventType == null) {
            afterSourceReference == null
        } else {
            !afterSourceReference.isNullOrBlank()
        }

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
