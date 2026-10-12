package com.stockforecast.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A {@link TransactionTemplate} that just runs the callback, for unit tests of services that demarcate transactions
 * programmatically.
 */
public final class NoOpTransactions {

    private NoOpTransactions() {
    }

    public static TransactionTemplate template() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
                // nothing to commit
            }

            @Override
            public void rollback(TransactionStatus status) {
                // nothing to roll back
            }
        });
    }
}
