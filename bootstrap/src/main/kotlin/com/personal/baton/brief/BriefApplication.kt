package com.personal.baton.brief

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
class BriefApplication

fun main(args: Array<String>) {
    runApplication<BriefApplication>(*args)
}
