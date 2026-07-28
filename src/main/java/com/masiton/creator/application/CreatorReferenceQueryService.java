package com.masiton.creator.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;
import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;
import com.masiton.creator.domain.model.LifecycleStatus;
import com.masiton.creator.domain.model.PublicationStatus;

@Service
@Transactional(readOnly = true)
class CreatorReferenceQueryService implements FindCreatorReferenceUseCase {

    private final CreatorRepositoryPort creatorRepository;

    CreatorReferenceQueryService(CreatorRepositoryPort creatorRepository) {
        this.creatorRepository = creatorRepository;
    }

    @Override
    public Optional<CreatorReference> findCreatorReference(UUID creatorId) {
        return creatorRepository.findByIdForUpdate(creatorId)
                .map(creator -> new CreatorReference(
                        creator.getId(),
                        creator.getExternalChannelId(),
                        creator.getPublicationStatus() == PublicationStatus.PUBLIC
                                && creator.getLifecycleStatus() == LifecycleStatus.ACTIVE,
                        creator.getExternalAvailabilityStatus() == ExternalAvailabilityStatus.AVAILABLE));
    }
}
