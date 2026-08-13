package com.personal.baton.brief.application

import com.personal.baton.brief.domain.AttentionItem
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.BriefEditionItem
import com.personal.baton.brief.domain.ProjectionDecision
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.WeeklyWindow
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class IngestStatus {
    APPLIED,
    APPLIED_WITH_GAP,
    DUPLICATE,
    CONFLICT,
    STALE,
    UNSUPPORTED,
}

data class IngestResult(
    val eventId: UUID,
    val status: IngestStatus,
    val item: AttentionItem? = null,
)

data class RebuildResult(
    val receiptCount: Int,
    val itemCount: Int,
)

data class GenerateEditionCommand(
    val workspaceId: UUID,
    val seasonId: UUID,
    val weekStart: LocalDate,
    val zoneId: ZoneId,
)

data class EditionResult(
    val edition: BriefEdition,
    val created: Boolean,
)

data class EditionContent(
    val items: List<BriefEditionItem>,
    val stateFingerprint: String,
)

interface BriefUseCases {
    fun ingest(event: SourceEvent): IngestResult

    fun rebuild(): RebuildResult

    fun generateEdition(command: GenerateEditionCommand): EditionResult

    fun findEdition(editionId: UUID): BriefEdition?

    fun findLatestEdition(
        workspaceId: UUID,
        seasonId: UUID,
    ): BriefEdition?
}

interface BriefPersistencePort {
    fun recordUnsupported(
        event: SourceEvent,
        fingerprint: String,
        receivedAt: java.time.Instant,
    ): IngestResult

    fun processEvent(
        event: SourceEvent,
        fingerprint: String,
        receivedAt: java.time.Instant,
        project: (AttentionItem?) -> ProjectionDecision,
    ): IngestResult

    fun rebuild(project: (SourceEvent, AttentionItem?, java.time.Instant) -> ProjectionDecision): RebuildResult

    fun createEdition(
        command: GenerateEditionCommand,
        window: WeeklyWindow,
        currentTime: () -> java.time.Instant,
        selectContent: (List<AttentionItem>) -> EditionContent,
    ): EditionResult

    fun findEdition(editionId: UUID): BriefEdition?

    fun findLatestEdition(
        workspaceId: UUID,
        seasonId: UUID,
    ): BriefEdition?
}
