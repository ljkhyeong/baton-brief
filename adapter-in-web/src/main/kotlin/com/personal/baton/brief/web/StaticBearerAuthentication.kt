package com.personal.baton.brief.web

import java.security.MessageDigest
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken

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

internal fun staticBearerAuthenticationManager(
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

