package com.personal.baton.brief

import com.personal.baton.brief.web.BriefEventSecurityConfiguration
import com.personal.baton.brief.web.BriefServiceApiSecurityConfiguration
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
        ).withUserConfiguration(
            BriefEventSecurityConfiguration::class.java,
            BriefServiceApiSecurityConfiguration::class.java,
        )

    @Test
    fun `이벤트와 서비스 API 토큰이 겹치면 기동하지 않는다`() {
        contextRunner
            .withPropertyValues(
                "brief.event-receiver.authentication-required=true",
                "brief.event-receiver.bearer-token=$EVENT_TOKEN",
                "brief.service-api.authentication-required=true",
                "brief.service-api.bearer-token=$SERVICE_TOKEN",
                "brief.service-api.previous-bearer-token=$EVENT_TOKEN",
            ).run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasRootCauseMessage(
                        "BRIEF 이벤트 수신과 서비스 API bearer token은 서로 달라야 합니다",
                    ).hasMessageNotContaining(EVENT_TOKEN)
            }
    }

    private companion object {
        const val EVENT_TOKEN = "brief-event-token-000000000000000000001"
        const val SERVICE_TOKEN = "brief-service-token-0000000000000000001"
    }
}
