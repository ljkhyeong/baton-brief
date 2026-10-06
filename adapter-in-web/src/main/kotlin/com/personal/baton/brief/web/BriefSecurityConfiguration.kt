package com.personal.baton.brief.web

import java.security.MessageDigest
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern

private val BEARER_TOKEN_PATTERN = Regex("[A-Za-z0-9._~-]{32,200}")

@ConfigurationProperties("brief")
class BriefSecurityProperties(
    @DefaultValue val eventReceiver: StaticBearer,
    @DefaultValue val serviceApi: StaticBearer,
) {
    /** token이 `toString`·기동 실패 메시지에 드러나지 않도록 data class로 만들지 않는다. */
    class StaticBearer(
        @DefaultValue("false") private val authenticationRequired: Boolean,
        @DefaultValue("") private val bearerToken: String,
        @DefaultValue("") private val previousBearerToken: String,
    ) {
        /** 인증을 끄면 `null`, 켜면 형식을 검사한 현재·직전 token을 반환한다. */
        fun acceptedTokens(boundaryName: String): List<String>? {
            if (!authenticationRequired) return null
            require(BEARER_TOKEN_PATTERN.matches(bearerToken)) {
                "$boundaryName 현재 bearer token은 32~200자의 URL-safe ASCII여야 합니다"
            }
            if (previousBearerToken.isBlank()) {
                return listOf(bearerToken)
            }
            require(BEARER_TOKEN_PATTERN.matches(previousBearerToken)) {
                "$boundaryName 직전 bearer token은 32~200자의 URL-safe ASCII여야 합니다"
            }
            return listOf(bearerToken, previousBearerToken)
        }
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(BriefSecurityProperties::class)
class BriefSecurityConfiguration(properties: BriefSecurityProperties) {
    private val eventTokens = properties.eventReceiver.acceptedTokens("BRIEF 이벤트 수신")
    private val serviceApiTokens = properties.serviceApi.acceptedTokens("BRIEF 서비스 API")

    init {
        require(serviceApiTokens.orEmpty().none(eventTokens.orEmpty()::contains)) {
            "BRIEF 이벤트 수신과 서비스 API bearer token은 서로 달라야 합니다"
        }
    }

    @Bean
    @Order(1)
    fun eventIngestionSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .securityMatcher(pathPattern(HttpMethod.POST, "/api/v1/events"))
        .staticBearerFilterChain(eventTokens) { it.anyRequest().authenticated() }

    // 서비스 API 허용 목록만 서비스 token으로 열고, 목록 밖 /api/v1 경로는 인증을 켜면 거부한다.
    // 허용 목록을 바꾸면 ops/Caddyfile.service와 PRD-0025를 함께 맞춘다.
    @Bean
    @Order(2)
    fun serviceApiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .securityMatcher("/api/v1/**")
        .staticBearerFilterChain(serviceApiTokens) {
            it.requestMatchers(
                HttpMethod.GET,
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items",
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/current",
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/summary",
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/resolutions",
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/attention-items/transitions",
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions",
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions/latest",
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions/weekly/latest",
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions/{targetEditionId}/changes",
                "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions/{editionId}/freshness",
                "/api/v1/editions/{editionId}",
                "/api/v1/editions/{targetEditionId}/changes",
            ).authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/workspaces/{workspaceId}/seasons/{seasonId}/editions")
                .authenticated()
                .anyRequest().denyAll()
        }
}

/** `acceptedTokens`가 `null`이면 인증 없이 허용하고, 아니면 해당 token 중 하나와 `authorize` 규칙을 요구한다. */
private fun HttpSecurity.staticBearerFilterChain(
    acceptedTokens: List<String>?,
    authorize: Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry>,
): SecurityFilterChain {
    csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    if (acceptedTokens == null) {
        return authorizeHttpRequests { it.anyRequest().permitAll() }.build()
    }

    val expectedTokens = acceptedTokens.map(String::encodeToByteArray)
    val authenticationManager = AuthenticationManager { authentication ->
        val presentedToken = (authentication as? BearerTokenAuthenticationToken)?.token?.encodeToByteArray()
        if (presentedToken == null || expectedTokens.none { MessageDigest.isEqual(it, presentedToken) }) {
            throw BadCredentialsException("bearer token이 올바르지 않습니다")
        }
        // 요청 token 객체는 principal·credentials가 token 원문이므로 고정 principal로 바꿔 로그 노출을 막는다.
        UsernamePasswordAuthenticationToken.authenticated("baton", null, emptyList())
    }
    return authorizeHttpRequests(authorize)
        .oauth2ResourceServer { it.authenticationManagerResolver { authenticationManager } }
        .build()
}
