package com.personal.baton.brief.web

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

@ConfigurationProperties("brief.event-receiver")
class BriefEventReceiverSecurityProperties(
    @DefaultValue("false") val authenticationRequired: Boolean,
    @DefaultValue("") private val bearerToken: String,
    @DefaultValue("") private val previousBearerToken: String,
) {
    fun acceptedBearerTokens(): List<String> =
        acceptedBearerTokens(bearerToken, previousBearerToken, "BRIEF 이벤트 수신")
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
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
            .configureStatelessApi()

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
        return http
            .authorizeHttpRequests { it.anyRequest().authenticated() }
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
