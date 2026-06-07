package com.stockforecast.identity.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The HMAC key shared by the access-token encoder and decoder.
 *
 * <p>Trade-off: HS256 with one shared secret suits a single deployable that both issues and verifies tokens.
 * Moving to RS256/ES256 with a published JWKS is the planned path once another service must verify tokens.
 */
record JwtSigningKey(SecretKey key) {

    static final int MINIMUM_KEY_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(JwtSigningKey.class);
    private static final String ALGORITHM = "HmacSHA256";

    static JwtSigningKey from(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            if (!properties.allowEphemeralSecret()) {
                throw new IllegalStateException(
                        "app.security.jwt.secret (JWT_SECRET) must be set to a Base64-encoded key of at least "
                                + MINIMUM_KEY_BYTES + " bytes, e.g. `openssl rand -base64 48`");
            }
            log.warn("No JWT secret configured: using an ephemeral random key. Tokens will not survive restarts. "
                    + "Never use this outside local development.");
            byte[] random = new byte[MINIMUM_KEY_BYTES * 2];
            new SecureRandom().nextBytes(random);
            return new JwtSigningKey(new SecretKeySpec(random, ALGORITHM));
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) must be valid Base64", e);
        }
        if (decoded.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) must decode to at least "
                    + MINIMUM_KEY_BYTES + " bytes (256 bits) for HS256");
        }
        return new JwtSigningKey(new SecretKeySpec(decoded, ALGORITHM));
    }

    @Override
    public String toString() {
        return "JwtSigningKey[***]";
    }
}
