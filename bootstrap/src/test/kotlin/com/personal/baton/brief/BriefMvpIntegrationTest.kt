package com.personal.baton.brief

import com.jayway.jsonpath.JsonPath
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BriefMvpIntegrationTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val jdbc: JdbcClient,
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
            RESTART IDENTITY CASCADE
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
    fun `ingestion distinguishes duplicate conflict unsupported stale and gap`() {
        val workspaceId = "10000000-0000-0000-0000-000000000001"
        val seasonId = "20000000-0000-0000-0000-000000000001"
        val eventId = "30000000-0000-0000-0000-000000000001"
        val first = eventJson(eventId, workspaceId, seasonId, "handoff:1", 1)

        postEvent(first)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("APPLIED"))
            .andExpect(jsonPath("$.item.severity").value("HIGH"))

        postEvent(first)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DUPLICATE"))

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

        postEvent(
            eventJson(
                "30000000-0000-0000-0000-000000000002",
                workspaceId,
                seasonId,
                "handoff:1",
                1,
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
        ).andExpect(status().isAccepted)
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
        ).andExpect(status().isAccepted)
            .andExpect(jsonPath("$.item.observedAt").value("2026-08-16T14:59:59.999999Z"))
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
        ).andExpect(status().isAccepted)

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
        assertThat(firstResult.response.getHeader("Location"))
            .isEqualTo("/api/v1/editions/$firstEditionId")

        postEdition(generationPath, editionRequest)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.editionId").value(firstEditionId))

        postEvent(
            eventJson(
                "40000000-0000-0000-0000-000000000004",
                workspaceId,
                seasonId,
                blockedReference,
                2,
                state = "RESOLVED",
                occurredAt = "2026-08-13T09:00:00Z",
            ),
        ).andExpect(status().isAccepted)

        postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(2))
            .andExpect(jsonPath("$.sourceCursor").value(4))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].reasonCode").value("ROUTINE_MISSED"))

        postEvent(
            eventJson(
                "40000000-0000-0000-0000-000000000005",
                workspaceId,
                seasonId,
                blockedReference,
                3,
                occurredAt = "2026-08-09T15:00:00Z",
            ),
        ).andExpect(status().isAccepted)

        val recurringStateResult = postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(3))
            .andExpect(jsonPath("$.sourceCursor").value(5))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn()
        val recurringStateEditionId = JsonPath.read<String>(
            recurringStateResult.response.contentAsString,
            "$.editionId",
        )
        assertThat(recurringStateEditionId).isNotEqualTo(firstEditionId)

        mockMvc.perform(get(generationPath).param("limit", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.editions.length()").value(2))
            .andExpect(jsonPath("$.editions[0].editionId").value(recurringStateEditionId))
            .andExpect(jsonPath("$.editions[0].generation").value(3))
            .andExpect(jsonPath("$.editions[0].itemCount").value(2))
            .andExpect(jsonPath("$.editions[1].generation").value(2))
            .andExpect(jsonPath("$.editions[1].itemCount").value(1))
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
            .andExpect(jsonPath("$.nextBeforeGeneration").value(nullValue()))

        postEdition(generationPath, editionRequest)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.editionId").value(recurringStateEditionId))

        mockMvc.perform(get("$generationPath/latest"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.generation").value(3))

        mockMvc.perform(post("/api/v1/projections/rebuild"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receiptCount").value(5))
            .andExpect(jsonPath("$.itemCount").value(3))

        postEdition(generationPath, editionRequest)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.editionId").value(recurringStateEditionId))

        val rebuiltSnapshot = mockMvc.perform(get("/api/v1/editions/$firstEditionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn()
        assertThat(rebuiltSnapshot.response.contentAsString)
            .isEqualTo(firstResult.response.contentAsString)
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
        ).andExpect(status().isAccepted)
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
        ).andExpect(status().isAccepted)

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
                "60000000-0000-0000-0000-000000000003",
                workspaceId,
                seasonId,
                removedReference,
                2,
                state = "RESOLVED",
                occurredAt = "2026-08-12T01:00:00Z",
            ),
        ).andExpect(status().isAccepted)
        postEvent(
            eventJson(
                "60000000-0000-0000-0000-000000000004",
                workspaceId,
                seasonId,
                changedReference,
                2,
                type = "ROUTINE_MISSED",
                occurredAt = "2026-08-13T02:00:00Z",
            ),
        ).andExpect(status().isAccepted)
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
        ).andExpect(status().isAccepted)
        postEvent(
            eventJson(
                "60000000-0000-0000-0000-000000000006",
                workspaceId,
                seasonId,
                "handoff:added",
                1,
                occurredAt = "2026-08-15T03:00:00Z",
            ),
        ).andExpect(status().isAccepted)

        val targetEdition = postEdition(generationPath, editionRequest)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.generation").value(2))
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
            .andExpect(jsonPath("$.from.generation").value(1))
            .andExpect(jsonPath("$.from.itemCount").value(2))
            .andExpect(jsonPath("$.to.editionId").value(targetEditionId))
            .andExpect(jsonPath("$.to.generation").value(2))
            .andExpect(jsonPath("$.to.itemCount").value(3))
            .andExpect(jsonPath("$.added.length()").value(2))
            .andExpect(jsonPath("$.added[0].sourceReference").value("handoff:added"))
            .andExpect(jsonPath("$.added[0].severity").value("HIGH"))
            .andExpect(jsonPath("$.added[1].sourceReference").value("decision:added"))
            .andExpect(jsonPath("$.added[1].severity").value("MEDIUM"))
            .andExpect(jsonPath("$.removed.length()").value(1))
            .andExpect(jsonPath("$.removed[0].sourceReference").value(removedReference))
            .andExpect(jsonPath("$.removed[0].reasonCode").value("HANDOFF_BLOCKED"))
            .andExpect(jsonPath("$.changed.length()").value(1))
            .andExpect(jsonPath("$.changed[0].before.sourceReference").value(changedReference))
            .andExpect(jsonPath("$.changed[0].before.observedAt").value("2026-08-11T02:00:00Z"))
            .andExpect(jsonPath("$.changed[0].after.sourceReference").value(changedReference))
            .andExpect(jsonPath("$.changed[0].after.observedAt").value("2026-08-13T02:00:00Z"))
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
            .andExpect(jsonPath("$.changed[0].before.observedAt").value("2026-08-13T02:00:00Z"))
            .andExpect(jsonPath("$.changed[0].after.observedAt").value("2026-08-11T02:00:00Z"))

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
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))

        mockMvc.perform(
            get(changesPath)
                .param("fromEditionId", "50000000-0000-0000-0000-000000000099"),
        ).andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `rejects representations outside the HTTP contract and reports missing editions`() {
        val workspaceId = "10000000-0000-0000-0000-000000000003"
        val seasonId = "20000000-0000-0000-0000-000000000003"
        val eventId = "30000000-0000-0000-0000-000000000010"

        postEvent(eventJson(eventId, workspaceId, seasonId, "invalid", 1, eventVersion = 0))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").isNotEmpty)
            .andExpect(jsonPath("$.instance").value("/api/v1/events"))

        val numericInstant = eventJson(eventId, workspaceId, seasonId, "invalid", 1)
            .replace("\"occurredAt\": \"2026-08-12T09:00:00Z\"", "\"occurredAt\": 1786525200")
        postEvent(numericInstant).andExpect(status().isBadRequest)

        val path = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/editions"
        postEdition(path, """{"weekStart":[2026,8,10],"zoneId":"Asia/Seoul"}""")
            .andExpect(status().isBadRequest)
        postEdition(path, """{"weekStart":"2026-08-11","zoneId":"Asia/Seoul"}""")
            .andExpect(status().isBadRequest)
        postEdition(path, """{"weekStart":"2026-08-10","zoneId":"+09:00"}""")
            .andExpect(status().isBadRequest)

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
    fun `serializes concurrent duplicate ingestion and edition generation`() {
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
    }

    private fun postEvent(json: String) = mockMvc.perform(
        post("/api/v1/events")
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
    ): String = """
        {
          "eventId": "$eventId",
          "eventType": "$type",
          "eventVersion": $eventVersion,
          "workspaceId": "$workspaceId",
          "seasonId": "$seasonId",
          "sourceReference": "$sourceReference",
          "aggregateRevision": $revision,
          "occurredAt": "$occurredAt",
          "state": "$state"
        }
    """.trimIndent()

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:18.4-alpine")
    }
}
