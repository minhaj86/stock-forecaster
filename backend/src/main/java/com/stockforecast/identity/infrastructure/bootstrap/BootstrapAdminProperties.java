package com.stockforecast.identity.infrastructure.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional first administrator, created at startup when both values are set ({@code ADMIN_EMAIL},
 * {@code ADMIN_PASSWORD}). Existing accounts are never modified.
 */
@ConfigurationProperties("app.security.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password,
                                       @DefaultValue("Administrator") String displayName) {

    boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[email=" + email + ", password=***, displayName=" + displayName + "]";
    }
}
