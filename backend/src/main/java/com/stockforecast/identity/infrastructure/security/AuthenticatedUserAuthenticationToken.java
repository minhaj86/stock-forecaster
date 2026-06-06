package com.stockforecast.identity.infrastructure.security;

import com.stockforecast.identity.AuthenticatedUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.util.Collection;
import java.util.Map;

/**
 * Authentication whose principal is the module-neutral {@link AuthenticatedUser} instead of the raw JWT, so
 * controllers in other modules never depend on token internals.
 */
final class AuthenticatedUserAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

    AuthenticatedUserAuthenticationToken(Jwt jwt, AuthenticatedUser principal,
                                         Collection<? extends GrantedAuthority> authorities) {
        super(jwt, principal, jwt, authorities);
        setAuthenticated(true);
    }

    @Override
    public Map<String, Object> getTokenAttributes() {
        return getToken().getClaims();
    }

    @Override
    public String getName() {
        return ((AuthenticatedUser) getPrincipal()).id().toString();
    }
}
