package com.personal.baton.brief.config

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

class BriefOperationsContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val environment = applicationContext.environment
        if (!environment.containsProperty("brief.operations.command")) return

        require(environment.getProperty("spring.main.web-application-type").equals("none", ignoreCase = true)) {
            "운영 명령은 spring.main.web-application-type=none으로 실행해야 합니다"
        }
        require(environment.getProperty("spring.flyway.enabled", Boolean::class.java) == false) {
            "운영 명령은 spring.flyway.enabled=false로 실행해야 합니다"
        }
    }
}
