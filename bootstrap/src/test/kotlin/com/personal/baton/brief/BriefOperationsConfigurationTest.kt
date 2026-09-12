package com.personal.baton.brief

import com.personal.baton.brief.application.BriefUseCases
import com.personal.baton.brief.config.BriefOperationsConfiguration
import com.personal.baton.brief.config.BriefOperationsContextInitializer
import com.personal.baton.brief.config.BriefOperationsProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.boot.SpringApplication
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.json.JsonMapper

class BriefOperationsConfigurationTest {
    @Test
    fun `운영 프로필은 명령을 지정해야 실행기를 등록한다`() {
        val brief = mock(BriefUseCases::class.java)
        val runner = ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.config.location=classpath:/application.yml")
            .withUserConfiguration(BriefOperationsConfiguration::class.java)
            .withBean(BriefUseCases::class.java, { brief })
            .withBean(JsonMapper::class.java, { JsonMapper.builder().build() })

        listOf("spring.profiles.active=operations", "spring.profiles.default=operations").forEach { profile ->
            runner.withPropertyValues(profile).run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("parameter command")
            }
            runner.withPropertyValues(profile, "brief.operations.command=REBUILD").run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasBean("briefOperationsRunner")
                assertThat(context.getBean(BriefOperationsProperties::class.java).command)
                    .isEqualTo(BriefOperationsProperties.Command.REBUILD)
            }
        }
        verifyNoInteractions(brief)
    }

    @Test
    fun `false 명령은 실행 생략이 아니라 잘못된 명령으로 거부한다`() {
        val brief = mock(BriefUseCases::class.java)
        val runner = ApplicationContextRunner()
            .withUserConfiguration(BriefOperationsConfiguration::class.java)
            .withBean(BriefUseCases::class.java, { brief })
            .withBean(JsonMapper::class.java, { JsonMapper.builder().build() })

        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(BriefOperationsProperties::class.java)
            assertThat(context).doesNotHaveBean("briefOperationsRunner")
        }
        listOf("false", "FALSE").forEach { command ->
            runner.withPropertyValues("brief.operations.command=$command").run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasRootCauseInstanceOf(IllegalArgumentException::class.java)
            }
        }
        verifyNoInteractions(brief)
    }

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
