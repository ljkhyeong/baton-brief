package com.personal.baton.brief.web

import java.security.MessageDigest
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.RequestMatcher

private val BEARER_TOKEN_PATTERN = Regex("[A-Za-z0-9._~-]{32,200}")

internal fun acceptedBearerTokens(
    currentToken: String,
    previousToken: String,
    boundaryName: String,
): List<String> {
    require(BEARER_TOKEN_PATTERN.matches(currentToken)) {
        "$boundaryName 현재 bearer token은 32~200자의 URL-safe ASCII여야 합니다"
    }
    if (previousToken.isBlank()) {
        return listOf(currentToken)
    }
    require(BEARER_TOKEN_PATTERN.matches(previousToken)) {
        "$boundaryName 직전 bearer token은 32~200자의 URL-safe ASCII여야 합니다"
    }
    return listOf(currentToken, previousToken)
}

internal fun HttpSecurity.configureStatelessApi(): HttpSecurity = this
    .csrf { it.disable() }
    .requestCache { it.disable() }
    .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    .logout { it.disable() }

/** `acceptedTokens`가 `null`이면 인증 없이 허용하고, 아니면 해당 token 중 하나를 요구한다. */
internal fun HttpSecurity.staticBearerFilterChain(
    matcher: RequestMatcher,
    acceptedTokens: List<String>?,
    principal: String,
    failureMessage: String,
): SecurityFilterChain {
    securityMatcher(matcher).configureStatelessApi()
    if (acceptedTokens == null) {
        return authorizeHttpRequests { it.anyRequest().permitAll() }.build()
    }

    val authenticationManager = staticBearerAuthenticationManager(acceptedTokens, principal, failureMessage)
    return authorizeHttpRequests { it.anyRequest().authenticated() }
        .oauth2ResourceServer { it.authenticationManagerResolver { authenticationManager } }
        .build()
}

private fun staticBearerAuthenticationManager(
    acceptedTokens: List<String>,
    principal: String,
    failureMessage: String,
): AuthenticationManager {
    val expectedTokens = acceptedTokens.map(String::encodeToByteArray)
    return AuthenticationManager { authentication ->
        val presentedToken = (authentication as? BearerTokenAuthenticationToken)
            ?.token
            ?.encodeToByteArray()
        if (
            presentedToken == null ||
            expectedTokens.none { expectedToken ->
                MessageDigest.isEqual(expectedToken, presentedToken)
            }
        ) {
            throw BadCredentialsException(failureMessage)
        }
        UsernamePasswordAuthenticationToken.authenticated(principal, null, emptyList())
    }
}
