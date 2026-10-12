package com.stockforecast.stock.infrastructure.persistence;

import com.stockforecast.stock.domain.Stock;
import com.stockforecast.stock.domain.StockBrowser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
class JpaStockBrowser implements StockBrowser {

    private final SpringDataStockRepository repository;
    private final EntityManager entityManager;

    JpaStockBrowser(SpringDataStockRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public StockPage browse(String sector, int page, int size) {
        PageRequest request = PageRequest.of(page, size);
        Page<Stock> result = sector == null
                ? repository.findByActiveTrueOrderBySymbolAsc(request)
                : repository.findByActiveTrueAndSectorOrderBySymbolAsc(sector, request);
        return new StockPage(result.getContent(), page, size, result.getTotalElements());
    }

    @Override
    public List<String> findSectors() {
        return repository.findDistinctSectors();
    }

    @Override
    @Transactional(readOnly = true)
    public StockPage searchAll(String upperCaseFragment, Boolean active, int page, int size) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Stock> query = builder.createQuery(Stock.class);
        Root<Stock> stock = query.from(Stock.class);
        query.select(stock).where(predicates(builder, stock, upperCaseFragment, active))
                .orderBy(builder.asc(stock.get("symbol")));
        List<Stock> content = entityManager.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        CriteriaQuery<Long> count = builder.createQuery(Long.class);
        Root<Stock> counted = count.from(Stock.class);
        count.select(builder.count(counted)).where(predicates(builder, counted, upperCaseFragment, active));
        return new StockPage(content, page, size, entityManager.createQuery(count).getSingleResult());
    }

    private static Predicate[] predicates(CriteriaBuilder builder, Root<Stock> stock, String fragment, Boolean active) {
        List<Predicate> predicates = new ArrayList<>();
        if (fragment != null) {
            String pattern = "%" + fragment.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            predicates.add(builder.or(
                    builder.like(stock.get("symbol"), pattern, '\\'),
                    builder.like(builder.upper(stock.get("companyName")), pattern, '\\')));
        }
        if (active != null) {
            predicates.add(builder.equal(stock.get("active"), active));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
