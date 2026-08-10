package com.masiton.video.application;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.video.application.port.in.VerifiedVideoRegistrationUseCase;
import com.masiton.video.application.port.out.VideoRepositoryPort;
import com.masiton.video.domain.model.ExternalAvailabilityStatus;
import com.masiton.video.domain.model.LifecycleStatus;
import com.masiton.video.domain.model.PublicationStatus;
import com.masiton.video.domain.model.Video;

/** 자동 확정 orchestration의 트랜잭션에 참여하는 Video 등록 서비스다. */
@Service
public class VerifiedVideoRegistrationService implements VerifiedVideoRegistrationUseCase {

    private final VideoRepositoryPort videoRepository;

    public VerifiedVideoRegistrationService(VideoRepositoryPort videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistrationResult register(VerifiedVideoCommand command) {
        requireCommand(command);
        return videoRepository.findByExternalVideoId(command.externalVideoId())
                .map(existing -> existingWithCreator(existing, command.creatorId()))
                .orElseGet(() -> insert(command));
    }

    private RegistrationResult existingWithCreator(Video existing, UUID creatorId) {
        if (existing.getCreatorId() == null) {
            return videoRepository.assignCreatorIfUnassigned(existing.getId(), creatorId)
                    .map(updated -> new RegistrationResult(updated.getId(), false))
                    .orElseGet(() -> videoRepository.findById(existing.getId())
                            .filter(video -> creatorId.equals(video.getCreatorId()))
                            .map(video -> new RegistrationResult(video.getId(), false))
                            .orElseThrow(() -> new IllegalStateException("Video creator assignment conflicted.")));
        }
        if (!creatorId.equals(existing.getCreatorId())) {
            throw new IllegalArgumentException("Video is already assigned to another creator.");
        }
        return new RegistrationResult(existing.getId(), false);
    }

    private RegistrationResult insert(VerifiedVideoCommand command) {
        Video video = new Video(
                UUID.randomUUID(),
                command.creatorId(),
                command.externalVideoId(),
                command.publisherExternalChannelId(),
                command.title(),
                command.sourceUrl(),
                command.thumbnailUrl(),
                command.publishedAt(),
                PublicationStatus.PUBLIC,
                LifecycleStatus.ACTIVE,
                ExternalAvailabilityStatus.AVAILABLE,
                command.checkedAt() == null ? OffsetDateTime.now() : command.checkedAt(),
                null,
                null,
                null);
        return videoRepository.insertIfAbsent(video)
                .map(saved -> new RegistrationResult(saved.getId(), true))
                .orElseGet(() -> videoRepository.findByExternalVideoId(command.externalVideoId())
                        .map(existing -> existingWithCreator(existing, command.creatorId()))
                        .orElseThrow(() -> new IllegalStateException("Concurrent video result was not found.")));
    }

    private void requireCommand(VerifiedVideoCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.creatorId(), "creatorId");
        requireText(command.externalVideoId(), "externalVideoId");
        requireText(command.publisherExternalChannelId(), "publisherExternalChannelId");
        requireText(command.title(), "title");
        requireText(command.sourceUrl(), "sourceUrl");
        requireText(command.thumbnailUrl(), "thumbnailUrl");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
    }
}
