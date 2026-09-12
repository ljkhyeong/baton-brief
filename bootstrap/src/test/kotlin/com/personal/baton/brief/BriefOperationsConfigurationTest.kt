package com.personal.baton.brief

import com.personal.baton.brief.application.BriefUseCases
import com.personal.baton.brief.config.BriefOperationsConfiguration
import com.personal.baton.brief.config.BriefOperationsContextInitializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.boot.SpringApplication
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.json.JsonMapper

class BriefOperationsConfigurationTest {
    @Test
    fun `운영 명령은 잘못된 실행 설정을 빈 생성 전에 거부한다`() {
        val brief = mock(BriefUseCases::class.java)
        var useCasesCreated = false
        val runner = ApplicationContextRunner()
            .withInitializer(
                SpringApplication(BriefApplication::class.java).initializers
                    .filterIsInstance<BriefOperationsContextInitializer>().single(),
            )
            .withUserConfiguration(BriefOperationsConfiguration::class.java)
            .withBean(BriefUseCases::class.java, {
                useCasesCreated = true
                brief
            })
            .withBean(JsonMapper::class.java, { JsonMapper.builder().build() })
            .withPropertyValues("brief.operations.command=REBUILD")

        runner.withPropertyValues("spring.main.web-application-type=servlet", "spring.flyway.enabled=false")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasMessageContaining(
                    "운영 명령은 spring.main.web-application-type=none으로 실행해야 합니다",
                )
            }
        runner.withPropertyValues("spring.main.web-application-type=none", "spring.flyway.enabled=true")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasMessageContaining(
                    "운영 명령은 spring.flyway.enabled=false로 실행해야 합니다",
                )
            }
        runner.withPropertyValues("spring.main.web-application-type=none")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasMessageContaining(
                    "운영 명령은 spring.flyway.enabled=false로 실행해야 합니다",
                )
            }
        runner.withPropertyValues("spring.flyway.enabled=false")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasMessageContaining(
                    "운영 명령은 spring.main.web-application-type=none으로 실행해야 합니다",
                )
            }
        assertThat(useCasesCreated).isFalse()
        verifyNoInteractions(brief)
    }
}
