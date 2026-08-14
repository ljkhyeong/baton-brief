package com.personal.baton.brief.web

import com.personal.baton.brief.application.EditionHistoryResult
import com.personal.baton.brief.application.EditionSummary
import com.personal.baton.brief.application.GenerateEditionCommand
import com.personal.baton.brief.application.IngestResult
import com.personal.baton.brief.application.IngestStatus
import com.personal.baton.brief.domain.AttentionItem
import com.personal.baton.brief.domain.AttentionStatus
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.BriefEditionItem
import com.personal.baton.brief.domain.Severity
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.SourceEventState
import com.personal.baton.brief.domain.SourceEventType
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class SourceEventRequest(
    val eventId: UUID,
    val eventType: SourceEventType,
    @field:Positive
    val eventVersion: Int,
    val workspaceId: UUID,
    val seasonId: UUID,
    @field:NotBlank
    @field:Size(max = 128)
    val sourceReference: String,
    @field:Positive
    val aggregateRevision: Long,
    val occurredAt: String,
    val state: SourceEventState,
) {
    private val occurredAtInstant = runCatching { Instant.parse(occurredAt) }.getOrNull()

    @get:AssertTrue(message = "occurredAt must be an ISO-8601 instant")
    val validOccurredAt: Boolean
        get() = occurredAtInstant != null

    fun toDomain(): SourceEvent = SourceEvent(
        eventId = eventId,
        eventType = eventType,
        eventVersion = eventVersion,
        workspaceId = workspaceId,
        seasonId = seasonId,
        sourceReference = sourceReference,
        aggregateRevision = aggregateRevision,
        occurredAt = checkNotNull(occurredAtInstant),
        state = state,
    )
}

data class GenerateEditionRequest(
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
    val status: AttentionStatus,
    val observedAt: Instant,
    val aggregateRevision: Long,
    val ruleVersion: Int,
    val revisionGap: Boolean,
) {
    companion object {
        fun from(item: AttentionItem): AttentionItemResponse = AttentionItemResponse(
            reasonCode = item.reasonCode,
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

data class EditionItemResponse(
    val reasonCode: SourceEventType,
    val severity: Severity,
    val sourceReference: String,
    val status: AttentionStatus,
    val observedAt: Instant,
    val ruleVersion: Int,
) {
    companion object {
        fun from(item: BriefEditionItem): EditionItemResponse = EditionItemResponse(
            reasonCode = item.reasonCode,
            severity = item.severity,
            sourceReference = item.sourceReference,
            status = item.status,
            observedAt = item.observedAt,
            ruleVersion = item.ruleVersion,
        )
    }
}

data class BriefEditionResponse(
    val editionId: UUID,
    val workspaceId: UUID,
    val seasonId: UUID,
    val generation: Long,
    val weekStart: LocalDate,
    val zoneId: String,
    val windowStart: Instant,
    val windowEnd: Instant,
    val sourceCursor: Long,
    val generatedAt: Instant,
    val ruleVersion: Int,
    val items: List<EditionItemResponse>,
) {
    companion object {
        fun from(edition: BriefEdition): BriefEditionResponse = BriefEditionResponse(
            editionId = edition.editionId,
            workspaceId = edition.workspaceId,
            seasonId = edition.seasonId,
            generation = edition.generation,
            weekStart = edition.window.weekStart,
            zoneId = edition.window.zoneId.id,
            windowStart = edition.window.start,
            windowEnd = edition.window.end,
            sourceCursor = edition.sourceCursor,
            generatedAt = edition.generatedAt,
            ruleVersion = edition.ruleVersion,
            items = edition.items.map(EditionItemResponse::from),
        )
    }
}

data class EditionSummaryResponse(
    val editionId: UUID,
    val generation: Long,
    val weekStart: LocalDate,
    val zoneId: String,
    val generatedAt: Instant,
    val sourceCursor: Long,
    val ruleVersion: Int,
    val itemCount: Int,
) {
    companion object {
        fun from(summary: EditionSummary): EditionSummaryResponse = EditionSummaryResponse(
            editionId = summary.editionId,
            generation = summary.generation,
            weekStart = summary.weekStart,
            zoneId = summary.zoneId.id,
            generatedAt = summary.generatedAt,
            sourceCursor = summary.sourceCursor,
            ruleVersion = summary.ruleVersion,
            itemCount = summary.itemCount,
        )
    }
}

data class EditionHistoryResponse(
    val editions: List<EditionSummaryResponse>,
    val nextBeforeGeneration: Long?,
) {
    companion object {
        fun from(result: EditionHistoryResult): EditionHistoryResponse = EditionHistoryResponse(
            editions = result.editions.map(EditionSummaryResponse::from),
            nextBeforeGeneration = result.nextBeforeGeneration,
        )
    }
}
