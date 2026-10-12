package com.stockforecast.identity.infrastructure.persistence;

import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountSearch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Criteria query with only the filters that are set, so no untyped {@code NULL} parameters reach PostgreSQL.
 */
@Component
class JpaUserAccountSearch implements UserAccountSearch {

    private final EntityManager entityManager;

    JpaUserAccountSearch(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public SearchPage search(Criteria criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<UserAccount> query = builder.createQuery(UserAccount.class);
        Root<UserAccount> account = query.from(UserAccount.class);
        query.select(account)
                .where(predicates(builder, account, criteria))
                .orderBy(builder.desc(account.get("createdAt")), builder.asc(account.get("id")));
        List<UserAccount> content = entityManager.createQuery(query)
                .setFirstResult(criteria.page() * criteria.size())
                .setMaxResults(criteria.size())
                .getResultList();

        CriteriaQuery<Long> count = builder.createQuery(Long.class);
        Root<UserAccount> counted = count.from(UserAccount.class);
        count.select(builder.count(counted)).where(predicates(builder, counted, criteria));
        return new SearchPage(content, entityManager.createQuery(count).getSingleResult());
    }

    private static Predicate[] predicates(CriteriaBuilder builder, Root<UserAccount> account, Criteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        if (criteria.emailFragment() != null) {
            Predicate emailMatches = builder.like(account.get("email"),
                    "%" + escapeLike(criteria.emailFragment()) + "%", '\\');
            predicates.add(criteria.alsoMatchingIds().isEmpty() ? emailMatches
                    : builder.or(emailMatches, account.get("id").in(criteria.alsoMatchingIds())));
        }
        if (criteria.role() != null) {
            predicates.add(builder.equal(account.get("role"), criteria.role()));
        }
        if (criteria.status() != null) {
            predicates.add(builder.equal(account.get("status"), criteria.status()));
        }
        if (criteria.createdFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(account.<Instant>get("createdAt"), criteria.createdFrom()));
        }
        if (criteria.createdBefore() != null) {
            predicates.add(builder.lessThan(account.<Instant>get("createdAt"), criteria.createdBefore()));
        }
        return predicates.toArray(Predicate[]::new);
    }

    static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
