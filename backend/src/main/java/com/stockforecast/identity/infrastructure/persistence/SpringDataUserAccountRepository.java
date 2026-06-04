package com.stockforecast.identity.infrastructure.persistence;

import com.stockforecast.identity.domain.UserAccount;
import com.stockforecast.identity.domain.UserAccountRepository;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/**
 * Spring Data implements the domain port directly: every port method is either a CRUD method of the
 * {@link Repository} contract or a derived query, so no hand-written adapter is needed and the domain stays
 * free of Spring Data types.
 */
interface SpringDataUserAccountRepository extends Repository<UserAccount, UUID>, UserAccountRepository {
}
