package com.stockforecast.identity.domain;

public enum RevocationReason {
    /** Exchanged for a successor token during a normal refresh. */
    ROTATED,
    /** The user signed out. */
    LOGOUT,
    /** A rotated token was presented again: the whole session is treated as compromised. */
    REUSE_DETECTED,
    /** The owning account is no longer allowed to sign in (disabled, locked or deleted). */
    ACCOUNT_DISABLED,
    /** The user chose a new password: every other session ends. */
    PASSWORD_CHANGED,
    /** An administrator changed the account (status, password reset). */
    ADMIN_ACTION
}
