package com.stockforecast.identity.infrastructure.security;

import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.Role;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Maps a validated access token to an {@link AuthenticatedUserAuthenticationToken}. Structurally invalid
 * claims are reported as an invalid bearer token (HTTP 401), never as a server error.
 */
@Component
class AuthenticatedUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Role role = parseRole(jwt.getClaimAsStringList(JwtAccessTokenIssuer.ROLES_CLAIM));
        AuthenticatedUser user = new AuthenticatedUser(parseSubject(jwt.getSubject()),
                requireClaim(jwt.getClaimAsString(JwtAccessTokenIssuer.EMAIL_CLAIM)), role);
        return new AuthenticatedUserAuthenticationToken(jwt, user,
                List.of(new SimpleGrantedAuthority(role.authority())));
    }

    private static UUID parseSubject(String subject) {
        try {
            return UUID.fromString(requireClaim(subject));
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Access token subject is malformed", e);
        }
    }

    private static Role parseRole(List<String> roles) {
        if (roles == null || roles.size() != 1) {
            throw new InvalidBearerTokenException("Access token must carry exactly one role");
        }
        try {
            return Role.valueOf(roles.getFirst());
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Access token carries an unknown role", e);
        }
    }

    private static String requireClaim(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidBearerTokenException("Access token is missing required claims");
        }
        return value;
    }
}
