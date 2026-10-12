/**
 * User profile: display name and personal preferences. Accounts and credentials are owned by the identity
 * module; this module reacts to {@link com.stockforecast.identity.UserRegisteredEvent} (creates the profile) and
 * {@link com.stockforecast.identity.UserAccountDeleted} (deletes it).
 *
 * <p>Published API: {@link com.stockforecast.user.UserProfiles} (administrative profile access).
 */
@ApplicationModule(displayName = "User Profile")
package com.stockforecast.user;

import org.springframework.modulith.ApplicationModule;
