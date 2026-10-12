/**
 * Administration: the operational back office behind {@code /api/v1/admin/**} — dashboard, user, stock and
 * market-data administration, provider monitoring and the audit log of administrative actions.
 *
 * <p>The module orchestrates the published APIs of the owning modules (identity, user, watchlist, stock, marketdata);
 * their business rules (e.g. "the last administrator cannot be demoted", "repopulation never loses data") stay
 * there. Every administrative change is audited in the same transaction. No other module depends on this one.
 */
@ApplicationModule(displayName = "Administration")
package com.stockforecast.admin;

import org.springframework.modulith.ApplicationModule;
