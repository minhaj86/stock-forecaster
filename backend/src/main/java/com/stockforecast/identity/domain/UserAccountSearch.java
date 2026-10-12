package com.stockforecast.identity.domain;

import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.Role;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Filtered, paged account search for the admin console (newest first).
 */
public interface UserAccountSearch {

    SearchPage search(Criteria criteria);

    /**
     * @param emailFragment   lower-cased "contains" match on the e-mail address, or {@code null}
     * @param alsoMatchingIds ids that match the text through other data (OR-ed with the e-mail match)
     * @param createdBefore   exclusive
     */
    record Criteria(String emailFragment, Collection<UUID> alsoMatchingIds, Role role, AccountStatus status,
                    Instant createdFrom, Instant createdBefore, int page, int size) {
    }

    record SearchPage(List<UserAccount> content, long totalElements) {
    }
}
