package com.personal.baton.brief.application

import com.personal.baton.brief.domain.AttentionItem
import com.personal.baton.brief.domain.AttentionProjector
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.BriefEditionItem
import com.personal.baton.brief.domain.EditionItemSection
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.SourceEventState
import com.personal.baton.brief.domain.WeeklyWindow
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class BriefService(
    private val persistence: BriefPersistencePort,
    private val clock: Clock,
) : BriefUseCases, BriefQueries by persistence {
    override fun ingest(event: SourceEvent): IngestResult {
        val normalizedEvent = event.copy(occurredAt = event.occurredAt.truncatedTo(ChronoUnit.MICROS))
        val currentTimestamp = { clock.instant().truncatedTo(ChronoUnit.MICROS) }
        val receivedAt = currentTimestamp()
        val fingerprint = fingerprint(normalizedEvent)
        if (!normalizedEvent.isSupported) {
            return persistence.recordUnsupported(normalizedEvent, fingerprint, receivedAt, currentTimestamp)
        }

        return persistence.processEvent(normalizedEvent, fingerprint, receivedAt, currentTimestamp) { current ->
            AttentionProjector.project(normalizedEvent, current)
        }
    }

    override fun summarizeWeeklyResolutions(
        command: GenerateEditionCommand,
        after: AttentionItemCursor?,
        limit: Int,
    ): WeeklyResolutionSummary = persistence.findWeeklyResolutions(
        command.workspaceId, command.seasonId, WeeklyWindow.startingOn(command.weekStart, command.zoneId),
        clock.instant().truncatedTo(ChronoUnit.MICROS), after, limit,
    )

    override fun rebuild(): RebuildResult = persistence.rebuild(AttentionProjector::project)

    override fun generateEdition(command: GenerateEditionCommand): EditionResult {
        val window = WeeklyWindow.startingOn(command.weekStart, command.zoneId)
        return persistence.createEdition(
            command,
            window,
            { clock.instant().truncatedTo(ChronoUnit.MICROS) },
            { selectEditionContent(it, window) },
        )
    }

    override fun compareEditions(
        baseEditionId: UUID,
        targetEditionId: UUID,
    ): EditionComparisonResult {
        val base = persistence.findEdition(baseEditionId)
        val target = persistence.findEdition(targetEditionId)
        if (base == null || target == null) {
            return EditionComparisonResult.NotFound
        }
        if (base.workspaceId != target.workspaceId || base.seasonId != target.seasonId) {
            return EditionComparisonResult.ScopeMismatch
        }

        val baseItemsByKey = base.items.associateBy { it.reasonCode to it.sourceReference }
        val targetItemsByKey = target.items.associateBy { it.reasonCode to it.sourceReference }
        val added = target.items.filter { (it.reasonCode to it.sourceReference) !in baseItemsByKey }
        val removed = base.items.filter { (it.reasonCode to it.sourceReference) !in targetItemsByKey }
        val changed = target.items.mapNotNull { after ->
            val before = baseItemsByKey[after.reasonCode to after.sourceReference] ?: return@mapNotNull null
            if (before == after) null else EditionItemChange(before, after)
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

    private fun selectEditionContent(items: List<AttentionItem>, window: WeeklyWindow): EditionContent {
        val selected = items
            .asSequence()
            .filter { it.status == SourceEventState.ACTIVE }
            .map {
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
            ).toList()
        return EditionContent(
            items = selected,
            stateFingerprint = sha256(
                selected.asSequence().flatMap { item ->
                    sequenceOf(
                        item.sourceReference,
                        item.reasonCode.name,
                        item.severity.name,
                        item.status.name,
                        item.observedAt,
                        item.ruleVersion,
                        item.aggregateRevision,
                        item.revisionGap,
                        item.section,
                    )
                },
            ),
        )
    }

    private fun fingerprint(event: SourceEvent): String {
        val values = sequenceOf(
            event.eventId,
            event.eventType.name,
            event.eventVersion,
            event.workspaceId,
            event.seasonId,
            event.sourceReference,
            event.aggregateRevision,
            event.occurredAt,
            event.state.name,
        )
        return sha256(event.sourceSeverity?.let { values + it.name } ?: values)
    }

    private fun sha256(values: Sequence<Any?>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DataOutputStream(
            DigestOutputStream(OutputStream.nullOutputStream(), digest),
        ).use { output ->
            values.forEach { value ->
                val encoded = value.toString().encodeToByteArray()
                output.writeInt(encoded.size)
                output.write(encoded)
            }
        }
        return digest.digest().toHexString()
    }
}
