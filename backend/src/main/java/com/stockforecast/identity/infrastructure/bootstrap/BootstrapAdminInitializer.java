package com.stockforecast.identity.infrastructure.bootstrap;

import com.stockforecast.identity.Role;
import com.stockforecast.identity.application.RegistrationService;
import com.stockforecast.identity.domain.EmailAddress;
import com.stockforecast.identity.domain.EmailAlreadyRegisteredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Idempotently provisions the configured bootstrap administrator. Safe with several instances starting at
 * once: the unique e-mail constraint lets exactly one of them win.
 */
@Component
class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final RegistrationService registrationService;

    BootstrapAdminInitializer(BootstrapAdminProperties properties, RegistrationService registrationService) {
        this.properties = properties;
        this.registrationService = registrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }
        EmailAddress email = EmailAddress.of(properties.email());
        try {
            registrationService.createAccount(email, properties.password(), properties.displayName(), Role.ADMIN);
            log.info("Bootstrap administrator account created for {}", email);
        } catch (EmailAlreadyRegisteredException | DataIntegrityViolationException e) {
            log.info("Bootstrap administrator {} already exists; leaving it unchanged", email);
        }
    }
}
