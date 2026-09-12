package com.personal.baton.brief.config

import com.personal.baton.brief.application.BriefPersistencePort
import com.personal.baton.brief.application.BriefService
import com.personal.baton.brief.application.BriefUseCases
import java.time.Clock
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType

@Configuration(proxyBeanMethods = false)
class BriefRuntimeConfiguration {
    @Bean
    fun strictJsonStringValues(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { builder ->
        builder.withCoercionConfig(LogicalType.Textual) { config ->
            config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
        }
    }

    @Bean
    fun briefClock(): Clock = Clock.systemUTC()

    @Bean
    fun briefUseCases(
        persistence: BriefPersistencePort,
        clock: Clock,
    ): BriefUseCases = BriefService(persistence, clock)
}
