package com.stockforecast.identity.infrastructure.persistence;

import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data implements the domain port directly: every port method is either a CRUD method of the
 * {@link Repository} contract or a derived query, so no hand-written adapter is needed and the domain stays
 * free of Spring Data types.
 */
interface SpringDataUserAccountRepository extends Repository<UserAccount, UUID>, UserAccountRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from UserAccount a
             where a.role = com.stockforecast.identity.Role.ADMIN
               and a.status = com.stockforecast.identity.AccountStatus.ACTIVE
             order by a.id""")
    List<UserAccount> lockActiveAdministrators();
}
