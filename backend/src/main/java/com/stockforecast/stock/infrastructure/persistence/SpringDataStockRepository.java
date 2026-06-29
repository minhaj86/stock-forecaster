package com.stockforecast.stock.infrastructure.persistence;

import com.stockforecast.stock.domain.Stock;
import com.stockforecast.stock.domain.StockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

interface SpringDataStockRepository extends Repository<Stock, UUID>, StockRepository {

    @Override
    @Query(value = """
            SELECT s.* FROM stocks s
             WHERE s.active
               AND (s.symbol LIKE :term || '%'
                    OR upper(s.company_name) LIKE '%' || :term || '%'
                    OR upper(coalesce(s.aliases, '')) LIKE '%' || :term || '%')
             ORDER BY CASE
                        WHEN s.symbol = :term THEN 0
                        WHEN s.symbol LIKE :term || '%' THEN 1
                        WHEN upper(s.company_name) LIKE :term || '%'
                          OR upper(coalesce(s.aliases, '')) LIKE :term || '%' THEN 2
                        ELSE 3
                      END,
                      length(s.symbol), s.symbol
             LIMIT :limit""", nativeQuery = true)
    List<Stock> search(String term, int limit);

    Page<Stock> findByActiveTrueOrderBySymbolAsc(Pageable pageable);

    Page<Stock> findByActiveTrueAndSectorOrderBySymbolAsc(String sector, Pageable pageable);

    @Query("select distinct s.sector from Stock s where s.active = true and s.sector is not null order by s.sector")
    List<String> findDistinctSectors();
}
