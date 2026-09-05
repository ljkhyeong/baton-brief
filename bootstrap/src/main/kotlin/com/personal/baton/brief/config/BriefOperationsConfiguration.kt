package com.personal.baton.brief.config

import com.personal.baton.brief.application.BriefUseCases
import java.util.UUID
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import tools.jackson.databind.json.JsonMapper

@ConfigurationProperties("brief.operations")
data class BriefOperationsProperties(
    val command: Command,
    val eventId: UUID? = null,
    val workspaceId: UUID? = null,
    val seasonId: UUID? = null,
    val beforeIngestionSequence: Long? = null,
    val limit: Int = 20,
) {
    enum class Command {
        RECEIPT,
        ANOMALIES,
        REBUILD,
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("brief.operations.command")
@EnableConfigurationProperties(BriefOperationsProperties::class)
class BriefOperationsConfiguration {
    @Bean
    fun briefOperationsRunner(
        properties: BriefOperationsProperties,
        environment: Environment,
        brief: BriefUseCases,
        json: JsonMapper,
    ): ApplicationRunner {
        require(environment.getProperty("spring.main.web-application-type").equals("none", ignoreCase = true)) {
            "운영 명령은 spring.main.web-application-type=none으로 실행해야 합니다"
        }
        require(environment.getProperty("spring.flyway.enabled", Boolean::class.java) == false) {
            "운영 명령은 spring.flyway.enabled=false로 실행해야 합니다"
        }
        return ApplicationRunner {
            val result = when (properties.command) {
                BriefOperationsProperties.Command.RECEIPT -> brief.findEventReceipt(
                    requireNotNull(properties.eventId) { "수신 증거 조회에는 event-id가 필요합니다" },
                ) ?: error("이벤트 수신 증거를 찾을 수 없습니다")

                BriefOperationsProperties.Command.ANOMALIES -> {
                    require(properties.limit in 1..100) { "limit은 1~100이어야 합니다" }
                    require(properties.beforeIngestionSequence == null || properties.beforeIngestionSequence > 0) {
                        "before-ingestion-sequence는 양수여야 합니다"
                    }
                    brief.findEventReceiptAnomalies(
                        requireNotNull(properties.workspaceId) { "이상 이력 조회에는 workspace-id가 필요합니다" },
                        requireNotNull(properties.seasonId) { "이상 이력 조회에는 season-id가 필요합니다" },
                        properties.beforeIngestionSequence,
                        properties.limit,
                    )
                }

                BriefOperationsProperties.Command.REBUILD -> brief.rebuild()
            }
            println(json.writeValueAsString(result))
        }
    }
}
