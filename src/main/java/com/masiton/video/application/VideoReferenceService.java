package com.masiton.video.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.masiton.video.application.port.in.FindVideoReferenceUseCase;
import com.masiton.video.application.port.in.ResolveVideoCreatorUseCase;
import com.masiton.video.application.port.out.VideoRepositoryPort;
import com.masiton.video.domain.model.ExternalAvailabilityStatus;
import com.masiton.video.domain.model.LifecycleStatus;
import com.masiton.video.domain.model.PublicationStatus;
import com.masiton.video.domain.model.Video;

@Service
class VideoReferenceService implements FindVideoReferenceUseCase, ResolveVideoCreatorUseCase {

    private final VideoRepositoryPort videoRepository;

    VideoReferenceService(VideoRepositoryPort videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Override
    public Optional<VideoReference> findVideoReference(UUID videoId) {
        return videoRepository.findById(videoId).map(this::referenceOf);
    }

    @Override
    public VideoReference resolveCreator(UUID videoId, UUID creatorId) {
        Video video = videoRepository.assignCreatorIfUnassigned(videoId, creatorId)
                .or(() -> videoRepository.findById(videoId))
                .orElseThrow(() -> new IllegalStateException("Video reference disappeared during registration."));
        return referenceOf(video);
    }

    private VideoReference referenceOf(Video video) {
        return new VideoReference(
                video.getId(),
                video.getCreatorId(),
                video.getPublisherExternalChannelId(),
                video.getPublicationStatus() == PublicationStatus.PUBLIC
                        && video.getLifecycleStatus() == LifecycleStatus.ACTIVE,
                video.getExternalAvailabilityStatus() == ExternalAvailabilityStatus.AVAILABLE);
    }
}
