package com.personal.baton.brief.application

import com.personal.baton.brief.domain.AttentionItem
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.BriefEditionItem
import com.personal.baton.brief.domain.ProjectionDecision
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.SourceEventState
import com.personal.baton.brief.domain.SourceEventType
import com.personal.baton.brief.domain.WeeklyWindow
import java.time.Instant
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

data class SourceEventReceipt(
    val eventId: UUID,
    val ingestionSequence: Long,
    val eventType: SourceEventType,
    val eventVersion: Int,
    val workspaceId: UUID,
    val seasonId: UUID,
    val sourceReference: String,
    val aggregateRevision: Long,
    val occurredAt: Instant,
    val state: SourceEventState,
    val processingOutcome: IngestStatus,
    val receivedAt: Instant,
    val conflictDetectedAt: Instant?,
)

data class EventReceiptAnomalyResult(
    val receipts: List<SourceEventReceipt>,
    val nextBeforeIngestionSequence: Long?,
)

data class AttentionItemCursor(
    val eventType: SourceEventType,
    val sourceReference: String,
)

data class CurrentAttentionItemPage(
    val items: List<AttentionItem>,
    val nextCursor: AttentionItemCursor?,
)

data class AttentionItemTransition(
    val eventId: UUID,
    val aggregateRevision: Long,
    val state: SourceEventState,
    val observedAt: Instant,
    val detectedRevisionGap: Boolean,
)

data class AttentionItemTransitionHistory(
    val transitions: List<AttentionItemTransition>,
    val nextBeforeAggregateRevision: Long?,
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

data class EditionSummary(
    val editionId: UUID,
    val generation: Long,
    val weekStart: LocalDate,
    val zoneId: ZoneId,
    val generatedAt: Instant,
    val sourceCursor: Long,
    val ruleVersion: Int,
    val itemCount: Int,
) {
    companion object {
        fun from(edition: BriefEdition): EditionSummary = EditionSummary(
            editionId = edition.editionId,
            generation = edition.generation,
            weekStart = edition.window.weekStart,
            zoneId = edition.window.zoneId,
            generatedAt = edition.generatedAt,
            sourceCursor = edition.sourceCursor,
            ruleVersion = edition.ruleVersion,
            itemCount = edition.items.size,
        )
    }
}

data class EditionHistoryResult(
    val editions: List<EditionSummary>,
    val nextBeforeGeneration: Long?,
)

data class EditionItemChange(
    val before: BriefEditionItem,
    val after: BriefEditionItem,
)

data class EditionComparison(
    val from: EditionSummary,
    val to: EditionSummary,
    val added: List<BriefEditionItem>,
    val removed: List<BriefEditionItem>,
    val changed: List<EditionItemChange>,
)

sealed interface EditionComparisonResult {
    data class Found(
        val comparison: EditionComparison,
    ) : EditionComparisonResult

    data object NotFound : EditionComparisonResult

    data object ScopeMismatch : EditionComparisonResult
}

data class EditionContent(
    val items: List<BriefEditionItem>,
    val stateFingerprint: String,
)

interface BriefUseCases {
    fun ingest(event: SourceEvent): IngestResult

    fun findEventReceipt(eventId: UUID): SourceEventReceipt?

    fun findEventReceiptAnomalies(
        workspaceId: UUID,
        seasonId: UUID,
        beforeIngestionSequence: Long?,
        limit: Int,
    ): EventReceiptAnomalyResult

    fun findAttentionItem(
        workspaceId: UUID,
        seasonId: UUID,
        eventType: SourceEventType,
        sourceReference: String,
    ): AttentionItem?

    fun findAttentionItems(
        workspaceId: UUID,
        seasonId: UUID,
        status: SourceEventState,
        after: AttentionItemCursor?,
        limit: Int,
    ): CurrentAttentionItemPage

    fun findAttentionItemTransitions(
        workspaceId: UUID,
        seasonId: UUID,
        eventType: SourceEventType,
        sourceReference: String,
        beforeAggregateRevision: Long?,
        limit: Int,
    ): AttentionItemTransitionHistory

    fun rebuild(): RebuildResult

    fun generateEdition(command: GenerateEditionCommand): EditionResult

    fun findEdition(editionId: UUID): BriefEdition?

    fun findLatestEdition(
        workspaceId: UUID,
        seasonId: UUID,
    ): BriefEdition?

    fun findLatestEditionForWeek(command: GenerateEditionCommand): BriefEdition?

    fun findEditionHistory(
        workspaceId: UUID,
        seasonId: UUID,
        beforeGeneration: Long?,
        limit: Int,
    ): EditionHistoryResult

    fun compareEditions(
        baseEditionId: UUID,
        targetEditionId: UUID,
    ): EditionComparisonResult
}

interface BriefPersistencePort {
    fun recordUnsupported(
        event: SourceEvent,
        fingerprint: String,
        receivedAt: Instant,
    ): IngestResult

    fun processEvent(
        event: SourceEvent,
        fingerprint: String,
        receivedAt: Instant,
        project: (AttentionItem?) -> ProjectionDecision,
    ): IngestResult

    fun findEventReceipt(eventId: UUID): SourceEventReceipt?

    fun findEventReceiptAnomalies(
        workspaceId: UUID,
        seasonId: UUID,
        beforeIngestionSequence: Long?,
        limit: Int,
    ): EventReceiptAnomalyResult

    fun findAttentionItem(
        workspaceId: UUID,
        seasonId: UUID,
        eventType: SourceEventType,
        sourceReference: String,
    ): AttentionItem?

    fun findAttentionItems(
        workspaceId: UUID,
        seasonId: UUID,
        status: SourceEventState,
        after: AttentionItemCursor?,
        limit: Int,
    ): CurrentAttentionItemPage

    fun findAttentionItemTransitions(
        workspaceId: UUID,
        seasonId: UUID,
        eventType: SourceEventType,
        sourceReference: String,
        beforeAggregateRevision: Long?,
        limit: Int,
    ): AttentionItemTransitionHistory

    fun rebuild(project: (SourceEvent, AttentionItem?) -> ProjectionDecision): RebuildResult

    fun createEdition(
        command: GenerateEditionCommand,
        window: WeeklyWindow,
        currentTime: () -> Instant,
        selectContent: (List<AttentionItem>) -> EditionContent,
    ): EditionResult

    fun findEdition(editionId: UUID): BriefEdition?

    fun findLatestEdition(
        workspaceId: UUID,
        seasonId: UUID,
    ): BriefEdition?

    fun findLatestEditionForWeek(command: GenerateEditionCommand): BriefEdition?

    fun findEditionHistory(
        workspaceId: UUID,
        seasonId: UUID,
        beforeGeneration: Long?,
        limit: Int,
    ): EditionHistoryResult
}
