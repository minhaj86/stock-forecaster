package com.stockforecast.identity.infrastructure.security;

import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserJwtConverterTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final AuthenticatedUserJwtConverter converter = new AuthenticatedUserJwtConverter();

    @Test
    void mapsClaimsToTheAuthenticatedUserPrincipalAndRoleAuthority() {
        AbstractAuthenticationToken authentication = converter.convert(jwt(builder -> { }));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal())
                .isEqualTo(new AuthenticatedUser(USER_ID, "ada@example.com", Role.ADMIN));
        assertThat(authentication.getName()).isEqualTo(USER_ID.toString());
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void rejectsTokensWithMissingOrMalformedClaims() {
        assertInvalid(builder -> builder.subject("not-a-uuid"));
        assertInvalid(builder -> builder.claims(claims -> claims.remove("email")));
        assertInvalid(builder -> builder.claims(claims -> claims.remove("roles")));
        assertInvalid(builder -> builder.claim("roles", List.of("SUPERUSER")));
        assertInvalid(builder -> builder.claim("roles", List.of("USER", "ADMIN")));
    }

    private void assertInvalid(Consumer<Jwt.Builder> customizer) {
        Jwt jwt = jwt(customizer);
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
    }

    private static Jwt jwt(Consumer<Jwt.Builder> customizer) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .claim("email", "ada@example.com")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        customizer.accept(builder);
        return builder.build();
    }
}
