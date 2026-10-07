package com.personal.baton.brief.application

import com.personal.baton.brief.domain.AttentionItem
import com.personal.baton.brief.domain.AttentionProjector
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.BriefEditionItem
import com.personal.baton.brief.domain.EditionItemSection
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.SourceEventType
import com.personal.baton.brief.domain.WeeklyWindow
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

class BriefService(
    private val persistence: BriefPersistencePort,
    private val clock: Clock,
) : BriefUseCases, BriefQueries by persistence {
    override fun ingest(event: SourceEvent): IngestResult {
        val normalizedEvent = event.copy(occurredAt = event.occurredAt.truncatedTo(ChronoUnit.MICROS))
        val receivedAt = now()
        if (!normalizedEvent.isSupported) {
            return persistence.recordUnsupported(normalizedEvent, receivedAt, ::now)
        }

        return persistence.processEvent(normalizedEvent, receivedAt, ::now) { current ->
            AttentionProjector.project(normalizedEvent, current)
        }
    }

    override fun summarizeWeeklyResolutions(
        command: GenerateEditionCommand,
        after: AttentionItemCursor?,
        limit: Int,
        eventType: SourceEventType?,
    ): WeeklyResolutionSummary = persistence.findWeeklyResolutions(command, now(), after, limit, eventType)

    override fun rebuild(): RebuildResult = persistence.rebuild(AttentionProjector::project)

    override fun generateEdition(command: GenerateEditionCommand): EditionResult =
        persistence.createEdition(command, ::now) { selectEditionItems(it, command.window) }

    override fun compareEditions(
        baseEditionId: UUID,
        targetEditionId: UUID,
    ): EditionComparisonResult = compareStoredEditions(baseEditionId, targetEditionId) { true }

    override fun compareEditionsInSeason(
        workspaceId: UUID,
        seasonId: UUID,
        baseEditionId: UUID,
        targetEditionId: UUID,
    ): EditionComparisonResult = compareStoredEditions(baseEditionId, targetEditionId) {
        it.workspaceId == workspaceId && it.seasonId == seasonId
    }

    override fun checkEditionFreshness(
        workspaceId: UUID,
        seasonId: UUID,
        editionId: UUID,
    ): EditionFreshness? {
        val evaluatedAt = now()
        val edition = persistence.findEdition(editionId)
            ?.takeIf { it.workspaceId == workspaceId && it.seasonId == seasonId }
            ?: return null
        val ruleVersionChanged = edition.ruleVersion != BriefEdition.RULE_VERSION
        // 생성과 같은 선정으로 지금 다시 만들 항목이 저장된 항목(필드·순서)과 같은지 확인한다.
        val command = GenerateEditionCommand(workspaceId, seasonId, edition.window.weekStart, edition.window.zoneId)
        val upToDate = !ruleVersionChanged &&
            selectEditionItems(persistence.findEditionCandidates(command), command.window) == edition.items
        return EditionFreshness(editionId, upToDate, ruleVersionChanged, evaluatedAt)
    }

    private fun compareStoredEditions(
        baseEditionId: UUID,
        targetEditionId: UUID,
        inScope: (BriefEdition) -> Boolean,
    ): EditionComparisonResult {
        val base = persistence.findEdition(baseEditionId)?.takeIf(inScope) ?: return EditionComparisonResult.NotFound
        val target = persistence.findEdition(targetEditionId)?.takeIf(inScope)
            ?: return EditionComparisonResult.NotFound
        if (base.workspaceId != target.workspaceId || base.seasonId != target.seasonId) {
            return EditionComparisonResult.ScopeMismatch
        }

        val baseItemsByKey = base.items.associateBy { it.comparisonKey }
        val targetItemsByKey = target.items.associateBy { it.comparisonKey }
        val added = target.items.filter { it.comparisonKey !in baseItemsByKey }
        val removed = base.items.filter { it.comparisonKey !in targetItemsByKey }
        val changed = target.items.mapNotNull { after ->
            baseItemsByKey[after.comparisonKey]?.takeIf { it != after }?.let { EditionItemChange(it, after) }
        }

        return EditionComparisonResult.Found(
            EditionComparison(
                from = EditionSummary.from(base),
                to = EditionSummary.from(target),
                added = added,
                removed = removed,
                changed = changed,
            ),
        )
    }

    private fun now() = clock.instant().truncatedTo(ChronoUnit.MICROS)

    private val BriefEditionItem.comparisonKey
        get() = reasonCode to sourceReference

    private fun selectEditionItems(items: List<AttentionItem>, window: WeeklyWindow): List<BriefEditionItem> =
        items.map {
            BriefEditionItem(
                sourceReference = it.sourceReference,
                reasonCode = it.eventType,
                severity = it.severity,
                status = it.status,
                observedAt = it.observedAt,
                ruleVersion = it.ruleVersion,
                aggregateRevision = it.lastRevision,
                revisionGap = it.revisionGap,
                section = if (it.observedAt < window.start) {
                    EditionItemSection.CARRY_OVER
                } else {
                    EditionItemSection.CURRENT_WEEK
                },
            )
        }.sortedWith(
            compareBy<BriefEditionItem> { it.section }
                .thenByDescending { it.severity }
                .thenBy { it.reasonCode.name }
                .thenBy { it.sourceReference },
        )
}
