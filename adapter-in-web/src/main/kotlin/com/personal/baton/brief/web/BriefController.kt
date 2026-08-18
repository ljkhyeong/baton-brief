package com.personal.baton.brief.web

import com.personal.baton.brief.application.BriefUseCases
import com.personal.baton.brief.application.EditionComparisonResult
import com.personal.baton.brief.application.IngestStatus
import com.personal.baton.brief.application.RebuildResult
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import java.net.URI
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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

    @PostMapping("/projections/rebuild")
    fun rebuild(): RebuildResult = brief.rebuild()

    @PostMapping("/workspaces/{workspaceId}/seasons/{seasonId}/editions")
    fun generateEdition(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @Valid @RequestBody request: GenerateEditionRequest,
    ): ResponseEntity<BriefEditionResponse> {
        val result = brief.generateEdition(request.toCommand(workspaceId, seasonId))
        val response = BriefEditionResponse.from(result.edition)
        return if (result.created) {
            ResponseEntity.created(URI.create("/api/v1/editions/${result.edition.editionId}"))
                .body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }

    @GetMapping("/workspaces/{workspaceId}/seasons/{seasonId}/editions/latest")
    fun findLatestEdition(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
    ): BriefEditionResponse = brief.findLatestEdition(workspaceId, seasonId)
        ?.let(BriefEditionResponse::from)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "edition not found")

    @GetMapping("/workspaces/{workspaceId}/seasons/{seasonId}/editions")
    fun findEditionHistory(
        @PathVariable("workspaceId") workspaceId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @RequestParam("beforeGeneration", required = false) @Positive beforeGeneration: Long?,
        @RequestParam("limit", defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): EditionHistoryResponse = EditionHistoryResponse.from(
        brief.findEditionHistory(workspaceId, seasonId, beforeGeneration, limit),
    )

    @GetMapping("/editions/{editionId}")
    fun findEdition(
        @PathVariable("editionId") editionId: UUID,
    ): BriefEditionResponse = brief.findEdition(editionId)
        ?.let(BriefEditionResponse::from)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "edition not found")

    @GetMapping("/editions/{targetEditionId}/changes")
    fun compareEditions(
        @PathVariable("targetEditionId") targetEditionId: UUID,
        @RequestParam("fromEditionId") fromEditionId: UUID,
    ): EditionComparisonResponse = when (val result = brief.compareEditions(fromEditionId, targetEditionId)) {
        is EditionComparisonResult.Found -> EditionComparisonResponse.from(result.comparison)
        EditionComparisonResult.NotFound -> throw ResponseStatusException(HttpStatus.NOT_FOUND, "edition not found")
        EditionComparisonResult.ScopeMismatch -> throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "editions must belong to the same workspace and season",
        )
    }
}
