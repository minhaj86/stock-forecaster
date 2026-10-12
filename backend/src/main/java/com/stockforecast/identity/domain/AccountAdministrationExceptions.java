package com.stockforecast.identity.domain;

import com.stockforecast.shared.domain.DomainException;
import com.stockforecast.shared.domain.ErrorCategory;

/**
 * Business failures of account administration. Messages are shown to administrators as they are.
 */
public final class AccountAdministrationExceptions {

    private AccountAdministrationExceptions() {
    }

    public static class AccountNotFoundException extends DomainException {
        public AccountNotFoundException() {
            super("USER_NOT_FOUND", ErrorCategory.NOT_FOUND, "User not found");
        }
    }

    /** Removing, disabling, locking or demoting the last active administrator would lock everyone out. */
    public static class LastAdministratorException extends DomainException {
        public LastAdministratorException() {
            super("LAST_ADMINISTRATOR", ErrorCategory.CONFLICT,
                    "At least one active administrator must remain. Promote another administrator first.");
        }
    }

    /** Administrators cannot lock themselves out of the console they are using. */
    public static class SelfAdministrationException extends DomainException {

        private SelfAdministrationException(String message) {
            super("SELF_ADMINISTRATION_NOT_ALLOWED", ErrorCategory.CONFLICT, message);
        }

        public static SelfAdministrationException deletion() {
            return new SelfAdministrationException(
                    "You cannot delete your own account while signed in. Ask another administrator.");
        }

        public static SelfAdministrationException demotion() {
            return new SelfAdministrationException(
                    "You cannot remove your own administrator role. Ask another administrator.");
        }

        public static SelfAdministrationException deactivation() {
            return new SelfAdministrationException(
                    "You cannot disable or lock your own account. Ask another administrator.");
        }
    }

    public static class InvalidCurrentPasswordException extends DomainException {
        public InvalidCurrentPasswordException() {
            super("INVALID_CURRENT_PASSWORD", ErrorCategory.VALIDATION, "The current password is incorrect");
        }
    }

    public static class PasswordUnchangedException extends DomainException {
        public PasswordUnchangedException() {
            super("PASSWORD_UNCHANGED", ErrorCategory.VALIDATION,
                    "The new password must be different from the current one");
        }
    }
}
