package com.stockforecast.identity.infrastructure.security;

import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.application.AccessTokenIssuer;
import com.stockforecast.identity.application.IssuedAccessToken;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    static final String EMAIL_CLAIM = "email";
    static final String ROLES_CLAIM = "roles";

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    JwtAccessTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public IssuedAccessToken issue(AuthenticatedUser user, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .claim(EMAIL_CLAIM, user.email())
                .claim(ROLES_CLAIM, List.of(user.role().name()))
                .build();
        JwsHeader header = JwsHeader.with(JwtConfiguration.ALGORITHM).type(JwtConfiguration.ACCESS_TOKEN_TYPE).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }
}
