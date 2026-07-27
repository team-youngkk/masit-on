package com.masiton.security.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.security.application.port.out.AdminAccountRepositoryPort;
import com.masiton.security.domain.model.AdminAccount;

/**
 * AdminAccountRepositoryPort의 JPA 구현체다. JPA Entity와 도메인 모델 변환은
 * AdminAccountMapper에 위임한다.
 */
@Component
class AdminAccountPersistenceAdapter implements AdminAccountRepositoryPort {

    private final SpringDataAdminAccountRepository springDataAdminAccountRepository;

    AdminAccountPersistenceAdapter(SpringDataAdminAccountRepository springDataAdminAccountRepository) {
        this.springDataAdminAccountRepository = springDataAdminAccountRepository;
    }

    @Override
    public AdminAccount save(AdminAccount adminAccount) {
        AdminAccountJpaEntity savedEntity =
                springDataAdminAccountRepository.save(AdminAccountMapper.toEntity(adminAccount));
        return AdminAccountMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<AdminAccount> findById(UUID id) {
        return springDataAdminAccountRepository.findById(id)
                .map(AdminAccountMapper::toDomain);
    }
}
