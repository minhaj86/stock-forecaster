package com.stockforecast.marketdata.infrastructure.persistence;

import com.stockforecast.marketdata.domain.MarketDataJob;
import com.stockforecast.marketdata.domain.MarketDataJobSearch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
class JpaMarketDataJobSearch implements MarketDataJobSearch {

    private final EntityManager entityManager;

    JpaMarketDataJobSearch(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public SearchPage search(Criteria criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<MarketDataJob> query = builder.createQuery(MarketDataJob.class);
        Root<MarketDataJob> job = query.from(MarketDataJob.class);
        query.select(job).where(predicates(builder, job, criteria))
                .orderBy(builder.desc(job.get("requestedAt")), builder.desc(job.get("id")));
        List<MarketDataJob> content = entityManager.createQuery(query)
                .setFirstResult(criteria.page() * criteria.size())
                .setMaxResults(criteria.size())
                .getResultList();

        CriteriaQuery<Long> count = builder.createQuery(Long.class);
        Root<MarketDataJob> counted = count.from(MarketDataJob.class);
        count.select(builder.count(counted)).where(predicates(builder, counted, criteria));
        return new SearchPage(content, entityManager.createQuery(count).getSingleResult());
    }

    private static Predicate[] predicates(CriteriaBuilder builder, Root<MarketDataJob> job, Criteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        if (criteria.status() != null) {
            predicates.add(builder.equal(job.get("status"), criteria.status()));
        }
        if (criteria.type() != null) {
            predicates.add(builder.equal(job.get("type"), criteria.type()));
        }
        if (criteria.trigger() != null) {
            predicates.add(builder.equal(job.get("trigger"), criteria.trigger()));
        }
        if (criteria.stockId() != null) {
            predicates.add(builder.equal(job.get("stockId"), criteria.stockId()));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
