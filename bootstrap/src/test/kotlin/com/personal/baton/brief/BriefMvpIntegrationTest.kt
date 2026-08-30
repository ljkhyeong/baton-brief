package com.personal.baton.brief

import com.jayway.jsonpath.JsonPath
import com.personal.baton.brief.application.BriefPersistencePort
import com.personal.baton.brief.application.BriefService
import com.personal.baton.brief.application.IngestResult
import com.personal.baton.brief.application.IngestStatus
import com.personal.baton.brief.application.RebuildResult
import com.personal.baton.brief.domain.AttentionProjector
import com.personal.baton.brief.domain.ProjectionDecision
import com.personal.baton.brief.domain.SourceEvent
import com.personal.baton.brief.domain.SourceEventState
import com.personal.baton.brief.domain.SourceEventType
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.core.json.JsonWriteFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.util.RawValue

@Testcontainers
@SpringBootTest(
    properties = [
        "brief.event-receiver.authentication-required=true",
        "brief.event-receiver.bearer-token=$EVENT_BEARER_TOKEN",
        "brief.event-receiver.previous-bearer-token=$PREVIOUS_EVENT_BEARER_TOKEN",
    ],
)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BriefMvpIntegrationTest(
    private val mockMvc: MockMvc,
    private val jdbc: JdbcClient,
    private val persistence: BriefPersistencePort,
    private val dataSource: DataSource,
) {
    @BeforeEach
    fun clearDatabase() {
        jdbc.sql(
            """
            TRUNCATE TABLE
                brief_edition_item,
                brief_edition,
                attention_item,
                source_event_conflict,
                source_event_receipt
            RESTART IDENTITY
            """.trimIndent(),
        ).update()
    }

    @Test
    fun `health exposes aggregate status without deployment probes`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist())

        mockMvc.perform(get("/actuator"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `V2와 V7 대표 데이터를 최신 마이그레이션까지 보존한다`() {
        val schema = "brief_migration_upgrade"
        jdbc.sql("DROP SCHEMA IF EXISTS $schema CASCADE").update()
        jdbc.sql("CREATE SCHEMA $schema").update()

        try {
            val flywayConfiguration = Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema(schema)
                .schemas(schema)

            listOf(
                "2" to "representative_v2_data.sql",
                "7" to "representative_v7_data.sql",
            ).forEach { (version, fixture) ->
                flywayConfiguration.target(version).load().migrate()
                dataSource.connection.use { connection ->
                    val originalSchema = connection.schema
                    try {
                        connection.schema = schema
                        ResourceDatabasePopulator(
                            ClassPathResource("fixtures/$fixture"),
                        ).populate(connection)
                    } finally {
                        connection.schema = originalSchema
                    }
                }
            }
            flywayConfiguration.target(MigrationVersion.LATEST).load().migrate()

            assertThat(
                jdbc.sql("SELECT COUNT(*) FROM $schema.attention_item")
                    .query(Long::class.java)
                    .single(),
            ).isEqualTo(1)
            assertThat(
                jdbc.sql(
                    """
                    SELECT COUNT(*)
                      FROM information_schema.columns
                     WHERE table_schema = :schema
                       AND table_name = 'attention_item'
                       AND column_name IN ('item_id', 'projected_at', 'reason_code')
                    """.trimIndent(),
                ).param("schema", schema)
                    .query(Long::class.java)
                    .single(),
            ).isZero()
            assertThat(
                jdbc.sql(
                    """
                    SELECT pg_get_constraintdef(oid)
                      FROM pg_constraint
                     WHERE conrelid = '$schema.attention_item'::regclass
                       AND contype = 'p'
                    """.trimIndent(),
                ).query(String::class.java)
                    .single(),
            ).isEqualTo("PRIMARY KEY (workspace_id, season_id, event_type, source_reference)")
            assertThat(
                jdbc.sql(
                    """
                    SELECT COUNT(*)
                      FROM $schema.brief_edition_item
                     WHERE aggregate_revision IS NULL
                       AND revision_gap IS NULL
                    """.trimIndent(),
                ).query(Long::class.java)
                    .single(),
            ).isEqualTo(1)
            assertThatThrownBy {
                jdbc.sql(
                    "UPDATE $schema.brief_edition_item SET aggregate_revision = 1",
                ).update()
            }.isInstanceOf(DataIntegrityViolationException::class.java)
            assertThatThrownBy {
                jdbc.sql(
                    """
                    UPDATE $schema.brief_edition_item
                       SET aggregate_revision = 0,
                           revision_gap = FALSE
                    """.trimIndent(),
                ).update()
            }.isInstanceOf(DataIntegrityViolationException::class.java)
            assertThat(
                jdbc.sql(
                    """
                    SELECT COUNT(*) FROM $schema.source_event_receipt
                     WHERE (event_version = 1 OR processing_outcome = 'UNSUPPORTED')
                       AND source_severity IS NULL
                    """.trimIndent(),
                ).query(Long::class.java).single(),
            ).isEqualTo(2)
            assertThat(
                jdbc.sql(
                    """
                    SELECT source_severity FROM $schema.source_event_receipt
                     WHERE event_version = 2 AND processing_outcome = 'APPLIED'
                    """.trimIndent(),
                ).query(String::class.java).single(),
            ).isEqualTo("CRITICAL")
            assertThatThrownBy {
                jdbc.sql(
                    """
                    UPDATE $schema.source_event_receipt SET source_severity = NULL
                     WHERE event_version = 2 AND processing_outcome = 'APPLIED'
                    """.trimIndent(),
                ).update()
            }.isInstanceOf(DataIntegrityViolationException::class.java)
                .hasMessageContaining("source_event_receipt_supported_contract")
        } finally {
            jdbc.sql("DROP SCHEMA IF EXISTS $schema CASCADE").update()
        }
    }

    @Test
    fun `ingestion distinguishes duplicate conflict unsupported stale and gap`() {
        val workspaceId = "10000000-0000-0000-0000-000000000001"
        val seasonId = "20000000-0000-0000-0000-000000000001"
        val eventId = "30000000-0000-0000-0000-000000000001"
        val first = eventJson(
            eventId,
            workspaceId,
            seasonId,
            "handoff:1",
            1,
            occurredAt = "2026-08-12t18:00:00.1+09:00",
        )

        postEvent(first)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("APPLIED"))
        assertThat(
            jdbc.sql(
                "SELECT payload_fingerprint FROM source_event_receipt WHERE event_id = :eventId",
            ).param("eventId", UUID.fromString(eventId))
                .query(String::class.java)
                .single(),
        ).isEqualTo("abf432596fd0f8614fd0ba91815d8f7f736dc0b99182e6a8fa9e2bea6717c93e")

        postEvent(first)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DUPLICATE"))

        val anomalyPath =
            "/api/v1/workspaces/$workspaceId/seasons/$seasonId/event-receipts/anomalies"
        mockMvc.perform(get(anomalyPath))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receipts").isEmpty)
            .andExpect(jsonPath("$.nextBeforeIngestionSequence").value(nullValue()))

        postEvent(eventJson(eventId, workspaceId, seasonId, "handoff:1", 1, state = "RESOLVED"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value("CONFLICT"))
        postEvent(eventJson(eventId, workspaceId, seasonId, "handoff:1", 2))
            .andExpect(status().isConflict)
        assertThat(
            jdbc.sql("SELECT COUNT(*) FROM source_event_conflict")
                .query(Long::class.java)
                .single(),
        ).isEqualTo(1)

        val receiptPath = "/api/v1/events/$eventId/receipt"
        val canonicalReceipt = mockMvc.perform(get(receiptPath))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.eventId").value(eventId))
            .andExpect(jsonPath("$.eventType").value("HANDOFF_BLOCKED"))
            .andExpect(jsonPath("$.aggregateRevision").value(1))
            .andExpect(jsonPath("$.state").value("ACTIVE"))
            .andExpect(jsonPath("$.processingOutcome").value("APPLIED"))
            .andExpect(jsonPath("$.conflictDetectedAt").isNotEmpty)
            .andReturn()
            .response
            .contentAsString
        assertThat(JsonPath.read<Map<String, Any?>>(canonicalReceipt, "$").keys)
            .containsExactlyInAnyOrder(
                "eventId",
                "ingestionSequence",
                "eventType",
                "eventVersion",
                "sourceSeverity",
                "workspaceId",
                "seasonId",
                "sourceReference",
                "aggregateRevision",
                "occurredAt",
                "state",
                "processingOutcome",
                "receivedAt",
                "conflictDetectedAt",
            )

        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000002",
                workspaceId,
                seasonId,
                "handoff:1",
                1,
                occurredAt = "2026-08-12T09:00:00.123456789z",
            ),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("STALE"))

        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000003",
                workspaceId,
                seasonId,
                "handoff:1",
                3,
            ),
        ).andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("APPLIED_WITH_GAP"))
            .andExpect(jsonPath("$.item.aggregateRevision").value(3))
            .andExpect(jsonPath("$.item.revisionGap").value(true))

        val unsupported = eventJson(
            "30000000-0000-0000-0000-000000000004",
            workspaceId,
            seasonId,
            "handoff:2",
            1,
            eventVersion = 2,
        )
        postEvent(unsupported)
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.status").value("UNSUPPORTED"))
        postEvent(unsupported)
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.status").value("UNSUPPORTED"))
        assertThat(
            jdbc.sql(
                "SELECT payload_fingerprint FROM source_event_receipt WHERE event_id = :eventId",
            ).param("eventId", UUID.fromString("30000000-0000-0000-0000-000000000004"))
                .query(String::class.java)
                .single(),
        ).isEqualTo("ac86b6dc7a11b60105bd556485eb07f11d9d451c08e3d88f2b3de23b65b31ec9")
        mockMvc.perform(get("/api/v1/events/30000000-0000-0000-0000-000000000004/receipt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.eventVersion").value(2))
            .andExpect(jsonPath("$.processingOutcome").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.conflictDetectedAt").value(nullValue()))

        mockMvc.perform(get(anomalyPath).param("limit", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receipts[0].ingestionSequence").value(4))
            .andExpect(jsonPath("$.receipts[0].processingOutcome").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.receipts[1].ingestionSequence").value(3))
            .andExpect(jsonPath("$.receipts[1].processingOutcome").value("APPLIED_WITH_GAP"))
            .andExpect(jsonPath("$.nextBeforeIngestionSequence").value(3))

        mockMvc.perform(
            get(anomalyPath)
                .param("beforeIngestionSequence", "3")
                .param("limit", "2"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receipts[0].ingestionSequence").value(2))
            .andExpect(jsonPath("$.receipts[0].processingOutcome").value("STALE"))
            .andExpect(jsonPath("$.receipts[1].ingestionSequence").value(1))
            .andExpect(jsonPath("$.receipts[1].eventId").value(eventId))
            .andExpect(jsonPath("$.receipts[1].processingOutcome").value("APPLIED"))
            .andExpect(jsonPath("$.receipts[1].conflictDetectedAt").isNotEmpty)
            .andExpect(jsonPath("$.nextBeforeIngestionSequence").value(nullValue()))

        mockMvc.perform(
            get(
                "/api/v1/workspaces/$workspaceId/seasons/20000000-0000-0000-0000-000000000099/" +
                    "event-receipts/anomalies",
            ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receipts").isEmpty)

        mockMvc.perform(
            get(
                "/api/v1/workspaces/10000000-0000-0000-0000-000000000099/seasons/$seasonId/" +
                    "event-receipts/anomalies",
            ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receipts").isEmpty)

        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000006",
                workspaceId,
                seasonId,
                "decision:current",
                1,
                type = "DECISION_FOLLOW_UP_OVERDUE",
            ),
        )
        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000007",
                workspaceId,
                seasonId,
                "routine:current",
                1,
                type = "ROUTINE_MISSED",
            ),
        )

        mockMvc.perform(post("/api/v1/projections/rebuild"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receiptCount").value(5))
            .andExpect(jsonPath("$.itemCount").value(3))
        val rebuiltReceipt = mockMvc.perform(get(receiptPath))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        assertThat(rebuiltReceipt).isEqualTo(canonicalReceipt)

        val currentAttentionPath =
            "/api/v1/workspaces/$workspaceId/seasons/$seasonId/attention-items/current"
        val currentAttention = mockMvc.perform(
            get(currentAttentionPath)
                .param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", "handoff:1"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.aggregateRevision").value(3))
            .andExpect(jsonPath("$.revisionGap").value(true))
            .andReturn()
        val currentAttentionEtag = checkNotNull(currentAttention.response.getHeader(HttpHeaders.ETAG))
        mockMvc.perform(
            get(currentAttentionPath)
                .param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", "handoff:1")
                .header(HttpHeaders.IF_NONE_MATCH, currentAttentionEtag),
        ).andExpect(status().isNotModified)

        val attentionItemsPath =
            "/api/v1/workspaces/$workspaceId/seasons/$seasonId/attention-items"
        mockMvc.perform(get(attentionItemsPath).param("limit", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].reasonCode").value("DECISION_FOLLOW_UP_OVERDUE"))
            .andExpect(jsonPath("$.items[0].sourceReference").value("decision:current"))
            .andExpect(jsonPath("$.items[1].reasonCode").value("HANDOFF_BLOCKED"))
            .andExpect(jsonPath("$.items[1].sourceReference").value("handoff:1"))
            .andExpect(jsonPath("$.nextCursor.eventType").value("HANDOFF_BLOCKED"))
            .andExpect(jsonPath("$.nextCursor.sourceReference").value("handoff:1"))

        mockMvc.perform(
            get(attentionItemsPath)
                .param("afterEventType", "HANDOFF_BLOCKED")
                .param("afterSourceReference", "handoff:1")
                .param("limit", "2"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].reasonCode").value("ROUTINE_MISSED"))
            .andExpect(jsonPath("$.items[0].sourceReference").value("routine:current"))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))

        mockMvc.perform(
            get(
                "/api/v1/workspaces/10000000-0000-0000-0000-000000000099/seasons/$seasonId/" +
                    "attention-items",
            ),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)

        mockMvc.perform(
            get(attentionItemsPath)
                .param("afterEventType", "HANDOFF_BLOCKED"),
        ).andExpect(status().isBadRequest)

        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000005",
                workspaceId,
                seasonId,
                "handoff:1",
                4,
                state = "RESOLVED",
            ),
        )
        mockMvc.perform(
            get(currentAttentionPath)
                .param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", "handoff:1")
                .header(HttpHeaders.IF_NONE_MATCH, currentAttentionEtag),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.aggregateRevision").value(4))
            .andExpect { result ->
                assertThat(checkNotNull(result.response.getHeader(HttpHeaders.ETAG)))
                    .isNotEqualTo(currentAttentionEtag)
            }

        mockMvc.perform(get(attentionItemsPath))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$.items[*].sourceReference")
                    .value(contains("decision:current", "routine:current")),
            )

        mockMvc.perform(get(attentionItemsPath).param("status", "RESOLVED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].sourceReference").value("handoff:1"))
            .andExpect(jsonPath("$.items[0].status").value("RESOLVED"))

        val transitionPath = "$attentionItemsPath/transitions"
        mockMvc.perform(
            get(transitionPath)
                .param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", "handoff:1")
                .param("limit", "2"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.transitions.length()").value(2))
            .andExpect(
                jsonPath("$.transitions[0].eventId")
                    .value("30000000-0000-0000-0000-000000000005"),
            )
            .andExpect(jsonPath("$.transitions[0].aggregateRevision").value(4))
            .andExpect(jsonPath("$.transitions[0].state").value("RESOLVED"))
            .andExpect(jsonPath("$.transitions[0].detectedRevisionGap").value(false))
            .andExpect(jsonPath("$.transitions[1].aggregateRevision").value(3))
            .andExpect(jsonPath("$.transitions[1].state").value("ACTIVE"))
            .andExpect(jsonPath("$.transitions[1].detectedRevisionGap").value(true))
            .andExpect(jsonPath("$.nextBeforeAggregateRevision").value(3))

        mockMvc.perform(
            get(transitionPath)
                .param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", "handoff:1")
                .param("beforeAggregateRevision", "3")
                .param("limit", "2"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.transitions.length()").value(1))
            .andExpect(jsonPath("$.transitions[0].aggregateRevision").value(1))
            .andExpect(jsonPath("$.nextBeforeAggregateRevision").value(nullValue()))

        mockMvc.perform(
            get(
                "/api/v1/workspaces/10000000-0000-0000-0000-000000000099/seasons/$seasonId/" +
                    "attention-items/transitions",
            ).param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", "handoff:1"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.transitions").isEmpty)

        mockMvc.perform(
            get(
                "/api/v1/workspaces/10000000-0000-0000-0000-000000000099/seasons/$seasonId/" +
                    "attention-items/current",
            ).param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", "handoff:1"),
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            get(currentAttentionPath)
                .param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", " "),
        ).andExpect(status().isBadRequest)

        val missingReceiptPath = "/api/v1/events/30000000-0000-0000-0000-000000000099/receipt"
        mockMvc.perform(get(missingReceiptPath))
            .andExpect(status().isNotFound)

        val receivedAt = Instant.parse("2026-08-12T09:00:01Z")
        val conflictRequestAt = Instant.parse("2026-08-12T09:00:02Z")
        val conflictDetectedAt = Instant.parse("2026-08-12T09:00:03Z")
        val sequentialClock = Mockito.mock(Clock::class.java)
        Mockito.`when`(sequentialClock.instant())
            .thenReturn(receivedAt, conflictRequestAt, conflictDetectedAt)
        val conflictEventId = UUID.fromString("30000000-0000-0000-0000-000000000098")
        val conflictEvent = SourceEvent(
            eventId = conflictEventId,
            eventType = SourceEventType.HANDOFF_BLOCKED,
            eventVersion = 1,
            workspaceId = UUID.fromString(workspaceId),
            seasonId = UUID.fromString(seasonId),
            sourceReference = "handoff:conflict-time",
            aggregateRevision = 1,
            occurredAt = receivedAt,
            state = SourceEventState.ACTIVE,
        )
        val service = BriefService(persistence, sequentialClock)
        assertThat(service.ingest(conflictEvent).status).isEqualTo(IngestStatus.APPLIED)
        assertThat(service.ingest(conflictEvent.copy(state = SourceEventState.RESOLVED)).status)
            .isEqualTo(IngestStatus.CONFLICT)
        assertThat(
            jdbc.sql("SELECT received_at FROM source_event_receipt WHERE event_id = :eventId")
                .param("eventId", conflictEventId)
                .query(OffsetDateTime::class.java)
                .single()
                .toInstant(),
        ).isEqualTo(receivedAt)
        assertThat(
            jdbc.sql("SELECT detected_at FROM source_event_conflict WHERE event_id = :eventId")
                .param("eventId", conflictEventId)
                .query(OffsetDateTime::class.java)
                .single()
                .toInstant(),
        ).isEqualTo(conflictDetectedAt)
    }

    @Test
    fun `consumes BATON continuity event v2 and reproduces it after rebuild`() {
        val workspaceId = "10000000-0000-0000-0000-000000000008"
        val seasonId = "20000000-0000-0000-0000-000000000008"
        val signals = listOf(
            Triple("role-unassigned.active-r1-critical.json", "ROLE_UNASSIGNED", "HIGH"),
            Triple("role-successor-missing.active-r1-warning.json", "ROLE_SUCCESSOR_MISSING", "MEDIUM"),
            Triple(
                "role-preparation-incomplete.active-r1-warning.json",
                "ROLE_PREPARATION_INCOMPLETE",
                "MEDIUM",
            ),
            Triple(
                "routine-repeatedly-overdue.active-r1-critical.json",
                "ROUTINE_REPEATEDLY_OVERDUE",
                "HIGH",
            ),
            Triple("handoff-incomplete.active-r1-warning.json", "HANDOFF_INCOMPLETE", "MEDIUM"),
        )

        signals.forEach { (fileName, type, severity) ->
            postEvent(contractEvent(fileName))
                .andExpect(jsonPath("$.item.reasonCode").value(type))
                .andExpect(jsonPath("$.item.severity").value(severity))
        }

        val firstEventId = "70000000-0000-0000-0000-000000000001"
        val firstReference = "baton-continuity:60000000-0000-0000-0000-000000000001"
        assertThat(
            jdbc.sql(
                "SELECT payload_fingerprint FROM source_event_receipt WHERE event_id = :eventId",
            ).param("eventId", UUID.fromString(firstEventId))
                .query(String::class.java)
                .single(),
        ).isEqualTo("69bf5f24726545fd73fba11ae22261f7ec1c7d9279f3e12543c03061344b5c55")
        val conflictingEvent = JSON.readTree(
            contractEvent("role-unassigned.active-r1-critical.json"),
        ) as ObjectNode
        postEvent(conflictingEvent.put("sourceSeverity", "WARNING"))
            .andExpect(status().isConflict)

        mockMvc.perform(get("/api/v1/events/$firstEventId/receipt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceSeverity").value("CRITICAL"))

        postEvent(contractEvent("role-unassigned.active-r2-warning.json"))
            .andExpect(jsonPath("$.item.severity").value("MEDIUM"))

        postEvent(contractEvent("role-unassigned.resolved-r3-warning.json"))

        val currentPath = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/attention-items/current"
        val beforeRebuild = mockMvc.perform(
            get(currentPath)
                .param("eventType", "ROLE_UNASSIGNED")
                .param("sourceReference", firstReference),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.aggregateRevision").value(3))
            .andReturn()

        mockMvc.perform(post("/api/v1/projections/rebuild"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receiptCount").value(7))
            .andExpect(jsonPath("$.itemCount").value(5))

        val afterRebuild = mockMvc.perform(
            get(currentPath)
                .param("eventType", "ROLE_UNASSIGNED")
                .param("sourceReference", firstReference),
        ).andExpect(status().isOk)
            .andReturn()
        assertThat(afterRebuild.response.contentAsString)
            .isEqualTo(beforeRebuild.response.contentAsString)
    }

    @Test
    fun `edition is idempotent immutable generated and reproducible after rebuild`() {
        val workspaceId = "10000000-0000-0000-0000-000000000002"
        val seasonId = "20000000-0000-0000-0000-000000000002"
        val blockedReference = "handoff:weekly"

        postEvent(
            eventJson(
                "40000000-0000-0000-0000-000000000001",
                workspaceId,
                seasonId,
                blockedReference,
                1,
                occurredAt = "2026-08-09T15:00:00Z",
            ),
        )
        postEvent(
            eventJson(
                "40000000-0000-0000-0000-000000000002",
                workspaceId,
                seasonId,
                "routine:weekly",
                1,
                type = "ROUTINE_MISSED",
                occurredAt = "2026-08-16T14:59:59.999999999Z",
            ),
        ).andExpect(jsonPath("$.item.observedAt").value("2026-08-16T14:59:59.999999Z"))
        postEvent(
            eventJson(
                "40000000-0000-0000-0000-000000000003",
                workspaceId,
                seasonId,
                "decision:next-week",
                1,
                type = "DECISION_FOLLOW_UP_OVERDUE",
                occurredAt = "2026-08-16T15:00:00Z",
            ),
        )

        val generationPath = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/editions"
        val editionRequest = """{"weekStart":"2026-08-10","zoneId":"Asia/Seoul"}"""
        val firstResult = postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(1))
            .andExpect(jsonPath("$.sourceCursor").value(3))
            .andExpect(jsonPath("$.items[0].severity").value("HIGH"))
            .andExpect(jsonPath("$.items[1].severity").value("MEDIUM"))
            .andReturn()
        val firstEditionId = JsonPath.read<String>(firstResult.response.contentAsString, "$.editionId")
        val firstEditionEtag = checkNotNull(firstResult.response.getHeader(HttpHeaders.ETAG))
        assertThat(
            jdbc.sql("SELECT state_fingerprint FROM brief_edition WHERE edition_id = :editionId")
                .param("editionId", UUID.fromString(firstEditionId))
                .query(String::class.java)
                .single(),
        ).isEqualTo("df93d4d5a31fdb77c50cc87eef92ce89899a4793a754ea13e96d664b9352b1b7")
        assertThat(firstResult.response.getHeader("Location"))
            .isEqualTo("/api/v1/editions/$firstEditionId")

        postEvent(
            eventJson(
                "40000000-0000-0000-0000-000000000004",
                workspaceId,
                seasonId,
                "decision:temporary",
                1,
                type = "DECISION_FOLLOW_UP_OVERDUE",
                occurredAt = "2026-08-13T09:00:00Z",
            ),
        )

        postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(2))
            .andExpect(jsonPath("$.sourceCursor").value(4))

        postEvent(
            eventJson(
                "40000000-0000-0000-0000-000000000005",
                workspaceId,
                seasonId,
                "decision:temporary",
                2,
                state = "RESOLVED",
                type = "DECISION_FOLLOW_UP_OVERDUE",
                occurredAt = "2026-08-13T09:00:00Z",
            ),
        )

        val recurringStateResult = postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(3))
            .andExpect(jsonPath("$.sourceCursor").value(5))
            .andReturn()
        val recurringStateEditionId = JsonPath.read<String>(
            recurringStateResult.response.contentAsString,
            "$.editionId",
        )
        mockMvc.perform(get(generationPath).param("limit", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.editions.length()").value(2))
            .andExpect(jsonPath("$.editions[0].editionId").value(recurringStateEditionId))
            .andExpect(jsonPath("$.editions[0].generation").value(3))
            .andExpect(jsonPath("$.editions[0].itemCount").value(2))
            .andExpect(jsonPath("$.editions[1].generation").value(2))
            .andExpect(jsonPath("$.editions[1].itemCount").value(3))
            .andExpect(jsonPath("$.nextBeforeGeneration").value(2))

        mockMvc.perform(
            get(generationPath)
                .param("beforeGeneration", "2")
                .param("limit", "2"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.editions.length()").value(1))
            .andExpect(jsonPath("$.editions[0].editionId").value(firstEditionId))
            .andExpect(jsonPath("$.editions[0].generation").value(1))
            .andExpect(jsonPath("$.editions[0].itemCount").value(2))
            .andExpect(jsonPath("$.nextBeforeGeneration").value(nullValue()))

        val emptyScopePath =
            "/api/v1/workspaces/$workspaceId/seasons/20000000-0000-0000-0000-000000000099/editions"
        mockMvc.perform(get(emptyScopePath))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.editions").isEmpty)

        val attentionCountBeforeFailedRebuild = jdbc.sql("SELECT COUNT(*) FROM attention_item")
            .query(Long::class.java)
            .single()
        assertThatThrownBy {
            persistence.rebuild { event, current ->
                when (val decision = AttentionProjector.project(event, current)) {
                    is ProjectionDecision.Applied -> if (event.sourceReference == "routine:weekly") {
                        decision.copy(item = decision.item.copy(ruleVersion = 0))
                    } else {
                        decision
                    }

                    ProjectionDecision.Stale -> decision
                }
            }
        }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(
            jdbc.sql("SELECT COUNT(*) FROM attention_item").query(Long::class.java).single(),
        ).isEqualTo(attentionCountBeforeFailedRebuild)

        postEdition(generationPath, editionRequest)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.editionId").value(recurringStateEditionId))

        mockMvc.perform(post("/api/v1/projections/rebuild"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receiptCount").value(5))
            .andExpect(jsonPath("$.itemCount").value(4))

        postEdition(generationPath, editionRequest)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.editionId").value(recurringStateEditionId))

        val rebuiltSnapshot = mockMvc.perform(get("/api/v1/editions/$firstEditionId"))
            .andExpect(status().isOk)
            .andReturn()
        assertThat(rebuiltSnapshot.response.contentAsString)
            .isEqualTo(firstResult.response.contentAsString)
        mockMvc.perform(
            get("/api/v1/editions/$firstEditionId")
                .header(HttpHeaders.IF_NONE_MATCH, firstEditionEtag),
        ).andExpect(status().isNotModified)

        val nextWeekRequest = """{"weekStart":"2026-08-17","zoneId":"Asia/Seoul"}"""
        postEdition(generationPath, nextWeekRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(4))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].sourceReference").value("decision:next-week"))

        val previousLatest = mockMvc.perform(get("$generationPath/latest"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.generation").value(4))
            .andReturn()
        val previousLatestEtag = checkNotNull(previousLatest.response.getHeader(HttpHeaders.ETAG))

        postEvent(
            eventJson(
                "40000000-0000-0000-0000-000000000006",
                workspaceId,
                seasonId,
                "routine:weekly",
                2,
                type = "ROUTINE_MISSED",
                state = "RESOLVED",
                occurredAt = "2026-08-14T09:00:00Z",
            ),
        )

        postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(5))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].sourceReference").value(blockedReference))

        mockMvc.perform(
            get("$generationPath/latest")
                .header(HttpHeaders.IF_NONE_MATCH, previousLatestEtag),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.generation").value(5))

        val weeklyLatestPath = "$generationPath/weekly/latest"
        val nextWeekLatest = mockMvc.perform(
            get(weeklyLatestPath)
                .param("weekStart", "2026-08-17")
                .param("zoneId", "Asia/Seoul"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.generation").value(4))
            .andReturn()
        mockMvc.perform(
            get(weeklyLatestPath)
                .param("weekStart", "2026-08-17")
                .param("zoneId", "Asia/Seoul")
                .header(
                    HttpHeaders.IF_NONE_MATCH,
                    checkNotNull(nextWeekLatest.response.getHeader(HttpHeaders.ETAG)),
                ),
        ).andExpect(status().isNotModified)

        mockMvc.perform(
            get(weeklyLatestPath)
                .param("weekStart", "2026-08-10")
                .param("zoneId", "Asia/Seoul"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.generation").value(5))

        mockMvc.perform(
            get(
                "/api/v1/workspaces/10000000-0000-0000-0000-000000000099/seasons/$seasonId" +
                    "/editions/weekly/latest",
            ).param("weekStart", "2026-08-10")
                .param("zoneId", "Asia/Seoul"),
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            get(
                "/api/v1/workspaces/$workspaceId/seasons/20000000-0000-0000-0000-000000000099" +
                    "/editions/weekly/latest",
            ).param("weekStart", "2026-08-10")
                .param("zoneId", "Asia/Seoul"),
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            get(weeklyLatestPath)
                .param("weekStart", "2026-08-17")
                .param("zoneId", "UTC"),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `edition changes compare immutable snapshots and reject invalid scopes`() {
        val workspaceId = "10000000-0000-0000-0000-000000000005"
        val seasonId = "20000000-0000-0000-0000-000000000005"
        val removedReference = "handoff:removed"
        val changedReference = "routine:changed"

        postEvent(
            eventJson(
                "60000000-0000-0000-0000-000000000001",
                workspaceId,
                seasonId,
                removedReference,
                1,
                occurredAt = "2026-08-11T01:00:00Z",
            ),
        )
        postEvent(
            eventJson(
                "60000000-0000-0000-0000-000000000002",
                workspaceId,
                seasonId,
                changedReference,
                1,
                type = "ROUTINE_MISSED",
                occurredAt = "2026-08-11T02:00:00Z",
            ),
        )

        val generationPath = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/editions"
        val editionRequest = """{"weekStart":"2026-08-10","zoneId":"Asia/Seoul"}"""
        val baseEdition = postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(1))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn()
        val baseEditionId = JsonPath.read<String>(baseEdition.response.contentAsString, "$.editionId")

        postEvent(
            eventJson(
                "60000000-0000-0000-0000-000000000004",
                workspaceId,
                seasonId,
                changedReference,
                3,
                type = "ROUTINE_MISSED",
                occurredAt = "2026-08-11T02:00:00Z",
            ),
        )

        val revisionEvidenceEdition = postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn()
        val revisionEvidenceEditionId = JsonPath.read<String>(
            revisionEvidenceEdition.response.contentAsString,
            "$.editionId",
        )
        postEdition(generationPath, editionRequest)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.editionId").value(revisionEvidenceEditionId))

        postEvent(
            eventJson(
                "60000000-0000-0000-0000-000000000003",
                workspaceId,
                seasonId,
                removedReference,
                2,
                state = "RESOLVED",
                occurredAt = "2026-08-12T01:00:00Z",
            ),
        )
        postEvent(
            eventJson(
                "60000000-0000-0000-0000-000000000005",
                workspaceId,
                seasonId,
                "decision:added",
                1,
                type = "DECISION_FOLLOW_UP_OVERDUE",
                occurredAt = "2026-08-14T03:00:00Z",
            ),
        )
        postEvent(
            eventJson(
                "60000000-0000-0000-0000-000000000006",
                workspaceId,
                seasonId,
                "handoff:added",
                1,
                occurredAt = "2026-08-15T03:00:00Z",
            ),
        )

        val targetEdition = postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(3))
            .andExpect(jsonPath("$.items.length()").value(3))
            .andReturn()
        val targetEditionId = JsonPath.read<String>(
            targetEdition.response.contentAsString,
            "$.editionId",
        )
        val changesPath = "/api/v1/editions/$targetEditionId/changes"
        val changes = mockMvc.perform(get(changesPath).param("fromEditionId", baseEditionId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.from.editionId").value(baseEditionId))
            .andExpect(jsonPath("$.to.editionId").value(targetEditionId))
            .andExpect(jsonPath("$.added.length()").value(2))
            .andExpect(jsonPath("$.added[0].sourceReference").value("handoff:added"))
            .andExpect(jsonPath("$.added[1].sourceReference").value("decision:added"))
            .andExpect(jsonPath("$.removed.length()").value(1))
            .andExpect(jsonPath("$.removed[0].sourceReference").value(removedReference))
            .andExpect(jsonPath("$.changed.length()").value(1))
            .andExpect(jsonPath("$.changed[0].before.sourceReference").value(changedReference))
            .andExpect(jsonPath("$.changed[0].before.aggregateRevision").value(1))
            .andExpect(jsonPath("$.changed[0].before.revisionGap").value(false))
            .andExpect(jsonPath("$.changed[0].after.sourceReference").value(changedReference))
            .andExpect(jsonPath("$.changed[0].after.aggregateRevision").value(3))
            .andExpect(jsonPath("$.changed[0].after.revisionGap").value(true))
            .andReturn()

        mockMvc.perform(
            get("/api/v1/editions/$baseEditionId/changes")
                .param("fromEditionId", targetEditionId),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.from.editionId").value(targetEditionId))
            .andExpect(jsonPath("$.to.editionId").value(baseEditionId))
            .andExpect(jsonPath("$.added.length()").value(1))
            .andExpect(jsonPath("$.added[0].sourceReference").value(removedReference))
            .andExpect(jsonPath("$.removed.length()").value(2))
            .andExpect(jsonPath("$.removed[0].sourceReference").value("handoff:added"))
            .andExpect(jsonPath("$.removed[1].sourceReference").value("decision:added"))
            .andExpect(jsonPath("$.changed.length()").value(1))
            .andExpect(jsonPath("$.changed[0].before.aggregateRevision").value(3))
            .andExpect(jsonPath("$.changed[0].after.aggregateRevision").value(1))

        postEvent(
            eventJson(
                "60000000-0000-0000-0000-000000000007",
                workspaceId,
                seasonId,
                "handoff:after-target",
                1,
                occurredAt = "2026-08-15T04:00:00Z",
            ),
        ).andExpect(status().isAccepted)
        mockMvc.perform(post("/api/v1/projections/rebuild"))
            .andExpect(status().isOk)
        val rebuiltChanges = mockMvc.perform(
            get(changesPath).param("fromEditionId", baseEditionId),
        ).andExpect(status().isOk)
            .andReturn()
        assertThat(rebuiltChanges.response.contentAsString)
            .isEqualTo(changes.response.contentAsString)

        val otherWorkspaceId = "10000000-0000-0000-0000-000000000099"
        val otherGenerationPath =
            "/api/v1/workspaces/$otherWorkspaceId/seasons/$seasonId/editions"
        val otherEdition = postEdition(otherGenerationPath, editionRequest)
            .andExpect(status().isCreated)
            .andReturn()
        val otherEditionId = JsonPath.read<String>(
            otherEdition.response.contentAsString,
            "$.editionId",
        )

        mockMvc.perform(get(changesPath).param("fromEditionId", otherEditionId))
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            get(changesPath)
                .param("fromEditionId", "50000000-0000-0000-0000-000000000099"),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `rejects representations outside the HTTP contract and reports missing editions`() {
        val workspaceId = "10000000-0000-0000-0000-000000000003"
        val seasonId = "20000000-0000-0000-0000-000000000003"
        val eventId = "30000000-0000-0000-0000-000000000010"
        val unauthorizedBody = JSON.writeValueAsString(
            eventJson(eventId, workspaceId, seasonId, "unauthorized", 1),
        )

        mockMvc.perform(
            post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(unauthorizedBody),
        ).andExpect(status().isUnauthorized)
            .andExpect(
                header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")),
            )

        mockMvc.perform(
            post("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token-000000000000000000000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(unauthorizedBody),
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $PREVIOUS_EVENT_BEARER_TOKEN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JSON.writeValueAsString(
                        eventJson(
                            "30000000-0000-0000-0000-000000000011",
                            workspaceId,
                            seasonId,
                            "rotation-overlap",
                            1,
                        ),
                    ),
                ),
        ).andExpect(status().isAccepted)

        postEvent(eventJson(eventId, workspaceId, seasonId, "invalid", 1, eventVersion = 0))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").isNotEmpty)
            .andExpect(jsonPath("$.instance").value("/api/v1/events"))

        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000012",
                workspaceId,
                seasonId,
                "future-version",
                1,
                eventVersion = 3,
            ),
        ).andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.status").value("UNSUPPORTED"))

        val numericInstant = eventJson(eventId, workspaceId, seasonId, "invalid", 1)
            .put("occurredAt", 1786525200)
        postEvent(numericInstant).andExpect(status().isBadRequest)

        val fractionalRevision = eventJson(eventId, workspaceId, seasonId, "invalid", 1)
            .put("aggregateRevision", 1.5)
        postEvent(fractionalRevision).andExpect(status().isBadRequest)

        val decimalVersion = eventJson(
            eventId,
            workspaceId,
            seasonId,
            "invalid",
            1,
            type = "ROLE_UNASSIGNED",
            eventVersion = 2,
            sourceSeverity = "CRITICAL",
        ).putRawValue("eventVersion", RawValue("2.0"))
        postEvent(decimalVersion).andExpect(status().isBadRequest)

        val decimalRevision = eventJson(eventId, workspaceId, seasonId, "invalid", 1)
            .putRawValue("aggregateRevision", RawValue("1.0"))
        postEvent(decimalRevision).andExpect(status().isBadRequest)

        val overflowingRevision = eventJson(eventId, workspaceId, seasonId, "invalid", 1)
            .put("aggregateRevision", "9223372036854775808".toBigInteger())
        postEvent(overflowingRevision).andExpect(status().isBadRequest)

        val unknownField = eventJson(eventId, workspaceId, seasonId, "invalid", 1)
            .put("unexpected", true)
        postEvent(unknownField).andExpect(status().isBadRequest)

        listOf(
            "AAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAA==",
        ).forEach { nonCanonicalEventId ->
            postEvent(
                eventJson(nonCanonicalEventId, workspaceId, seasonId, "invalid", 1),
            ).andExpect(status().isBadRequest)
        }

        listOf("\u0000", "valid\u0000suffix").forEach { sourceReference ->
            postEvent(
                eventJson(eventId, workspaceId, seasonId, sourceReference, 1),
            ).andExpect(status().isBadRequest)
        }

        listOf(
            "2026-08-12T24:00:00Z",
            "2026-08-12T23:59:60Z",
        ).forEach { occurredAt ->
            postEvent(
                eventJson(
                    eventId,
                    workspaceId,
                    seasonId,
                    "invalid",
                    1,
                    occurredAt = occurredAt,
                ),
            ).andExpect(status().isBadRequest)
        }

        postEvent(
            eventJson(
                eventId = eventId,
                workspaceId = workspaceId,
                seasonId = seasonId,
                sourceReference = "invalid",
                revision = 1,
                type = "ROLE_UNASSIGNED",
                eventVersion = 2,
            ),
        ).andExpect(status().isBadRequest)

        postEvent(
            eventJson(
                eventId = "30000000-0000-0000-0000-000000000013",
                workspaceId = workspaceId,
                seasonId = seasonId,
                sourceReference = "😀".repeat(128),
                revision = 1,
                type = "ROLE_UNASSIGNED",
                eventVersion = 2,
                sourceSeverity = "CRITICAL",
            ),
        ).andExpect(status().isAccepted)

        postEvent(
            eventJson(
                eventId = "30000000-0000-0000-0000-000000000014",
                workspaceId = workspaceId,
                seasonId = seasonId,
                sourceReference = "😀".repeat(129),
                revision = 1,
                type = "ROLE_UNASSIGNED",
                eventVersion = 2,
                sourceSeverity = "CRITICAL",
            ),
        ).andExpect(status().isBadRequest)

        val path = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/editions"
        postEdition(path, """{"weekStart":[2026,8,10],"zoneId":"Asia/Seoul"}""")
            .andExpect(status().isBadRequest)

        val weeklyLatestPath = "$path/weekly/latest"
        mockMvc.perform(
            get(weeklyLatestPath)
                .param("weekStart", "2026-08-11")
                .param("zoneId", "Asia/Seoul"),
        ).andExpect(status().isBadRequest)
        mockMvc.perform(
            get(weeklyLatestPath)
                .param("weekStart", "2026-08-10")
                .param("zoneId", "+09:00"),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(get(path).param("beforeGeneration", "0"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get(path).param("limit", "0"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get(path).param("limit", "101"))
            .andExpect(status().isBadRequest)

        mockMvc.perform(get("$path/latest")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/editions/50000000-0000-0000-0000-000000000001"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").isNotEmpty)
            .andExpect(
                jsonPath("$.instance")
                    .value("/api/v1/editions/50000000-0000-0000-0000-000000000001"),
            )
    }

    @Test
    fun `주간 날짜는 네 자리 연도만 받고 양 끝 주간을 저장 조회한다`() {
        val workspaceId = "10000000-0000-0000-0000-000000000010"
        val seasonId = "20000000-0000-0000-0000-000000000010"
        val path = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/editions"

        listOf("0000-01-03", "9999-12-27").forEach { weekStart ->
            val request = JSON.writeValueAsString(
                JSON.createObjectNode().put("weekStart", weekStart).put("zoneId", "UTC"),
            )
            val editionId = JsonPath.read<String>(
                postEdition(path, request)
                    .andExpect(status().isCreated)
                    .andReturn().response.contentAsString,
                "$.editionId",
            )
            mockMvc.perform(
                get("$path/weekly/latest")
                    .param("weekStart", weekStart)
                    .param("zoneId", "UTC"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.editionId").value(editionId))
                .andExpect(jsonPath("$.weekStart").value(weekStart))
        }

        listOf("-5000-01-06", "+10000-01-03", "+6000000-01-03", "+999999999-12-27")
            .forEach { weekStart ->
                val request = JSON.writeValueAsString(
                    JSON.createObjectNode().put("weekStart", weekStart).put("zoneId", "UTC"),
                )
                postEdition(path, request)
                    .andExpect(status().isBadRequest)
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                mockMvc.perform(
                    get("$path/weekly/latest")
                        .param("weekStart", weekStart)
                        .param("zoneId", "UTC"),
                ).andExpect(status().isBadRequest)
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            }
    }

    @Test
    fun `원본 참조의 정상 문자는 보존하고 저장할 수 없는 문자는 거부한다`() {
        val workspaceId = "10000000-0000-0000-0000-000000000009"
        val seasonId = "20000000-0000-0000-0000-000000000009"
        val path = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/attention-items"
        val sourceReference = "handoff:\"quoted\"\\path"
        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000091",
                workspaceId,
                seasonId,
                sourceReference,
                1,
            ),
        ).andExpect(status().isAccepted)
        mockMvc.perform(
            get("$path/current")
                .param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", sourceReference),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceReference").value(sourceReference))

        val validEvent = eventJson(
            "30000000-0000-0000-0000-000000000092",
            workspaceId,
            seasonId,
            "review:?",
            1,
            type = "ROLE_UNASSIGNED",
            eventVersion = 2,
            sourceSeverity = "CRITICAL",
        )
        val escapedJson = JSON.writer().with(JsonWriteFeature.ESCAPE_NON_ASCII)
        listOf("review:\uD800", "review:\uDC00").forEach { invalidReference ->
            postEvent(
                escapedJson.writeValueAsString(validEvent.deepCopy().put("sourceReference", invalidReference)),
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        }
        postEvent(validEvent).andExpect(status().isAccepted)

        listOf(
            get("$path/current")
                .param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", "invalid\u0000reference"),
            get("$path/transitions")
                .param("eventType", "HANDOFF_BLOCKED")
                .param("sourceReference", "invalid\u0000reference"),
            get(path)
                .param("afterEventType", "HANDOFF_BLOCKED")
                .param("afterSourceReference", "invalid\u0000reference"),
        ).forEach { request ->
            mockMvc.perform(request)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    @Test
    fun `수신한 NBSP 원본 참조로 다음 페이지를 조회한다`() {
        val workspaceId = "10000000-0000-0000-0000-000000000011"
        val seasonId = "20000000-0000-0000-0000-000000000011"
        val path = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/attention-items"
        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000111",
                workspaceId,
                seasonId,
                "\u00a0",
                1,
            ),
        ).andExpect(status().isAccepted)
        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000112",
                workspaceId,
                seasonId,
                "next-item",
                1,
                type = "ROUTINE_MISSED",
            ),
        ).andExpect(status().isAccepted)

        val firstPage = mockMvc.perform(get(path).param("limit", "1"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsByteArray
        val cursor = JSON.readTree(firstPage).path("nextCursor")
        assertThat(cursor.path("sourceReference").stringValue()).isEqualTo("\u00a0")
        mockMvc.perform(
            get(path)
                .param("limit", "1")
                .param("afterEventType", cursor.path("eventType").stringValue())
                .param("afterSourceReference", cursor.path("sourceReference").stringValue()),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].sourceReference").value("next-item"))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `serializes concurrent duplicate ingestion edition generation and rebuild`() {
        val workspaceId = "10000000-0000-0000-0000-000000000004"
        val seasonId = "20000000-0000-0000-0000-000000000004"
        val event = eventJson(
            "30000000-0000-0000-0000-000000000020",
            workspaceId,
            seasonId,
            "handoff:concurrent",
            1,
        )

        assertThat(concurrentStatuses { postEvent(event).andReturn().response.status })
            .containsExactly(200, 202)
        assertThat(jdbc.sql("SELECT COUNT(*) FROM source_event_receipt").query(Long::class.java).single())
            .isEqualTo(1)

        val path = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/editions"
        val request = """{"weekStart":"2026-08-10","zoneId":"Asia/Seoul"}"""
        assertThat(concurrentStatuses { postEdition(path, request).andReturn().response.status })
            .containsExactly(200, 201)
        assertThat(jdbc.sql("SELECT COUNT(*) FROM brief_edition").query(Long::class.java).single())
            .isEqualTo(1)

        val rebuildStarted = CountDownLatch(1)
        val releaseRebuild = CountDownLatch(1)
        val receivedAt = Instant.parse("2026-08-12T09:00:01Z")
        val supportedEvent = SourceEvent(
            eventId = UUID.fromString("30000000-0000-0000-0000-000000000021"),
            eventType = SourceEventType.HANDOFF_BLOCKED,
            eventVersion = 1,
            workspaceId = UUID.fromString(workspaceId),
            seasonId = UUID.fromString(seasonId),
            sourceReference = "handoff:during-rebuild",
            aggregateRevision = 1,
            occurredAt = receivedAt,
            state = SourceEventState.ACTIVE,
        )

        Executors.newFixedThreadPool(2).use { executor ->
            val rebuild = executor.submit<RebuildResult> {
                persistence.rebuild { receivedEvent, current ->
                    rebuildStarted.countDown()
                    check(releaseRebuild.await(10, TimeUnit.SECONDS))
                    AttentionProjector.project(receivedEvent, current)
                }
            }
            try {
                assertThat(rebuildStarted.await(10, TimeUnit.SECONDS)).isTrue()
                val ingest = executor.submit<IngestResult> {
                    persistence.processEvent(
                        event = supportedEvent,
                        fingerprint = "a".repeat(64),
                        receivedAt = receivedAt,
                        conflictDetectedAt = { receivedAt },
                    ) { current ->
                        AttentionProjector.project(supportedEvent, current)
                    }
                }

                val waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                var sharedLockWaitObserved: Boolean
                do {
                    sharedLockWaitObserved = jdbc.sql(
                        """
                        SELECT EXISTS (
                            SELECT 1
                              FROM pg_locks
                             WHERE database = (
                                       SELECT oid
                                         FROM pg_database
                                        WHERE datname = current_database()
                                   )
                               AND locktype = 'advisory'
                               AND mode = 'ShareLock'
                               AND NOT granted
                        )
                        """.trimIndent(),
                    ).query(Boolean::class.java).single()
                    if (!sharedLockWaitObserved) {
                        TimeUnit.MILLISECONDS.sleep(10)
                    }
                } while (!sharedLockWaitObserved && System.nanoTime() < waitDeadline)
                assertThat(sharedLockWaitObserved).isTrue()
                releaseRebuild.countDown()

                rebuild.get(10, TimeUnit.SECONDS)
                assertThat(ingest.get(10, TimeUnit.SECONDS).status).isEqualTo(IngestStatus.APPLIED)
            } finally {
                releaseRebuild.countDown()
            }
        }
        assertThat(
            jdbc.sql(
                "SELECT COUNT(*) FROM attention_item WHERE source_reference = :sourceReference",
            ).param("sourceReference", supportedEvent.sourceReference)
                .query(Long::class.java)
                .single(),
        ).isEqualTo(1)
    }

    private fun postEvent(event: ObjectNode) = postEvent(JSON.writeValueAsString(event))

    private fun postEvent(json: String) = mockMvc.perform(
        post("/api/v1/events")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $EVENT_BEARER_TOKEN")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json),
    )

    private fun postEdition(
        path: String,
        json: String,
    ) = mockMvc.perform(
        post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json),
    )

    private fun concurrentStatuses(request: () -> Int): List<Int> =
        Executors.newFixedThreadPool(2).use { executor ->
            val barrier = CyclicBarrier(2)
            List(2) {
                executor.submit<Int> {
                    barrier.await()
                    request()
                }
            }.map { it.get(10, TimeUnit.SECONDS) }.sorted()
        }

    private fun contractEvent(fileName: String): String =
        ClassPathResource("contracts/examples/$fileName")
            .getContentAsString(Charsets.UTF_8)

    private fun eventJson(
        eventId: String,
        workspaceId: String,
        seasonId: String,
        sourceReference: String,
        revision: Long,
        state: String = "ACTIVE",
        type: String = "HANDOFF_BLOCKED",
        occurredAt: String = "2026-08-12T09:00:00Z",
        eventVersion: Int = 1,
        sourceSeverity: String? = null,
    ): ObjectNode = JSON.createObjectNode()
        .put("eventId", eventId)
        .put("eventType", type)
        .put("eventVersion", eventVersion)
        .put("workspaceId", workspaceId)
        .put("seasonId", seasonId)
        .put("sourceReference", sourceReference)
        .put("aggregateRevision", revision)
        .put("occurredAt", occurredAt)
        .put("state", state)
        .apply { sourceSeverity?.let { put("sourceSeverity", it) } }

    companion object {
        private val JSON = JsonMapper.builder().build()

        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }
}

private const val EVENT_BEARER_TOKEN = "brief-event-receiver-test-token-00000001"
private const val PREVIOUS_EVENT_BEARER_TOKEN = "brief-event-receiver-test-token-previous-01"
