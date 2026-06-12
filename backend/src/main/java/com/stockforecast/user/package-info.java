/**
 * User profile: display name and personal preferences. Accounts and credentials are owned by the identity
 * module; this module reacts to {@link com.stockforecast.identity.UserRegisteredEvent}.
 */
@ApplicationModule(displayName = "User Profile")
package com.stockforecast.user;

import org.springframework.modulith.ApplicationModule;
