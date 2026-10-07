package com.personal.baton.brief

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/** 통합 테스트 컨텍스트마다 PostgreSQL 컨테이너 하나를 띄워 데이터 소스로 연결한다. */
@TestConfiguration(proxyBeanMethods = false)
class BriefPostgresConfiguration {
    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer("postgres:18.6-alpine")
}
