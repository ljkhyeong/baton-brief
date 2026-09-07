package com.personal.baton.brief

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
class BriefApplication

fun main(args: Array<String>) {
    val context = runApplication<BriefApplication>(*args)
    if (context.environment.containsProperty("brief.operations.command")) {
        context.close()
    }
}
