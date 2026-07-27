package com.masiton.visit.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.visit.application.port.out.VisitRepositoryPort;
import com.masiton.visit.domain.model.Visit;

/**
 * VisitRepositoryPort의 JPA 구현체다. JPA Entity와 도메인 모델 변환은 VisitMapper에 위임한다.
 */
@Component
class VisitPersistenceAdapter implements VisitRepositoryPort {

    private final SpringDataVisitRepository springDataVisitRepository;

    VisitPersistenceAdapter(SpringDataVisitRepository springDataVisitRepository) {
        this.springDataVisitRepository = springDataVisitRepository;
    }

    @Override
    public Visit save(Visit visit) {
        VisitJpaEntity savedEntity = springDataVisitRepository.save(VisitMapper.toEntity(visit));
        return VisitMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Visit> findById(UUID id) {
        return springDataVisitRepository.findById(id)
                .map(VisitMapper::toDomain);
    }
}
