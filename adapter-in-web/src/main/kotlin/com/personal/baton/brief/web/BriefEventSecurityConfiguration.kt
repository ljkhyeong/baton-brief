package com.personal.baton.brief.web

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

@ConfigurationProperties("brief.event-receiver")
class BriefEventReceiverSecurityProperties(
    @DefaultValue("false") val authenticationRequired: Boolean,
    @DefaultValue("") private val bearerToken: String,
    @DefaultValue("") private val previousBearerToken: String,
) {
    fun acceptedBearerTokens(): List<String> = acceptedBearerTokens(
        bearerToken,
        previousBearerToken,
        "BRIEF 이벤트 수신",
    )
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BriefEventReceiverSecurityProperties::class)
class BriefEventSecurityConfiguration {
    @Bean
    @Order(1)
    fun eventIngestionSecurityFilterChain(
        http: HttpSecurity,
        properties: BriefEventReceiverSecurityProperties,
    ): SecurityFilterChain {
        http
            .securityMatcher(EVENT_INGESTION)
            .csrf { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .logout { it.disable() }

        if (!properties.authenticationRequired) {
            return http
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .build()
        }

        val authenticationManager = staticBearerAuthenticationManager(
            properties.acceptedBearerTokens(),
            "baton-event-producer",
            "BRIEF 이벤트 수신 인증 정보가 올바르지 않습니다",
        )
        val entryPoint = BearerTokenAuthenticationEntryPoint()

        return http
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
            .oauth2ResourceServer {
                it.authenticationManagerResolver { authenticationManager }
            }
            .build()
    }

    private companion object {
        val EVENT_INGESTION = PathPatternRequestMatcher.pathPattern(
            HttpMethod.POST,
            "/api/v1/events",
        )
    }
}
