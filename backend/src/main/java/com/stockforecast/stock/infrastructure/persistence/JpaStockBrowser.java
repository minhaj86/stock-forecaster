package com.stockforecast.stock.infrastructure.persistence;

import com.stockforecast.stock.domain.Stock;
import com.stockforecast.stock.domain.StockBrowser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class JpaStockBrowser implements StockBrowser {

    private final SpringDataStockRepository repository;

    JpaStockBrowser(SpringDataStockRepository repository) {
        this.repository = repository;
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
}
