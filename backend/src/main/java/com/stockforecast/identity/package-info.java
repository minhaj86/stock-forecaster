/**
 * Identity &amp; Access: user accounts, credentials, login sessions (rotating refresh tokens), JWT access tokens
 * and the HTTP security configuration.
 *
 * <p>Published API (types in this package): {@link com.stockforecast.identity.AuthenticatedUser},
 * {@link com.stockforecast.identity.CurrentUser}, {@link com.stockforecast.identity.Role} and
 * {@link com.stockforecast.identity.UserRegisteredEvent}. Sub-packages are internal to the module.
 */
@ApplicationModule(displayName = "Identity & Access")
package com.stockforecast.identity;

import org.springframework.modulith.ApplicationModule;
