package com.stockforecast.identity;

/**
 * Whether an account may sign in. Only {@code ACTIVE} accounts can authenticate; the others keep their data.
 */
public enum AccountStatus {
    ACTIVE,
    /** Switched off by an administrator; can be re-enabled. */
    DISABLED,
    /** Blocked for security reasons (e.g. suspected compromise) until an administrator unlocks it. */
    LOCKED
}
