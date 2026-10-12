package com.stockforecast.identity.infrastructure.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional first administrator, created at startup when both values are set ({@code ADMIN_EMAIL},
 * {@code ADMIN_PASSWORD}). Existing accounts are never modified.
 *
 * @param requirePasswordChange treat the configured password as a one-time bootstrap credential: the administrator
 *                              must choose a new password at the first sign-in ({@code ADMIN_REQUIRE_PASSWORD_CHANGE})
 */
@ConfigurationProperties("app.security.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password,
                                       @DefaultValue("Administrator") String displayName,
                                       @DefaultValue("true") boolean requirePasswordChange) {

    boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[email=" + email + ", password=***, displayName=" + displayName + "]";
    }
}
