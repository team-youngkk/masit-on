package com.masiton.creator.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.domain.model.Creator;

@Component
class CreatorPersistenceAdapter implements CreatorRepositoryPort {

    private final SpringDataCreatorRepository springDataCreatorRepository;

    public CreatorPersistenceAdapter(SpringDataCreatorRepository springDataCreatorRepository) {
        this.springDataCreatorRepository = springDataCreatorRepository;
    }

    @Override
    public Creator save(Creator creator) {
        CreatorJpaEntity savedEntity = springDataCreatorRepository.save(CreatorMapper.toEntity(creator));
        return CreatorMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Creator> findById(UUID id) {
        return springDataCreatorRepository.findById(id).map(CreatorMapper::toDomain);
    }
}
