package com.stockforecast.admin.api;

import com.stockforecast.identity.AccountAdministration;
import com.stockforecast.identity.AccountAdministration.AccountChanges;
import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.Role;
import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.support.AdminTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The "at least one active administrator" rule needs a database whose administrators the test controls, so this class
 * runs in its own application context (and therefore its own, empty database).
 */
@IntegrationTest
@TestPropertySource(properties = "spring.application.name=admin-safeguards-it")
class AdminSafeguardsIT {

    @Autowired
    private AdminTestClient admins;
    @Autowired
    private AccountAdministration accounts;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theLastActiveAdministratorCannotBeRemovedEvenUnderConcurrentDemotion() throws Exception {
        Session first = admins.newAdmin();
        Session second = admins.newAdmin();

        // Two administrators demote each other at the same moment: without the row lock both could succeed and lock
        // everyone out. Exactly one demotion must win.
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> firstDemotesSecond = CompletableFuture.runAsync(() ->
                demote(first.userId(), second.userId(), start, failures));
        CompletableFuture<Void> secondDemotesFirst = CompletableFuture.runAsync(() ->
                demote(second.userId(), first.userId(), start, failures));
        start.countDown();
        CompletableFuture.allOf(firstDemotesSecond, secondDemotesFirst).get(30, TimeUnit.SECONDS);

        assertThat(failures).hasSize(1).first().isInstanceOfSatisfying(DomainException.class, error ->
                assertThat(error.code()).isEqualTo("LAST_ADMINISTRATOR"));
        actAsAdministrator(UUID.randomUUID());
        long remainingAdministrators = List.of(first.userId(), second.userId()).stream()
                .map(accounts::get).filter(account -> account.role() == Role.ADMIN).count();
        assertThat(remainingAdministrators).isEqualTo(1);

        UUID lastAdministrator = accounts.get(first.userId()).role() == Role.ADMIN ? first.userId() : second.userId();
        UUID someoneElse = UUID.randomUUID();
        assertThatThrownBy(() -> accounts.delete(someoneElse, lastAdministrator))
                .isInstanceOfSatisfying(DomainException.class, error ->
                        assertThat(error.code()).isEqualTo("LAST_ADMINISTRATOR"));
        assertThatThrownBy(() -> accounts.update(someoneElse, lastAdministrator,
                new AccountChanges(null, null, AccountStatus.DISABLED)))
                .isInstanceOfSatisfying(DomainException.class, error ->
                        assertThat(error.code()).isEqualTo("LAST_ADMINISTRATOR"));
        assertThat(accounts.get(lastAdministrator).isActiveAdministrator()).isTrue();
    }

    private void demote(UUID actor, UUID target, CountDownLatch start, List<Throwable> failures) {
        actAsAdministrator(actor);
        try {
            start.await();
            accounts.update(actor, target, new AccountChanges(null, Role.USER, null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failures.add(e);
        } catch (DomainException e) {
            failures.add(e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void actAsAdministrator(UUID id) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(id,
                null, List.of(new SimpleGrantedAuthority(Role.ADMIN.authority()))));
    }
}
