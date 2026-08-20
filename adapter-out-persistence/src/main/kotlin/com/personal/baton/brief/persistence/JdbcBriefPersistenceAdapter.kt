package com.personal.baton.brief.persistence

import com.personal.baton.brief.application.BriefPersistencePort
import com.personal.baton.brief.application.EditionContent
import com.personal.baton.brief.application.EditionHistoryResult
import com.personal.baton.brief.application.EditionResult
import com.personal.baton.brief.application.EditionSummary
import com.personal.baton.brief.application.GenerateEditionCommand
import com.personal.baton.brief.application.IngestResult
import com.personal.baton.brief.application.IngestStatus
import com.personal.baton.brief.application.RebuildResult
import com.personal.baton.brief.application.SourceEventReceipt
import com.personal.baton.brief.domain.AttentionItem
import com.personal.baton.brief.domain.AttentionProjector
import com.personal.baton.brief.domain.AttentionStatus
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.BriefEditionItem
import com.personal.baton.brief.domain.ProjectionDecision
import com.personal.baton.brief.domain.Severity
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.SourceEventState
import com.personal.baton.brief.domain.SourceEventType
import com.personal.baton.brief.domain.WeeklyWindow
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JdbcBriefPersistenceAdapter(
    private val jdbc: JdbcClient,
) : BriefPersistencePort {
    @Transactional
    override fun recordUnsupported(
        event: SourceEvent,
        fingerprint: String,
        receivedAt: Instant,
    ): IngestResult {
        lockExclusive("event:${event.eventId}")
        findReceiptFingerprint(event.eventId)?.let { existingFingerprint ->
            return if (existingFingerprint == fingerprint) {
                IngestResult(event.eventId, IngestStatus.UNSUPPORTED)
            } else {
                recordConflict(event.eventId, fingerprint, receivedAt)
                IngestResult(event.eventId, IngestStatus.CONFLICT)
            }
        }

        insertReceipt(event, fingerprint, IngestStatus.UNSUPPORTED, receivedAt)
        return IngestResult(event.eventId, IngestStatus.UNSUPPORTED)
    }

    @Transactional
    override fun processEvent(
        event: SourceEvent,
        fingerprint: String,
        receivedAt: Instant,
        project: (AttentionItem?) -> ProjectionDecision,
    ): IngestResult {
        lockShared(PROJECTION_LOCK)
        lockExclusive("event:${event.eventId}")
        findReceiptFingerprint(event.eventId)?.let { existingFingerprint ->
            return if (existingFingerprint == fingerprint) {
                IngestResult(event.eventId, IngestStatus.DUPLICATE)
            } else {
                recordConflict(event.eventId, fingerprint, receivedAt)
                IngestResult(event.eventId, IngestStatus.CONFLICT)
            }
        }

        lockExclusive(event.identityLockKey())
        return when (val decision = project(findAttention(event))) {
            ProjectionDecision.Stale -> {
                insertReceipt(event, fingerprint, IngestStatus.STALE, receivedAt)
                IngestResult(event.eventId, IngestStatus.STALE)
            }

            is ProjectionDecision.Applied -> {
                val status = if (decision.hasRevisionGap) {
                    IngestStatus.APPLIED_WITH_GAP
                } else {
                    IngestStatus.APPLIED
                }
                insertReceipt(event, fingerprint, status, receivedAt)
                upsertAttention(decision.item)
                IngestResult(event.eventId, status, decision.item)
            }
        }
    }

    override fun findEventReceipt(eventId: UUID): SourceEventReceipt? = jdbc.sql(
        """
        SELECT receipt.event_id, receipt.ingestion_sequence, receipt.event_type,
               receipt.event_version, receipt.workspace_id, receipt.season_id,
               receipt.source_reference, receipt.aggregate_revision, receipt.occurred_at,
               receipt.event_state, receipt.processing_outcome, receipt.received_at,
               conflict.detected_at AS conflict_detected_at
          FROM source_event_receipt receipt
          LEFT JOIN source_event_conflict conflict ON conflict.event_id = receipt.event_id
         WHERE receipt.event_id = :eventId
        """.trimIndent(),
    ).param("eventId", eventId)
        .query { result, _ ->
            SourceEventReceipt(
                eventId = result.getObject("event_id", UUID::class.java),
                ingestionSequence = result.getLong("ingestion_sequence"),
                eventType = SourceEventType.valueOf(result.getString("event_type")),
                eventVersion = result.getInt("event_version"),
                workspaceId = result.getObject("workspace_id", UUID::class.java),
                seasonId = result.getObject("season_id", UUID::class.java),
                sourceReference = result.getString("source_reference"),
                aggregateRevision = result.getLong("aggregate_revision"),
                occurredAt = result.instant("occurred_at"),
                state = SourceEventState.valueOf(result.getString("event_state")),
                processingOutcome = IngestStatus.valueOf(result.getString("processing_outcome")),
                receivedAt = result.instant("received_at"),
                conflictDetectedAt = result
                    .getObject("conflict_detected_at", OffsetDateTime::class.java)
                    ?.toInstant(),
            )
        }.optional()
        .orElse(null)

    @Transactional
    override fun rebuild(
        project: (SourceEvent, AttentionItem?, Instant) -> ProjectionDecision,
    ): RebuildResult {
        lockExclusive(PROJECTION_LOCK)
        val current = linkedMapOf<EventIdentity, AttentionItem>()
        var receiptCount = 0
        jdbc.sql(
            """
            SELECT event_id, event_type, event_version, workspace_id, season_id,
                   source_reference, aggregate_revision, occurred_at, event_state, received_at
              FROM source_event_receipt
             WHERE processing_outcome <> 'UNSUPPORTED'
             ORDER BY ingestion_sequence
            """.trimIndent(),
        ).withFetchSize(500)
            .query(::mapReceiptEvent)
            .stream()
            .use { receipts ->
                receipts.forEachOrdered { receipt ->
                    receiptCount += 1
                    val identity = receipt.event.identity()
                    when (val decision = project(receipt.event, current[identity], receipt.receivedAt)) {
                        is ProjectionDecision.Applied -> current[identity] = decision.item
                        ProjectionDecision.Stale -> Unit
                    }
                }
            }

        jdbc.sql("DELETE FROM attention_item").update()
        current.values.forEach(::upsertAttention)
        return RebuildResult(receiptCount, current.size)
    }

    @Transactional
    override fun createEdition(
        command: GenerateEditionCommand,
        window: WeeklyWindow,
        currentTime: () -> Instant,
        selectContent: (List<AttentionItem>) -> EditionContent,
    ): EditionResult {
        lockExclusive(PROJECTION_LOCK)

        val candidates = findAttentionForWindow(command, window)
        val content = selectContent(candidates)
        findLatestEditionByState(command, content.stateFingerprint)?.let { existing ->
            return EditionResult(existing, created = false)
        }

        val generatedAt = currentTime()
        val sourceCursor = findSourceCursor(command)
        val generation = nextGeneration(command)
        val edition = BriefEdition(
            editionId = UUID.randomUUID(),
            workspaceId = command.workspaceId,
            seasonId = command.seasonId,
            generation = generation,
            window = window,
            ruleVersion = AttentionProjector.RULE_VERSION,
            sourceCursor = sourceCursor,
            generatedAt = generatedAt,
            items = content.items,
        )
        insertEdition(edition, content.stateFingerprint)
        insertEditionItems(edition)
        return EditionResult(edition, created = true)
    }

    override fun findEdition(editionId: UUID): BriefEdition? = findEditionRow(
        "WHERE edition_id = :editionId",
        mapOf("editionId" to editionId),
    )

    override fun findLatestEdition(
        workspaceId: UUID,
        seasonId: UUID,
    ): BriefEdition? = findEditionRow(
        "WHERE workspace_id = :workspaceId AND season_id = :seasonId ORDER BY generation DESC LIMIT 1",
        mapOf("workspaceId" to workspaceId, "seasonId" to seasonId),
    )

    override fun findLatestEditionForWeek(command: GenerateEditionCommand): BriefEdition? = findEditionRow(
        """
        WHERE workspace_id = :workspaceId
          AND season_id = :seasonId
          AND week_start = :weekStart
          AND zone_id = :zoneId
        ORDER BY generation DESC
        LIMIT 1
        """.trimIndent(),
        mapOf(
            "workspaceId" to command.workspaceId,
            "seasonId" to command.seasonId,
            "weekStart" to command.weekStart,
            "zoneId" to command.zoneId.id,
        ),
    )

    override fun findEditionHistory(
        workspaceId: UUID,
        seasonId: UUID,
        beforeGeneration: Long?,
        limit: Int,
    ): EditionHistoryResult {
        val beforeClause = if (beforeGeneration == null) {
            ""
        } else {
            "AND edition.generation < :beforeGeneration"
        }
        val parameters = mutableMapOf<String, Any>(
            "workspaceId" to workspaceId,
            "seasonId" to seasonId,
            "fetchLimit" to limit + 1,
        )
        if (beforeGeneration != null) {
            parameters["beforeGeneration"] = beforeGeneration
        }

        val summaries = jdbc.sql(
            """
            SELECT edition.edition_id, edition.generation, edition.week_start, edition.zone_id,
                   edition.generated_at, edition.source_cursor, edition.rule_version,
                   (
                       SELECT COUNT(*)
                         FROM brief_edition_item item
                        WHERE item.edition_id = edition.edition_id
                   ) AS item_count
              FROM brief_edition edition
             WHERE edition.workspace_id = :workspaceId
               AND edition.season_id = :seasonId
               $beforeClause
             ORDER BY edition.generation DESC
             LIMIT :fetchLimit
            """.trimIndent(),
        ).params(parameters)
            .query { result, _ ->
                EditionSummary(
                    editionId = result.getObject("edition_id", UUID::class.java),
                    generation = result.getLong("generation"),
                    weekStart = result.getObject("week_start", LocalDate::class.java),
                    zoneId = ZoneId.of(result.getString("zone_id")),
                    generatedAt = result.instant("generated_at"),
                    sourceCursor = result.getLong("source_cursor"),
                    ruleVersion = result.getInt("rule_version"),
                    itemCount = result.getInt("item_count"),
                )
            }.list()
        val hasNextPage = summaries.size > limit
        val editions = if (hasNextPage) summaries.take(limit) else summaries
        return EditionHistoryResult(
            editions = editions,
            nextBeforeGeneration = if (hasNextPage) editions.last().generation else null,
        )
    }

    private fun findReceiptFingerprint(eventId: UUID): String? = jdbc.sql(
        "SELECT payload_fingerprint FROM source_event_receipt WHERE event_id = :eventId",
    ).param("eventId", eventId)
        .query(String::class.java)
        .optional()
        .orElse(null)

    private fun insertReceipt(
        event: SourceEvent,
        fingerprint: String,
        outcome: IngestStatus,
        receivedAt: Instant,
    ) {
        jdbc.sql(
            """
            INSERT INTO source_event_receipt (
                event_id, event_type, event_version, workspace_id, season_id, source_reference,
                aggregate_revision, occurred_at, event_state, payload_fingerprint,
                processing_outcome, received_at
            ) VALUES (
                :eventId, :eventType, :eventVersion, :workspaceId, :seasonId, :sourceReference,
                :aggregateRevision, :occurredAt, :eventState, :fingerprint, :outcome, :receivedAt
            )
            """.trimIndent(),
        ).params(
            mapOf(
                "eventId" to event.eventId,
                "eventType" to event.eventType.name,
                "eventVersion" to event.eventVersion,
                "workspaceId" to event.workspaceId,
                "seasonId" to event.seasonId,
                "sourceReference" to event.sourceReference,
                "aggregateRevision" to event.aggregateRevision,
                "occurredAt" to event.occurredAt.jdbcValue(),
                "eventState" to event.state.name,
                "fingerprint" to fingerprint,
                "outcome" to outcome.name,
                "receivedAt" to receivedAt.jdbcValue(),
            ),
        ).update()
    }

    private fun recordConflict(
        eventId: UUID,
        fingerprint: String,
        detectedAt: Instant,
    ) {
        jdbc.sql(
            """
            INSERT INTO source_event_conflict (event_id, conflicting_fingerprint, detected_at)
            VALUES (:eventId, :fingerprint, :detectedAt)
            ON CONFLICT (event_id) DO NOTHING
            """.trimIndent(),
        ).params(
            mapOf(
                "eventId" to eventId,
                "fingerprint" to fingerprint,
                "detectedAt" to detectedAt.jdbcValue(),
            ),
        ).update()
    }

    private fun findAttention(event: SourceEvent): AttentionItem? = jdbc.sql(
        """
        SELECT * FROM attention_item
         WHERE workspace_id = :workspaceId
           AND season_id = :seasonId
           AND event_type = :eventType
           AND source_reference = :sourceReference
        """.trimIndent(),
    ).params(
        mapOf(
            "workspaceId" to event.workspaceId,
            "seasonId" to event.seasonId,
            "eventType" to event.eventType.name,
            "sourceReference" to event.sourceReference,
        ),
    ).query(::mapAttention).optional().orElse(null)

    private fun findAttentionForWindow(
        command: GenerateEditionCommand,
        window: WeeklyWindow,
    ): List<AttentionItem> = jdbc.sql(
        """
        SELECT * FROM attention_item
         WHERE workspace_id = :workspaceId
           AND season_id = :seasonId
           AND observed_at >= :windowStart
           AND observed_at < :windowEnd
        """.trimIndent(),
    ).params(
        mapOf(
            "workspaceId" to command.workspaceId,
            "seasonId" to command.seasonId,
            "windowStart" to window.start.jdbcValue(),
            "windowEnd" to window.end.jdbcValue(),
        ),
    ).query(::mapAttention).list()

    private fun upsertAttention(item: AttentionItem) {
        jdbc.sql(
            """
            INSERT INTO attention_item (
                item_id, workspace_id, season_id, event_type, source_reference, reason_code,
                severity, item_status, observed_at, projected_at, rule_version, last_revision,
                revision_gap
            ) VALUES (
                :itemId, :workspaceId, :seasonId, :eventType, :sourceReference, :reasonCode,
                :severity, :itemStatus, :observedAt, :projectedAt, :ruleVersion, :lastRevision,
                :revisionGap
            )
            ON CONFLICT (workspace_id, season_id, event_type, source_reference) DO UPDATE SET
                reason_code = EXCLUDED.reason_code,
                severity = EXCLUDED.severity,
                item_status = EXCLUDED.item_status,
                observed_at = EXCLUDED.observed_at,
                projected_at = EXCLUDED.projected_at,
                rule_version = EXCLUDED.rule_version,
                last_revision = EXCLUDED.last_revision,
                revision_gap = EXCLUDED.revision_gap
            """.trimIndent(),
        ).params(
            mapOf(
                "itemId" to item.itemId,
                "workspaceId" to item.workspaceId,
                "seasonId" to item.seasonId,
                "eventType" to item.eventType.name,
                "sourceReference" to item.sourceReference,
                "reasonCode" to item.reasonCode.name,
                "severity" to item.severity.name,
                "itemStatus" to item.status.name,
                "observedAt" to item.observedAt.jdbcValue(),
                "projectedAt" to item.projectedAt.jdbcValue(),
                "ruleVersion" to item.ruleVersion,
                "lastRevision" to item.lastRevision,
                "revisionGap" to item.revisionGap,
            ),
        ).update()
    }

    private fun findSourceCursor(command: GenerateEditionCommand): Long = jdbc.sql(
        """
        SELECT COALESCE(MAX(ingestion_sequence), 0) AS source_cursor
          FROM source_event_receipt
         WHERE workspace_id = :workspaceId
           AND season_id = :seasonId
           AND processing_outcome <> 'UNSUPPORTED'
        """.trimIndent(),
    ).params(
        mapOf(
            "workspaceId" to command.workspaceId,
            "seasonId" to command.seasonId,
        ),
    ).query(Long::class.java).single()

    private fun nextGeneration(command: GenerateEditionCommand): Long = jdbc.sql(
        """
        SELECT COALESCE(MAX(generation), 0) + 1
          FROM brief_edition
         WHERE workspace_id = :workspaceId AND season_id = :seasonId
        """.trimIndent(),
    ).params(mapOf("workspaceId" to command.workspaceId, "seasonId" to command.seasonId))
        .query(Long::class.java)
        .single()

    private fun findLatestEditionByState(
        command: GenerateEditionCommand,
        stateFingerprint: String,
    ): BriefEdition? = findEditionRow(
        """
        WHERE workspace_id = :workspaceId
          AND season_id = :seasonId
          AND week_start = :weekStart
          AND zone_id = :zoneId
          AND rule_version = :ruleVersion
          AND state_fingerprint = :stateFingerprint
          AND generation = (
              SELECT MAX(latest.generation)
                FROM brief_edition latest
               WHERE latest.workspace_id = :workspaceId
                 AND latest.season_id = :seasonId
                 AND latest.week_start = :weekStart
                 AND latest.zone_id = :zoneId
                 AND latest.rule_version = :ruleVersion
          )
        """.trimIndent(),
        mapOf(
            "workspaceId" to command.workspaceId,
            "seasonId" to command.seasonId,
            "weekStart" to command.weekStart,
            "zoneId" to command.zoneId.id,
            "ruleVersion" to AttentionProjector.RULE_VERSION,
            "stateFingerprint" to stateFingerprint,
        ),
    )

    private fun insertEdition(
        edition: BriefEdition,
        stateFingerprint: String,
    ) {
        jdbc.sql(
            """
            INSERT INTO brief_edition (
                edition_id, workspace_id, season_id, generation, week_start, zone_id,
                window_start, window_end, rule_version, source_cursor, state_fingerprint,
                generated_at
            ) VALUES (
                :editionId, :workspaceId, :seasonId, :generation, :weekStart, :zoneId,
                :windowStart, :windowEnd, :ruleVersion, :sourceCursor, :stateFingerprint,
                :generatedAt
            )
            """.trimIndent(),
        ).params(
            mapOf(
                "editionId" to edition.editionId,
                "workspaceId" to edition.workspaceId,
                "seasonId" to edition.seasonId,
                "generation" to edition.generation,
                "weekStart" to edition.window.weekStart,
                "zoneId" to edition.window.zoneId.id,
                "windowStart" to edition.window.start.jdbcValue(),
                "windowEnd" to edition.window.end.jdbcValue(),
                "ruleVersion" to edition.ruleVersion,
                "sourceCursor" to edition.sourceCursor,
                "stateFingerprint" to stateFingerprint,
                "generatedAt" to edition.generatedAt.jdbcValue(),
            ),
        ).update()
    }

    private fun insertEditionItems(edition: BriefEdition) {
        edition.items.forEachIndexed { position, item ->
            jdbc.sql(
                """
                INSERT INTO brief_edition_item (
                    edition_id, position, source_reference, reason_code, severity,
                    item_status, observed_at, rule_version, aggregate_revision, revision_gap
                ) VALUES (
                    :editionId, :position, :sourceReference, :reasonCode, :severity,
                    :itemStatus, :observedAt, :ruleVersion, :aggregateRevision, :revisionGap
                )
                """.trimIndent(),
            ).params(
                mapOf(
                    "editionId" to edition.editionId,
                    "position" to position,
                    "sourceReference" to item.sourceReference,
                    "reasonCode" to item.reasonCode.name,
                    "severity" to item.severity.name,
                    "itemStatus" to item.status.name,
                    "observedAt" to item.observedAt.jdbcValue(),
                    "ruleVersion" to item.ruleVersion,
                    "aggregateRevision" to item.aggregateRevision,
                    "revisionGap" to item.revisionGap,
                ),
            ).update()
        }
    }

    private fun findEditionRow(
        whereClause: String,
        parameters: Map<String, Any>,
    ): BriefEdition? {
        val edition = jdbc.sql(
            """
            SELECT edition_id, workspace_id, season_id, generation, week_start, zone_id,
                   window_start, window_end, rule_version, source_cursor, generated_at
              FROM brief_edition
              $whereClause
            """.trimIndent(),
        ).params(parameters)
            .query(::mapEditionWithoutItems)
            .optional()
            .orElse(null)
            ?: return null
        return edition.copy(items = findEditionItems(edition.editionId))
    }

    private fun findEditionItems(editionId: UUID): List<BriefEditionItem> = jdbc.sql(
        """
        SELECT source_reference, reason_code, severity, item_status, observed_at, rule_version,
               aggregate_revision, revision_gap
          FROM brief_edition_item
         WHERE edition_id = :editionId
         ORDER BY position
        """.trimIndent(),
    ).param("editionId", editionId)
        .query { result, _ ->
            BriefEditionItem(
                sourceReference = result.getString("source_reference"),
                reasonCode = SourceEventType.valueOf(result.getString("reason_code")),
                severity = Severity.valueOf(result.getString("severity")),
                status = AttentionStatus.valueOf(result.getString("item_status")),
                observedAt = result.instant("observed_at"),
                ruleVersion = result.getInt("rule_version"),
                aggregateRevision = result.getObject("aggregate_revision", Long::class.javaObjectType),
                revisionGap = result.getObject("revision_gap", Boolean::class.javaObjectType),
            )
        }.list()

    private fun lockExclusive(key: String) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
            .param("lockKey", key)
            .query { _, _ -> Unit }
            .single()
    }

    private fun lockShared(key: String) {
        jdbc.sql("SELECT pg_advisory_xact_lock_shared(hashtextextended(:lockKey, 0))")
            .param("lockKey", key)
            .query { _, _ -> Unit }
            .single()
    }

    private fun mapAttention(
        result: ResultSet,
        @Suppress("UNUSED_PARAMETER") rowNumber: Int,
    ): AttentionItem = AttentionItem(
        itemId = result.getObject("item_id", UUID::class.java),
        workspaceId = result.getObject("workspace_id", UUID::class.java),
        seasonId = result.getObject("season_id", UUID::class.java),
        eventType = SourceEventType.valueOf(result.getString("event_type")),
        sourceReference = result.getString("source_reference"),
        reasonCode = SourceEventType.valueOf(result.getString("reason_code")),
        severity = Severity.valueOf(result.getString("severity")),
        status = AttentionStatus.valueOf(result.getString("item_status")),
        observedAt = result.instant("observed_at"),
        projectedAt = result.instant("projected_at"),
        ruleVersion = result.getInt("rule_version"),
        lastRevision = result.getLong("last_revision"),
        revisionGap = result.getBoolean("revision_gap"),
    )

    private fun mapReceiptEvent(
        result: ResultSet,
        @Suppress("UNUSED_PARAMETER") rowNumber: Int,
    ): ReceiptEvent = ReceiptEvent(
        event = SourceEvent(
            eventId = result.getObject("event_id", UUID::class.java),
            eventType = SourceEventType.valueOf(result.getString("event_type")),
            eventVersion = result.getInt("event_version"),
            workspaceId = result.getObject("workspace_id", UUID::class.java),
            seasonId = result.getObject("season_id", UUID::class.java),
            sourceReference = result.getString("source_reference"),
            aggregateRevision = result.getLong("aggregate_revision"),
            occurredAt = result.instant("occurred_at"),
            state = SourceEventState.valueOf(result.getString("event_state")),
        ),
        receivedAt = result.instant("received_at"),
    )

    private fun mapEditionWithoutItems(
        result: ResultSet,
        @Suppress("UNUSED_PARAMETER") rowNumber: Int,
    ): BriefEdition {
        val weekStart = result.getObject("week_start", LocalDate::class.java)
        val zoneId = ZoneId.of(result.getString("zone_id"))
        return BriefEdition(
            editionId = result.getObject("edition_id", UUID::class.java),
            workspaceId = result.getObject("workspace_id", UUID::class.java),
            seasonId = result.getObject("season_id", UUID::class.java),
            generation = result.getLong("generation"),
            window = WeeklyWindow(
                weekStart = weekStart,
                zoneId = zoneId,
                start = result.instant("window_start"),
                end = result.instant("window_end"),
            ),
            ruleVersion = result.getInt("rule_version"),
            sourceCursor = result.getLong("source_cursor"),
            generatedAt = result.instant("generated_at"),
            items = emptyList(),
        )
    }

    private fun ResultSet.instant(column: String): Instant =
        getObject(column, OffsetDateTime::class.java).toInstant()

    private fun Instant.jdbcValue(): OffsetDateTime = atOffset(ZoneOffset.UTC)

    private fun SourceEvent.identity(): EventIdentity = EventIdentity(
        workspaceId,
        seasonId,
        eventType,
        sourceReference,
    )

    private fun SourceEvent.identityLockKey(): String =
        "attention:$workspaceId:$seasonId:${eventType.name}:$sourceReference"

    private data class ReceiptEvent(
        val event: SourceEvent,
        val receivedAt: Instant,
    )

    private data class EventIdentity(
        val workspaceId: UUID,
        val seasonId: UUID,
        val eventType: SourceEventType,
        val sourceReference: String,
    )

    companion object {
        private const val PROJECTION_LOCK = "brief:projection"
    }
}
