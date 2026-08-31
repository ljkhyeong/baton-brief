package com.personal.baton.brief

import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest(
    properties = [
        "brief.event-receiver.authentication-required=true",
        "brief.event-receiver.bearer-token=$SECURITY_EVENT_TOKEN",
        "brief.service-api.authentication-required=true",
        "brief.service-api.bearer-token=$SERVICE_API_TOKEN",
        "brief.service-api.previous-bearer-token=$PREVIOUS_SERVICE_API_TOKEN",
    ],
)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BriefServiceApiSecurityIntegrationTest(
    private val mockMvc: MockMvc,
) {
    @Test
    fun `서비스 토큰은 허용한 조회와 생성에만 사용할 수 있다`() {
        val workspaceId = "10000000-0000-0000-0000-000000000051"
        val seasonId = "20000000-0000-0000-0000-000000000051"
        val editionPath = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/editions"
        val summaryPath = "/api/v1/workspaces/$workspaceId/seasons/$seasonId/attention-items/summary"

        listOf("$editionPath/latest", summaryPath).forEach { path ->
            mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized)
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))

            mockMvc.perform(
                get(path).header(HttpHeaders.AUTHORIZATION, "Bearer $SECURITY_EVENT_TOKEN"),
            ).andExpect(status().isUnauthorized)
        }

        mockMvc.perform(
            get(summaryPath).header(HttpHeaders.AUTHORIZATION, "Bearer $SERVICE_API_TOKEN"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post(editionPath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $SERVICE_API_TOKEN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "weekStart": "2026-08-24",
                      "zoneId": "Asia/Seoul"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("$editionPath/latest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $PREVIOUS_SERVICE_API_TOKEN"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $SERVICE_API_TOKEN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/v1/projections/rebuild")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $SERVICE_API_TOKEN"),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            get("/api/v1/events/30000000-0000-0000-0000-000000000051/receipt")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $SERVICE_API_TOKEN"),
        ).andExpect(status().isForbidden)
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }
}

private const val SECURITY_EVENT_TOKEN = "brief-security-event-token-000000000001"
private const val SERVICE_API_TOKEN = "brief-service-api-token-000000000000001"
private const val PREVIOUS_SERVICE_API_TOKEN = "brief-service-api-token-previous-000001"
