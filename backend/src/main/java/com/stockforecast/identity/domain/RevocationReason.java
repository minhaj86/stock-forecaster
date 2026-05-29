package com.stockforecast.identity.domain;

public enum RevocationReason {
    /** Exchanged for a successor token during a normal refresh. */
    ROTATED,
    /** The user signed out. */
    LOGOUT,
    /** A rotated token was presented again: the whole session is treated as compromised. */
    REUSE_DETECTED,
    /** The owning account is no longer allowed to sign in. */
    ACCOUNT_DISABLED
}
