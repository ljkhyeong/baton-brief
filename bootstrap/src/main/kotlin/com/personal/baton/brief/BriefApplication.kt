package com.personal.baton.brief

import com.personal.baton.brief.config.OPERATIONS_COMMAND_PROPERTY
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
class BriefApplication

fun main(args: Array<String>) {
    val context = runApplication<BriefApplication>(*args)
    if (context.environment.containsProperty(OPERATIONS_COMMAND_PROPERTY)) {
        context.close()
    }
}
