package com.personal.baton.brief.web

import com.personal.baton.brief.application.AttentionItemTransitionHistory
import com.personal.baton.brief.application.BriefUseCases
import com.personal.baton.brief.application.EditionComparison
import com.personal.baton.brief.application.EditionComparisonResult
import com.personal.baton.brief.application.EditionHistoryResult
import com.personal.baton.brief.application.EventReceiptAnomalyResult
import com.personal.baton.brief.application.IngestStatus
import com.personal.baton.brief.application.RebuildResult
import com.personal.baton.brief.application.SourceEventReceipt
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.SourceEventType
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.net.URI
import java.util.UUID
import org.hibernate.validator.constraints.CodePointLength
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1")
class BriefController(
    private val brief: BriefUseCases,
) {
    @PostMapping("/events")
    fun ingest(
        @Valid @RequestBody request: SourceEventRequest,
    ): ResponseEntity<IngestResponse> {
        val result = brief.ingest(request.toDomain())
        val status = when (result.status) {
            IngestStatus.APPLIED,
            IngestStatus.APPLIED_WITH_GAP,
            -> HttpStatus.ACCEPTED
            IngestStatus.DUPLICATE,
            IngestStatus.STALE,
            -> HttpStatus.OK
            IngestStatus.CONFLICT -> HttpStatus.CONFLICT
            IngestStatus.UNSUPPORTED -> HttpStatus.UNPROCESSABLE_CONTENT
        }
        return ResponseEntity.status(status).body(IngestResponse.from(result))
    }

    @GetMapping("/events/{eventId}/receipt")
    fun findEventReceipt(
        @PathVariable("eventId") eventId: UUID,
    ): SourceEventReceipt = brief.findEventReceipt(eventId)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "event receipt not found")

    @GetMapping("/workspaces/{workspaceId}/seasons/{seasonId}/event-receipts/anomalies")
    fun findEventReceiptAnomalies(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @RequestParam("beforeIngestionSequence", required = false)
        @Positive beforeIngestionSequence: Long?,
        @RequestParam("limit", defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): EventReceiptAnomalyResult = brief.findEventReceiptAnomalies(
        workspaceId,
        seasonId,
        beforeIngestionSequence,
        limit,
    )

    @GetMapping("/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/current")
    fun findAttentionItem(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @RequestParam("eventType") eventType: SourceEventType,
        @RequestParam("sourceReference") @CodePointLength(max = 128)
        @Pattern(regexp = SOURCE_REFERENCE_PATTERN) sourceReference: String,
    ): ResponseEntity<AttentionItemResponse> {
        val item = brief.findAttentionItem(
            workspaceId,
            seasonId,
            eventType,
            sourceReference,
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "attention item not found")
        return ResponseEntity.ok()
            .eTag("brief-attention-item-v1-${item.ruleVersion}-${item.lastRevision}")
            .body(AttentionItemResponse.from(item))
    }

    @GetMapping("/workspaces/{workspaceId}/seasons/{seasonId}/attention-items")
    fun findAttentionItems(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @Valid @ModelAttribute request: CurrentAttentionItemPageRequest,
        @RequestParam("limit", defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): CurrentAttentionItemPageResponse = CurrentAttentionItemPageResponse.from(
        brief.findAttentionItems(
            workspaceId,
            seasonId,
            request.status,
            request.toCursor(),
            limit,
        ),
    )

    @GetMapping("/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/transitions")
    fun findAttentionItemTransitions(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @RequestParam("eventType") eventType: SourceEventType,
        @RequestParam("sourceReference") @CodePointLength(max = 128)
        @Pattern(regexp = SOURCE_REFERENCE_PATTERN) sourceReference: String,
        @RequestParam("beforeAggregateRevision", required = false)
        @Positive beforeAggregateRevision: Long?,
        @RequestParam("limit", defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): AttentionItemTransitionHistory = brief.findAttentionItemTransitions(
        workspaceId,
        seasonId,
        eventType,
        sourceReference,
        beforeAggregateRevision,
        limit,
    )

    @PostMapping("/projections/rebuild")
    fun rebuild(): RebuildResult = brief.rebuild()

    @PostMapping("/workspaces/{workspaceId}/seasons/{seasonId}/editions")
    fun generateEdition(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @Valid @RequestBody request: EditionWeekRequest,
    ): ResponseEntity<BriefEditionResponse> {
        val result = brief.generateEdition(request.toCommand(workspaceId, seasonId))
        val response = if (result.created) {
            ResponseEntity.created(URI.create("/api/v1/editions/${result.edition.editionId}"))
        } else {
            ResponseEntity.ok()
        }
        return result.edition.toResponse(response)
    }

    @GetMapping("/workspaces/{workspaceId}/seasons/{seasonId}/editions/latest")
    fun findLatestEdition(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
    ): ResponseEntity<BriefEditionResponse> = brief.findLatestEdition(workspaceId, seasonId)
        ?.toResponse()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "edition not found")

    @GetMapping("/workspaces/{workspaceId}/seasons/{seasonId}/editions/weekly/latest")
    fun findLatestEditionForWeek(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @Valid @ModelAttribute request: EditionWeekRequest,
    ): ResponseEntity<BriefEditionResponse> =
        brief.findLatestEditionForWeek(request.toCommand(workspaceId, seasonId))
            ?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "edition not found")

    @GetMapping("/workspaces/{workspaceId}/seasons/{seasonId}/editions")
    fun findEditionHistory(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @RequestParam("beforeGeneration", required = false) @Positive beforeGeneration: Long?,
        @RequestParam("limit", defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): EditionHistoryResult = brief.findEditionHistory(workspaceId, seasonId, beforeGeneration, limit)

    @GetMapping("/editions/{editionId}")
    fun findEdition(
        @PathVariable("editionId") editionId: UUID,
    ): ResponseEntity<BriefEditionResponse> = brief.findEdition(editionId)
        ?.toResponse()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "edition not found")

    @GetMapping("/editions/{targetEditionId}/changes")
    fun compareEditions(
        @PathVariable("targetEditionId") targetEditionId: UUID,
        @RequestParam("fromEditionId") fromEditionId: UUID,
    ): EditionComparison = when (val result = brief.compareEditions(fromEditionId, targetEditionId)) {
        is EditionComparisonResult.Found -> result.comparison
        EditionComparisonResult.NotFound -> throw ResponseStatusException(HttpStatus.NOT_FOUND, "edition not found")
        EditionComparisonResult.ScopeMismatch -> throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "editions must belong to the same workspace and season",
        )
    }
}

private fun BriefEdition.toResponse(
    builder: ResponseEntity.BodyBuilder = ResponseEntity.ok(),
): ResponseEntity<BriefEditionResponse> = builder
    .eTag("brief-edition-v1-$editionId")
    .body(BriefEditionResponse.from(this))
