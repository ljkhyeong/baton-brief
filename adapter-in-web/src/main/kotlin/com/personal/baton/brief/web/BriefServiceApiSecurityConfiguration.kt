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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

@ConfigurationProperties("brief.service-api")
class BriefServiceApiSecurityProperties(
    @DefaultValue("false") val authenticationRequired: Boolean,
    @DefaultValue("") private val bearerToken: String,
    @DefaultValue("") private val previousBearerToken: String,
) {
    fun acceptedBearerTokens(): List<String> = acceptedBearerTokens(
        bearerToken,
        previousBearerToken,
        "BRIEF 서비스 API",
    )
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BriefServiceApiSecurityProperties::class)
class BriefServiceApiSecurityConfiguration {
    @Bean
    @Order(2)
    fun serviceApiSecurityFilterChain(
        http: HttpSecurity,
        properties: BriefServiceApiSecurityProperties,
        eventProperties: BriefEventReceiverSecurityProperties,
    ): SecurityFilterChain {
        http
            .securityMatcher(SERVICE_API)
            .csrf { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .logout { it.disable() }

        if (!properties.authenticationRequired) {
            return http
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .build()
        }

        val serviceApiTokens = properties.acceptedBearerTokens()
        if (eventProperties.authenticationRequired) {
            val eventTokens = eventProperties.acceptedBearerTokens()
            require(serviceApiTokens.none(eventTokens::contains)) {
                "BRIEF 이벤트 수신과 서비스 API bearer token은 서로 달라야 합니다"
            }
        }

        val authenticationManager = staticBearerAuthenticationManager(
            serviceApiTokens,
            "baton-backend",
            "BRIEF 서비스 API 인증 정보가 올바르지 않습니다",
        )
        return http
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling { it.authenticationEntryPoint(BearerTokenAuthenticationEntryPoint()) }
            .oauth2ResourceServer {
                it.authenticationManagerResolver { authenticationManager }
            }
            .build()
    }

    @Bean
    @Order(3)
    fun unlistedApiSecurityFilterChain(
        http: HttpSecurity,
        properties: BriefServiceApiSecurityProperties,
    ): SecurityFilterChain {
        http
            .securityMatcher(pathPattern("/api/v1/**"))
            .csrf { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .logout { it.disable() }
            .authorizeHttpRequests {
                if (properties.authenticationRequired) {
                    it.anyRequest().denyAll()
                } else {
                    it.anyRequest().permitAll()
                }
            }
        return http.build()
    }

    private companion object {
        val SERVICE_API: RequestMatcher = OrRequestMatcher(
            pathPattern(HttpMethod.GET, "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items"),
            pathPattern(HttpMethod.GET, "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/current"),
            pathPattern(HttpMethod.GET, "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/transitions"),
            pathPattern(HttpMethod.GET, "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions"),
            pathPattern(HttpMethod.GET, "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions/latest"),
            pathPattern(HttpMethod.GET, "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions/weekly/latest"),
            pathPattern(HttpMethod.GET, "/api/v1/editions/{editionId}"),
            pathPattern(HttpMethod.GET, "/api/v1/editions/{targetEditionId}/changes"),
            pathPattern(HttpMethod.POST, "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions"),
        )
    }
}
