package com.personal.baton.brief.config

import com.personal.baton.brief.application.BriefPersistencePort
import com.personal.baton.brief.application.BriefService
import com.personal.baton.brief.application.BriefUseCases
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class BriefRuntimeConfiguration {
    @Bean
    fun briefClock(): Clock = Clock.systemUTC()

    @Bean
    fun briefUseCases(
        persistence: BriefPersistencePort,
        clock: Clock,
    ): BriefUseCases = BriefService(persistence, clock)
}
