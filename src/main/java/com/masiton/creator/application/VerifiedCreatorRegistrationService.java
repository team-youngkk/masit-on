package com.masiton.creator.application;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.creator.application.port.in.VerifiedCreatorRegistrationUseCase;
import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.domain.model.Creator;
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;
import com.masiton.creator.domain.model.LifecycleStatus;
import com.masiton.creator.domain.model.PublicationStatus;

/** 자동 확정 orchestration의 트랜잭션에 참여하는 Creator 등록 서비스다. */
@Service
public class VerifiedCreatorRegistrationService implements VerifiedCreatorRegistrationUseCase {

    private final CreatorRepositoryPort creatorRepository;

    public VerifiedCreatorRegistrationService(CreatorRepositoryPort creatorRepository) {
        this.creatorRepository = creatorRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistrationResult register(VerifiedCreatorCommand command) {
        requireCommand(command);
        return creatorRepository.findByExternalChannelId(command.externalChannelId())
                .map(existing -> new RegistrationResult(existing.getId(), false))
                .orElseGet(() -> insert(command));
    }

    private RegistrationResult insert(VerifiedCreatorCommand command) {
        Creator creator = new Creator(
                UUID.randomUUID(),
                command.externalChannelId(),
                command.channelName(),
                command.channelUrl(),
                null,
                null,
                null,
                PublicationStatus.PUBLIC,
                LifecycleStatus.ACTIVE,
                ExternalAvailabilityStatus.AVAILABLE,
                OffsetDateTime.now(),
                null,
                null,
                null);
        return creatorRepository.insertIfAbsent(creator)
                .map(saved -> new RegistrationResult(saved.getId(), true))
                .orElseGet(() -> creatorRepository.findByExternalChannelId(command.externalChannelId())
                        .map(existing -> new RegistrationResult(existing.getId(), false))
                        .orElseThrow(() -> new IllegalStateException("Concurrent creator result was not found.")));
    }

    private void requireCommand(VerifiedCreatorCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.externalChannelId(), "externalChannelId");
        requireText(command.channelName(), "channelName");
        requireText(command.channelUrl(), "channelUrl");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
    }
}
