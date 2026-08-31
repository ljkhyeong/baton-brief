package com.personal.baton.brief.persistence

import com.personal.baton.brief.application.AttentionItemCursor
import com.personal.baton.brief.application.AttentionItemTransition
import com.personal.baton.brief.application.AttentionItemTransitionHistory
import com.personal.baton.brief.application.BriefPersistencePort
import com.personal.baton.brief.application.CurrentAttentionItemPage
import com.personal.baton.brief.application.CurrentAttentionItemSummary
import com.personal.baton.brief.application.EditionContent
import com.personal.baton.brief.application.EditionHistoryResult
import com.personal.baton.brief.application.EditionResult
import com.personal.baton.brief.application.EditionSummary
import com.personal.baton.brief.application.EventReceiptAnomalyResult
import com.personal.baton.brief.application.GenerateEditionCommand
import com.personal.baton.brief.application.IngestResult
import com.personal.baton.brief.application.IngestStatus
import com.personal.baton.brief.application.RebuildResult
import com.personal.baton.brief.application.SourceEventReceipt
import com.personal.baton.brief.domain.AttentionItem
import com.personal.baton.brief.domain.AttentionProjector
import com.personal.baton.brief.domain.BriefEdition
import com.personal.baton.brief.domain.BriefEditionItem
import com.personal.baton.brief.domain.ProjectionDecision
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
import kotlin.jvm.optionals.getOrNull
import org.springframework.jdbc.core.DataClassRowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JdbcBriefPersistenceAdapter(
    private val jdbc: JdbcClient,
    private val namedJdbc: NamedParameterJdbcOperations,
) : BriefPersistencePort {
    @Transactional
    override fun recordUnsupported(
        event: SourceEvent,
        fingerprint: String,
        receivedAt: Instant,
        conflictDetectedAt: () -> Instant,
    ): IngestResult {
        lockExclusive("event:${event.eventId}")
        findExistingEventResult(
            event.eventId,
            fingerprint,
            IngestStatus.UNSUPPORTED,
            conflictDetectedAt,
        )?.let { return it }

        insertReceipt(event, fingerprint, IngestStatus.UNSUPPORTED, receivedAt)
        return IngestResult(event.eventId, IngestStatus.UNSUPPORTED)
    }

    @Transactional
    override fun processEvent(
        event: SourceEvent,
        fingerprint: String,
        receivedAt: Instant,
        conflictDetectedAt: () -> Instant,
        project: (AttentionItem?) -> ProjectionDecision,
    ): IngestResult {
        lockShared(PROJECTION_LOCK)
        lockExclusive("event:${event.eventId}")
        findExistingEventResult(
            event.eventId,
            fingerprint,
            IngestStatus.DUPLICATE,
            conflictDetectedAt,
        )?.let { return it }

        lockExclusive(
            "attention:${event.workspaceId}:${event.seasonId}:${event.eventType.name}:${event.sourceReference}",
        )
        val current = findAttentionItem(
            event.workspaceId,
            event.seasonId,
            event.eventType,
            event.sourceReference,
        )
        return when (val decision = project(current)) {
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
                jdbc.sql(UPSERT_ATTENTION).params(decision.item.jdbcParameters()).update()
                IngestResult(event.eventId, status, decision.item)
            }
        }
    }

    override fun findEventReceipt(eventId: UUID): SourceEventReceipt? = jdbc.sql(
        """
        $SOURCE_EVENT_RECEIPT_SELECT
         WHERE receipt.event_id = :eventId
        """.trimIndent(),
    ).param("eventId", eventId)
        .query(SOURCE_EVENT_RECEIPT_MAPPER)
        .optional()
        .getOrNull()

    override fun findEventReceiptAnomalies(
        workspaceId: UUID,
        seasonId: UUID,
        beforeIngestionSequence: Long?,
        limit: Int,
    ): EventReceiptAnomalyResult {
        val beforeClause = if (beforeIngestionSequence == null) {
            ""
        } else {
            "AND receipt.ingestion_sequence < :beforeIngestionSequence"
        }
        val parameters = mutableMapOf<String, Any>(
            "workspaceId" to workspaceId,
            "seasonId" to seasonId,
            "fetchLimit" to limit + 1,
        )
        if (beforeIngestionSequence != null) {
            parameters["beforeIngestionSequence"] = beforeIngestionSequence
        }

        val fetched = jdbc.sql(
            """
            $SOURCE_EVENT_RECEIPT_SELECT
             WHERE receipt.workspace_id = :workspaceId
               AND receipt.season_id = :seasonId
               AND (
                   receipt.processing_outcome IN ('APPLIED_WITH_GAP', 'STALE', 'UNSUPPORTED')
                   OR conflict.event_id IS NOT NULL
               )
               $beforeClause
             ORDER BY receipt.ingestion_sequence DESC
             LIMIT :fetchLimit
            """.trimIndent(),
        ).params(parameters)
            .query(SOURCE_EVENT_RECEIPT_MAPPER)
            .list()
        val hasNextPage = fetched.size > limit
        val receipts = fetched.take(limit)
        return EventReceiptAnomalyResult(
            receipts = receipts,
            nextBeforeIngestionSequence = if (hasNextPage) receipts.last().ingestionSequence else null,
        )
    }

    override fun findAttentionItem(
        workspaceId: UUID,
        seasonId: UUID,
        eventType: SourceEventType,
        sourceReference: String,
    ): AttentionItem? = jdbc.sql(
        """
        $ATTENTION_ITEM_SELECT
         WHERE workspace_id = :workspaceId
           AND season_id = :seasonId
           AND event_type = :eventType
           AND source_reference = :sourceReference
        """.trimIndent(),
    ).params(
        mapOf(
            "workspaceId" to workspaceId,
            "seasonId" to seasonId,
            "eventType" to eventType.name,
            "sourceReference" to sourceReference,
        ),
    ).query(ATTENTION_ITEM_MAPPER).optional().getOrNull()

    override fun findAttentionItems(
        workspaceId: UUID,
        seasonId: UUID,
        status: SourceEventState,
        after: AttentionItemCursor?,
        limit: Int,
    ): CurrentAttentionItemPage {
        val afterClause = if (after == null) {
            ""
        } else {
            """
            AND (event_type, source_reference) > (:afterEventType, :afterSourceReference)
            """.trimIndent()
        }
        val parameters = mutableMapOf<String, Any>(
            "workspaceId" to workspaceId,
            "seasonId" to seasonId,
            "status" to status.name,
            "fetchLimit" to limit + 1,
        )
        if (after != null) {
            parameters["afterEventType"] = after.eventType.name
            parameters["afterSourceReference"] = after.sourceReference
        }

        val fetched = jdbc.sql(
            """
            $ATTENTION_ITEM_SELECT
             WHERE workspace_id = :workspaceId
               AND season_id = :seasonId
               AND item_status = :status
               $afterClause
             ORDER BY event_type, source_reference
             LIMIT :fetchLimit
            """.trimIndent(),
        ).params(parameters)
            .query(ATTENTION_ITEM_MAPPER)
            .list()
        val items = fetched.take(limit)
        return CurrentAttentionItemPage(
            items = items,
            nextCursor = if (fetched.size > limit) {
                items.last().let { AttentionItemCursor(it.eventType, it.sourceReference) }
            } else {
                null
            },
        )
    }

    override fun findAttentionItemSummary(workspaceId: UUID, seasonId: UUID): CurrentAttentionItemSummary = jdbc.sql(
        """
        SELECT COUNT(*) FILTER (WHERE severity = 'HIGH') AS high_count,
               COUNT(*) FILTER (WHERE severity = 'MEDIUM') AS medium_count,
               COUNT(*) FILTER (WHERE revision_gap) AS revision_gap_count
          FROM attention_item
         WHERE workspace_id = :workspaceId
           AND season_id = :seasonId
           AND item_status = 'ACTIVE'
        """.trimIndent(),
    ).param("workspaceId", workspaceId)
        .param("seasonId", seasonId)
        .query(ATTENTION_ITEM_SUMMARY_MAPPER)
        .single()

    override fun findAttentionItemTransitions(
        workspaceId: UUID,
        seasonId: UUID,
        eventType: SourceEventType,
        sourceReference: String,
        beforeAggregateRevision: Long?,
        limit: Int,
    ): AttentionItemTransitionHistory {
        val beforeClause = if (beforeAggregateRevision == null) {
            ""
        } else {
            "AND aggregate_revision < :beforeAggregateRevision"
        }
        val parameters = mutableMapOf<String, Any>(
            "workspaceId" to workspaceId,
            "seasonId" to seasonId,
            "eventType" to eventType.name,
            "sourceReference" to sourceReference,
            "fetchLimit" to limit + 1,
        )
        if (beforeAggregateRevision != null) {
            parameters["beforeAggregateRevision"] = beforeAggregateRevision
        }

        val fetched = jdbc.sql(
            """
            SELECT event_id, aggregate_revision, event_state AS state, occurred_at AS observed_at,
                   processing_outcome = 'APPLIED_WITH_GAP' AS detected_revision_gap
              FROM source_event_receipt
             WHERE workspace_id = :workspaceId
               AND season_id = :seasonId
               AND event_type = :eventType
               AND source_reference = :sourceReference
               AND processing_outcome IN ('APPLIED', 'APPLIED_WITH_GAP')
               $beforeClause
             ORDER BY aggregate_revision DESC
             LIMIT :fetchLimit
            """.trimIndent(),
        ).params(parameters)
            .query(ATTENTION_ITEM_TRANSITION_MAPPER)
            .list()
        val transitions = fetched.take(limit)
        return AttentionItemTransitionHistory(
            transitions = transitions,
            nextBeforeAggregateRevision = if (fetched.size > limit) {
                transitions.last().aggregateRevision
            } else {
                null
            },
        )
    }

    @Transactional
    override fun rebuild(
        project: (SourceEvent, AttentionItem?) -> ProjectionDecision,
    ): RebuildResult {
        lockExclusive(PROJECTION_LOCK)
        val current = linkedMapOf<EventIdentity, AttentionItem>()
        var receiptCount = 0
        jdbc.sql(
            """
            SELECT event_id, event_type, event_version, source_severity, workspace_id, season_id,
                   source_reference, aggregate_revision, occurred_at, event_state AS state
              FROM source_event_receipt
             WHERE processing_outcome <> 'UNSUPPORTED'
             ORDER BY ingestion_sequence
            """.trimIndent(),
        ).withFetchSize(500)
            .query(SOURCE_EVENT_MAPPER)
            .stream()
            .use { events ->
                events.forEachOrdered { event ->
                    receiptCount += 1
                    val identity = EventIdentity(
                        event.workspaceId,
                        event.seasonId,
                        event.eventType,
                        event.sourceReference,
                    )
                    when (val decision = project(event, current[identity])) {
                        is ProjectionDecision.Applied -> current[identity] = decision.item
                        ProjectionDecision.Stale -> Unit
                    }
                }
            }

        jdbc.sql("DELETE FROM attention_item").update()
        namedJdbc.batchUpdate(
            UPSERT_ATTENTION,
            current.values.map { it.jdbcParameters() }.toTypedArray(),
        )
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
            .query(EDITION_SUMMARY_MAPPER)
            .list()
        val hasNextPage = summaries.size > limit
        val editions = summaries.take(limit)
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
        .getOrNull()

    private fun findExistingEventResult(
        eventId: UUID,
        fingerprint: String,
        replayStatus: IngestStatus,
        conflictDetectedAt: () -> Instant,
    ): IngestResult? {
        val existingFingerprint = findReceiptFingerprint(eventId) ?: return null
        if (existingFingerprint == fingerprint) {
            return IngestResult(eventId, replayStatus)
        }

        recordConflict(eventId, fingerprint, conflictDetectedAt())
        return IngestResult(eventId, IngestStatus.CONFLICT)
    }

    private fun insertReceipt(
        event: SourceEvent,
        fingerprint: String,
        outcome: IngestStatus,
        receivedAt: Instant,
    ) {
        jdbc.sql(
            """
            INSERT INTO source_event_receipt (
                event_id, event_type, event_version, source_severity, workspace_id, season_id, source_reference,
                aggregate_revision, occurred_at, event_state, payload_fingerprint,
                processing_outcome, received_at
            ) VALUES (
                :eventId, :eventType, :eventVersion, :sourceSeverity, :workspaceId, :seasonId, :sourceReference,
                :aggregateRevision, :occurredAt, :eventState, :fingerprint, :outcome, :receivedAt
            )
            """.trimIndent(),
        ).params(
            mapOf<String, Any?>(
                "eventId" to event.eventId,
                "eventType" to event.eventType.name,
                "eventVersion" to event.eventVersion,
                "sourceSeverity" to event.sourceSeverity?.name,
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

    private fun findAttentionForWindow(
        command: GenerateEditionCommand,
        window: WeeklyWindow,
    ): List<AttentionItem> = jdbc.sql(
        """
        $ATTENTION_ITEM_SELECT
         WHERE workspace_id = :workspaceId
           AND season_id = :seasonId
           AND item_status = 'ACTIVE'
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
    ).query(ATTENTION_ITEM_MAPPER).list()

    private fun AttentionItem.jdbcParameters(): Map<String, Any> = mapOf(
        "workspaceId" to workspaceId,
        "seasonId" to seasonId,
        "eventType" to eventType.name,
        "sourceReference" to sourceReference,
        "severity" to severity.name,
        "itemStatus" to status.name,
        "observedAt" to observedAt.jdbcValue(),
        "ruleVersion" to ruleVersion,
        "lastRevision" to lastRevision,
        "revisionGap" to revisionGap,
    )

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
        namedJdbc.batchUpdate(
            """
            INSERT INTO brief_edition_item (
                edition_id, position, source_reference, reason_code, severity,
                item_status, observed_at, rule_version, aggregate_revision, revision_gap
            ) VALUES (
                :editionId, :position, :sourceReference, :reasonCode, :severity,
                :itemStatus, :observedAt, :ruleVersion, :aggregateRevision, :revisionGap
            )
            """.trimIndent(),
            edition.items.mapIndexed { position, item ->
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
                )
            }.toTypedArray(),
        )
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
            .getOrNull()
            ?: return null
        return edition.copy(items = findEditionItems(edition.editionId))
    }

    private fun findEditionItems(editionId: UUID): List<BriefEditionItem> = jdbc.sql(
        """
        SELECT source_reference, reason_code, severity, item_status AS status, observed_at, rule_version,
               aggregate_revision, revision_gap
          FROM brief_edition_item
         WHERE edition_id = :editionId
         ORDER BY position
        """.trimIndent(),
    ).param("editionId", editionId)
        .query(EDITION_ITEM_MAPPER)
        .list()

    private fun lockExclusive(key: String) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
            .param("lockKey", key)
            .query()
            .singleValue()
    }

    private fun lockShared(key: String) {
        jdbc.sql("SELECT pg_advisory_xact_lock_shared(hashtextextended(:lockKey, 0))")
            .param("lockKey", key)
            .query()
            .singleValue()
    }

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

    private data class EventIdentity(
        val workspaceId: UUID,
        val seasonId: UUID,
        val eventType: SourceEventType,
        val sourceReference: String,
    )

    companion object {
        private const val PROJECTION_LOCK = "brief:projection"
        private val SOURCE_EVENT_RECEIPT_MAPPER = PostgresDataClassRowMapper(SourceEventReceipt::class.java)
        private val SOURCE_EVENT_MAPPER = PostgresDataClassRowMapper(SourceEvent::class.java)
        private val ATTENTION_ITEM_MAPPER = PostgresDataClassRowMapper(AttentionItem::class.java)
        private val ATTENTION_ITEM_SUMMARY_MAPPER = DataClassRowMapper(CurrentAttentionItemSummary::class.java)
        private val ATTENTION_ITEM_TRANSITION_MAPPER = PostgresDataClassRowMapper(AttentionItemTransition::class.java)
        private val EDITION_SUMMARY_MAPPER = PostgresDataClassRowMapper(EditionSummary::class.java)
        private val EDITION_ITEM_MAPPER = PostgresDataClassRowMapper(BriefEditionItem::class.java)
        private val UPSERT_ATTENTION = """
            INSERT INTO attention_item (
                workspace_id, season_id, event_type, source_reference, severity,
                item_status, observed_at, rule_version, last_revision, revision_gap
            ) VALUES (
                :workspaceId, :seasonId, :eventType, :sourceReference, :severity,
                :itemStatus, :observedAt, :ruleVersion, :lastRevision, :revisionGap
            )
            ON CONFLICT (workspace_id, season_id, event_type, source_reference) DO UPDATE SET
                severity = EXCLUDED.severity,
                item_status = EXCLUDED.item_status,
                observed_at = EXCLUDED.observed_at,
                rule_version = EXCLUDED.rule_version,
                last_revision = EXCLUDED.last_revision,
                revision_gap = EXCLUDED.revision_gap
        """.trimIndent()
        private val ATTENTION_ITEM_SELECT = """
            SELECT workspace_id, season_id, event_type, source_reference, severity,
                   item_status AS status, observed_at, rule_version, last_revision, revision_gap
              FROM attention_item
        """.trimIndent()
        private val SOURCE_EVENT_RECEIPT_SELECT = """
            SELECT receipt.event_id, receipt.ingestion_sequence, receipt.event_type,
                   receipt.event_version, receipt.source_severity, receipt.workspace_id, receipt.season_id,
                   receipt.source_reference, receipt.aggregate_revision, receipt.occurred_at,
                   receipt.event_state AS state, receipt.processing_outcome, receipt.received_at,
                   conflict.detected_at AS conflict_detected_at
              FROM source_event_receipt receipt
              LEFT JOIN source_event_conflict conflict ON conflict.event_id = receipt.event_id
        """.trimIndent()
    }
}

private class PostgresDataClassRowMapper<T : Any>(mappedClass: Class<T>) : DataClassRowMapper<T>(mappedClass) {
    override fun getColumnValue(result: ResultSet, index: Int, paramType: Class<*>): Any? =
        if (paramType == Instant::class.java) {
            // 구형 Timestamp를 경유하면 과거 날짜가 달라지므로 JDBC 4.2 타입으로 읽는다.
            result.getObject(index, OffsetDateTime::class.java)?.toInstant()
        } else {
            super.getColumnValue(result, index, paramType)
        }
}
