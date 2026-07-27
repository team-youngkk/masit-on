package com.masiton.security.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.masiton.security.domain.model.AdminAccount;

/**
 * Application이 AdminAccount 영속성에 요구하는 계약이다.
 * Infrastructure Adapter가 구현하며, Application은 이 인터페이스만 의존한다.
 */
public interface AdminAccountRepositoryPort {

    AdminAccount save(AdminAccount adminAccount);

    Optional<AdminAccount> findById(UUID id);
}
