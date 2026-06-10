package com.stockforecast.identity.api;

import com.stockforecast.identity.application.IssuedRefreshToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Transports the refresh token in an HttpOnly cookie so page scripts (and therefore XSS payloads) can never
 * read it; the short-lived access token is returned in the response body and kept in memory by the SPA.
 */
@Component
class RefreshTokenCookies {

    private final RefreshCookieProperties properties;
    private final Clock clock;

    RefreshTokenCookies(RefreshCookieProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Persistent sessions get a cookie that lives as long as the token; others get a browser-session cookie (no
     * Max-Age) that disappears when the browser closes. The server-side expiry applies in both cases.
     */
    ResponseCookie create(IssuedRefreshToken token) {
        if (!token.persistent()) {
            return baseCookie(token.value()).build();
        }
        Duration remaining = Duration.between(clock.instant(), token.expiresAt());
        // Round up to whole seconds so the cookie never disappears before the token itself expires.
        long maxAgeSeconds = remaining.isNegative() ? 0 : remaining.plusMillis(999).toSeconds();
        return baseCookie(token.value()).maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
    }

    ResponseCookie clear() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    Optional<String> read(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, properties.name()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path());
    }
}
