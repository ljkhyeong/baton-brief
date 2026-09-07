package com.personal.baton.brief

import com.personal.baton.brief.application.BriefUseCases
import com.personal.baton.brief.config.BriefOperationsConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.json.JsonMapper

class BriefOperationsConfigurationTest {
    @Test
    fun `운영 명령은 웹 서버 또는 자동 마이그레이션이 켜지면 실행하지 않는다`() {
        val brief = mock(BriefUseCases::class.java)
        val runner = ApplicationContextRunner()
            .withUserConfiguration(BriefOperationsConfiguration::class.java)
            .withBean(BriefUseCases::class.java, { brief })
            .withBean(JsonMapper::class.java, { JsonMapper.builder().build() })
            .withPropertyValues("brief.operations.command=REBUILD")

        runner.withPropertyValues("spring.main.web-application-type=servlet", "spring.flyway.enabled=false")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasRootCauseMessage(
                    "운영 명령은 spring.main.web-application-type=none으로 실행해야 합니다",
                )
            }
        runner.withPropertyValues("spring.main.web-application-type=none", "spring.flyway.enabled=true")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasRootCauseMessage(
                    "운영 명령은 spring.flyway.enabled=false로 실행해야 합니다",
                )
            }
        verifyNoInteractions(brief)
    }
}
