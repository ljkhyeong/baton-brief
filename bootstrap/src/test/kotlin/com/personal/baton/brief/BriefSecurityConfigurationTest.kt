package com.personal.baton.brief

import com.personal.baton.brief.web.BriefSecurityConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

class BriefSecurityConfigurationTest {
    private val contextRunner = WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SecurityAutoConfiguration::class.java,
                ServletWebSecurityAutoConfiguration::class.java,
            ),
        ).withUserConfiguration(BriefSecurityConfiguration::class.java)

    @Test
    fun `이벤트 인증을 켠 경우에만 필수 토큰을 검증한다`() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
        }
        contextRunner
            .withPropertyValues("brief.event-receiver.authentication-required=true")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasRootCauseMessage(
                        "BRIEF 이벤트 수신 현재 bearer token은 32~200자의 URL-safe ASCII여야 합니다",
                    )
            }
    }

    @Test
    fun `토큰은 형식이 맞는 값만 받고 잘못된 값은 원문 없이 기동을 거부한다`() {
        val serviceApi = contextRunner.withPropertyValues("brief.service-api.authentication-required=true")
        listOf("a".repeat(31), "a".repeat(201), "a".repeat(31) + "!").forEach { token ->
            serviceApi.withPropertyValues("brief.service-api.bearer-token=$token").run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasRootCauseMessage("BRIEF 서비스 API 현재 bearer token은 32~200자의 URL-safe ASCII여야 합니다")
                    .hasMessageNotContaining(token)
            }
        }
        listOf("a".repeat(32), "a".repeat(200)).forEach { token ->
            serviceApi.withPropertyValues("brief.service-api.bearer-token=$token").run { context ->
                assertThat(context).hasNotFailed()
            }
        }
        serviceApi.withPropertyValues(
            "brief.service-api.bearer-token=$SERVICE_TOKEN",
            "brief.service-api.previous-bearer-token=too-short",
        ).run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure)
                .hasRootCauseMessage("BRIEF 서비스 API 직전 bearer token은 32~200자의 URL-safe ASCII여야 합니다")
        }
    }

    @Test
    fun `이벤트와 서비스 API 토큰이 겹치면 기동하지 않는다`() {
        listOf(
            listOf("event-receiver.bearer-token=$SHARED_TOKEN", "service-api.bearer-token=$SHARED_TOKEN"),
            listOf(
                "event-receiver.bearer-token=$SHARED_TOKEN",
                "service-api.bearer-token=$SERVICE_TOKEN",
                "service-api.previous-bearer-token=$SHARED_TOKEN",
            ),
            listOf(
                "event-receiver.bearer-token=$EVENT_TOKEN",
                "event-receiver.previous-bearer-token=$SHARED_TOKEN",
                "service-api.bearer-token=$SHARED_TOKEN",
            ),
            listOf(
                "event-receiver.bearer-token=$EVENT_TOKEN",
                "event-receiver.previous-bearer-token=$SHARED_TOKEN",
                "service-api.bearer-token=$SERVICE_TOKEN",
                "service-api.previous-bearer-token=$SHARED_TOKEN",
            ),
        ).forEach { tokens ->
            contextRunner
                .withPropertyValues(
                    "brief.event-receiver.authentication-required=true",
                    "brief.service-api.authentication-required=true",
                    *tokens.map { "brief.$it" }.toTypedArray(),
                ).run { context ->
                    assertThat(context).hasFailed()
                    assertThat(context.startupFailure)
                        .hasRootCauseMessage(
                            "BRIEF 이벤트 수신과 서비스 API bearer token은 서로 달라야 합니다",
                        ).hasMessageNotContaining(SHARED_TOKEN)
                }
        }
    }

    private companion object {
        const val EVENT_TOKEN = "brief-event-token-000000000000000000001"
        const val SERVICE_TOKEN = "brief-service-token-0000000000000000001"
        const val SHARED_TOKEN = "brief-shared-token-00000000000000000001"
    }
}
