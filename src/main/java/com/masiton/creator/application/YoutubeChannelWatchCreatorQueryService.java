package com.masiton.creator.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.creator.application.port.in.FindYoutubeChannelWatchCreatorsUseCase;
import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;
import com.masiton.creator.domain.model.LifecycleStatus;
import com.masiton.creator.domain.model.PublicationStatus;

@Service
@Transactional(readOnly = true)
class YoutubeChannelWatchCreatorQueryService implements FindYoutubeChannelWatchCreatorsUseCase {

    private final CreatorRepositoryPort creatorRepository;

    YoutubeChannelWatchCreatorQueryService(CreatorRepositoryPort creatorRepository) {
        this.creatorRepository = creatorRepository;
    }

    @Override
    public List<CreatorReference> findAll() {
        return creatorRepository.findAllForYoutubeChannelWatch().stream()
                .map(creator -> new CreatorReference(
                        creator.getId(),
                        creator.getChannelName(),
                        creator.getExternalChannelId(),
                        creator.getPublicationStatus() == PublicationStatus.PUBLIC
                                && creator.getLifecycleStatus() == LifecycleStatus.ACTIVE,
                        creator.getExternalAvailabilityStatus() == ExternalAvailabilityStatus.AVAILABLE))
                .toList();
    }
}
