package com.stockforecast.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Stateless API security.
 *
 * <ul>
 *     <li>Access: short-lived JWT bearer tokens validated by the OAuth2 resource-server filter.</li>
 *     <li>Session continuity: rotating refresh token in an HttpOnly, SameSite=Strict cookie scoped to the auth
 *     endpoints.</li>
 *     <li>CSRF protection is disabled because no ambient credential authorizes API calls: bearer tokens are
 *     attached explicitly by the client, and the refresh cookie is SameSite=Strict and only accepted by
 *     endpoints whose effect is to (re)issue tokens to the caller itself.</li>
 *     <li>Deny by default: anything not explicitly public requires authentication; admin paths require
 *     {@code ADMIN} here and again via method security (defence in depth).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"};

    private static final RequestMatcher AUTH_ENDPOINTS =
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/**");

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, AuthenticatedUserJwtConverter jwtConverter,
                                               ApiAuthenticationEntryPoint authenticationEntryPoint,
                                               ApiAccessDeniedHandler accessDeniedHandler) {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Scraped by Prometheus; in production the management port is not publicly exposed.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/error", "/.well-known/oauth-protected-resource").permitAll()
                        .requestMatchers("/api/v1/admin/**", "/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        // Spring Security publishes RFC 9728 metadata; describe our (non mTLS-bound) tokens accurately.
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(builder ->
                                builder.resourceName("Stock Forecast Platform API")
                                        .tlsClientCertificateBoundAccessTokens(false)))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Ignores bearer tokens on the authentication endpoints: they authenticate by credentials or refresh cookie, and
     * a client that still attaches its expired access token to {@code /auth/refresh} must get a new one, not a 401.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> AUTH_ENDPOINTS.matches(request) ? null : delegate.resolve(request);
    }
}
