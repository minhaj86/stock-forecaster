package com.stockforecast.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Hashes are stored with an algorithm prefix ({@code {bcrypt}...}) so the algorithm or work factor can be
 * upgraded later; outdated hashes are transparently re-hashed at the next successful login.
 *
 * <p>Trade-off: BCrypt over Argon2id. Argon2id is memory-hard (better against GPUs) but costs ~16-64 MiB per
 * verification, which turns login floods into memory exhaustion before rate limiting exists. BCrypt is
 * CPU-bound and allocation-free; the delegating encoder keeps the migration path open.
 */
@Configuration(proxyBeanMethods = false)
class PasswordEncodingConfiguration {

    private static final String BCRYPT = "bcrypt";

    @Bean
    PasswordEncoder passwordEncoder(PasswordProperties properties) {
        return new DelegatingPasswordEncoder(BCRYPT,
                Map.of(BCRYPT, new BCryptPasswordEncoder(properties.bcryptStrength())));
    }
}
