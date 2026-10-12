package com.stockforecast.identity.infrastructure.security;

import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.Role;
import com.stockforecast.identity.application.AccountAdministrationService;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Request authorization rules beyond plain role checks.
 */
@Component
class AccessPolicies {

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private final AccountAdministrationService accounts;

    AccessPolicies(AccountAdministrationService accounts) {
        this.accounts = accounts;
    }

    /**
     * Admin APIs: the token must carry the ADMIN role <em>and</em> the account must still be an active administrator
     * in the database. Access tokens live for minutes; this closes the window in which a demoted, disabled or
     * deleted administrator could keep using the console.
     */
    AuthorizationManager<RequestAuthorizationContext> activeAdministrator() {
        return (authenticationSupplier, context) -> {
            Authentication authentication = authenticationSupplier.get();
            if (!isFullyAuthorized(authentication) || !hasAuthority(authentication, Role.ADMIN.authority())
                    || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
                return new AuthorizationDecision(false);
            }
            return new AuthorizationDecision(accounts.isActiveAdministrator(user.id()));
        };
    }

    /**
     * Everything else requires authentication; a user who must replace a temporary password may only do that.
     */
    AuthorizationManager<RequestAuthorizationContext> authenticatedWithoutPendingPasswordChange() {
        return (authenticationSupplier, context) ->
                new AuthorizationDecision(isFullyAuthorized(authenticationSupplier.get()));
    }

    static boolean isPasswordChangePending(Authentication authentication) {
        return authentication != null && hasAuthority(authentication,
                AuthenticatedUserJwtConverter.PASSWORD_CHANGE_REQUIRED_AUTHORITY);
    }

    private static boolean isFullyAuthorized(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !TRUST_RESOLVER.isAnonymous(authentication) && !isPasswordChangePending(authentication);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
