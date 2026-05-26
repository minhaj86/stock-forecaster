package com.stockforecast.identity;

/**
 * Coarse-grained authorization roles. Fine-grained ownership checks (e.g. "is this my watchlist") are done
 * in the owning module against the authenticated user's id, never against client-supplied ids.
 */
public enum Role {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
