package com.stockforecast.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Self-issued JWT access tokens, validated by Spring Security's resource-server support rather than a
 * hand-written filter.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfiguration {

    /** RFC 9068 access-token type; prevents other JWTs signed with the same key being accepted as access tokens. */
    static final String ACCESS_TOKEN_TYPE = "at+jwt";
    static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    JwtSigningKey jwtSigningKey(JwtProperties properties) {
        return JwtSigningKey.from(properties);
    }

    @Bean
    JwtEncoder jwtEncoder(JwtSigningKey signingKey) {
        return NimbusJwtEncoder.withSecretKey(signingKey.key()).algorithm(ALGORITHM).build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtSigningKey signingKey, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey.key()).macAlgorithm(ALGORITHM).build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(
                timestampValidator,
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()),
                new JwtTypeValidator(ACCESS_TOKEN_TYPE))));
        return decoder;
    }
}
