package com.personal.baton.brief.application

import com.personal.baton.brief.domain.AttentionItem
import com.personal.baton.brief.domain.AttentionProjector
import com.personal.baton.brief.domain.AttentionStatus
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.BriefEditionItem
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.WeeklyWindow
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

class BriefService(
    private val persistence: BriefPersistencePort,
    private val clock: Clock,
) : BriefUseCases {
    private val projector = AttentionProjector()

    override fun ingest(event: SourceEvent): IngestResult {
        val normalizedEvent = event.copy(occurredAt = event.occurredAt.truncatedTo(ChronoUnit.MICROS))
        val receivedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val fingerprint = fingerprint(normalizedEvent)
        if (normalizedEvent.eventVersion != SUPPORTED_EVENT_VERSION) {
            return persistence.recordUnsupported(normalizedEvent, fingerprint, receivedAt)
        }

        return persistence.processEvent(normalizedEvent, fingerprint, receivedAt) { current ->
            projector.project(normalizedEvent, current, receivedAt)
        }
    }

    override fun rebuild(): RebuildResult = persistence.rebuild(projector::project)

    override fun generateEdition(command: GenerateEditionCommand): EditionResult {
        val window = WeeklyWindow.startingOn(command.weekStart, command.zoneId)
        return persistence.createEdition(
            command,
            window,
            { clock.instant().truncatedTo(ChronoUnit.MICROS) },
            ::selectEditionContent,
        )
    }

    override fun findEdition(editionId: UUID): BriefEdition? = persistence.findEdition(editionId)

    override fun findLatestEdition(
        workspaceId: UUID,
        seasonId: UUID,
    ): BriefEdition? = persistence.findLatestEdition(workspaceId, seasonId)

    private fun selectEditionContent(items: List<AttentionItem>): EditionContent {
        val selected = items
            .asSequence()
            .filter { it.status == AttentionStatus.ACTIVE }
            .sortedWith(
                compareByDescending<AttentionItem> { it.severity }
                    .thenBy { it.reasonCode.name }
                    .thenBy { it.sourceReference },
            ).map {
                BriefEditionItem(
                    sourceReference = it.sourceReference,
                    reasonCode = it.reasonCode,
                    severity = it.severity,
                    status = it.status,
                    observedAt = it.observedAt,
                    ruleVersion = it.ruleVersion,
                )
            }.toList()
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
                    )
                },
            ),
        )
    }

    private fun fingerprint(event: SourceEvent): String = sha256(
        sequenceOf(
            event.eventId,
            event.eventType.name,
            event.eventVersion,
            event.workspaceId,
            event.seasonId,
            event.sourceReference,
            event.aggregateRevision,
            event.occurredAt,
            event.state.name,
        ),
    )

    private fun sha256(values: Sequence<Any>): String {
        val canonical = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                values.forEach { value ->
                    val encoded = value.toString().toByteArray(StandardCharsets.UTF_8)
                    output.writeInt(encoded.size)
                    output.write(encoded)
                }
            }
            bytes.toByteArray()
        }
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(canonical),
        )
    }

    companion object {
        private const val SUPPORTED_EVENT_VERSION = 1
    }
}
