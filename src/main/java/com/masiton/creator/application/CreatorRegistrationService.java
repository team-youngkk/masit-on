package com.masiton.creator.application;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.creator.application.port.in.CreatorRegistrationUseCase;
import com.masiton.creator.application.port.out.ChannelVerificationPort;
import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.application.port.out.VerifiedChannel;
import com.masiton.creator.domain.model.Creator;
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;
import com.masiton.creator.domain.model.LifecycleStatus;
import com.masiton.creator.domain.model.PublicationStatus;
import com.masiton.security.application.AcquiredConfirmationToken;
import com.masiton.security.application.ConfirmationTokenIssueCommand;
import com.masiton.security.application.IssuedConfirmationToken;
import com.masiton.security.application.port.in.ConfirmationTokenUseCase;
import com.masiton.security.domain.model.ConfirmationTokenResourceType;
import com.masiton.security.domain.model.ConfirmationTokenStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CreatorRegistrationService implements CreatorRegistrationUseCase {
    private static final short SNAPSHOT_SCHEMA_VERSION = 1;
    private final ChannelVerificationPort channelVerificationPort;
    private final CreatorRepositoryPort creatorRepository;
    private final ConfirmationTokenUseCase confirmationTokenUseCase;
    private final ObjectMapper objectMapper;

    public CreatorRegistrationService(ChannelVerificationPort channelVerificationPort, CreatorRepositoryPort creatorRepository,
                                      ConfirmationTokenUseCase confirmationTokenUseCase, ObjectMapper objectMapper) {
        this.channelVerificationPort = channelVerificationPort;
        this.creatorRepository = creatorRepository;
        this.confirmationTokenUseCase = confirmationTokenUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public CreatorPreviewResult preview(CreatorPreviewCommand command) {
        UUID adminId = requireAdmin(command);
        URI channelUrl = youtubeChannelUrl(command.channelUrl());
        Optional<VerifiedChannel> verified;
        try {
            verified = channelVerificationPort.verify(channelUrl);
        } catch (ChannelVerificationFailedException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        if (verified.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
        }
        VerifiedChannel channel = verified.get();
        Optional<Creator> existing = creatorRepository.findByExternalChannelId(channel.externalChannelId());
        if (existing.isPresent()) {
            return new CreatorPreviewResult(
                    CreatorPreviewResult.Decision.DUPLICATE,
                    null,
                    null,
                    candidate(existing.get()),
                    existing(existing.get()));
        }
        ChannelSnapshot snapshot = new ChannelSnapshot(channel.externalChannelId(), channel.channelName(), channel.channelUrl(), channel.checkedAt());
        IssuedConfirmationToken token = confirmationTokenUseCase.issue(new ConfirmationTokenIssueCommand(
                adminId, ConfirmationTokenResourceType.CREATOR, SNAPSHOT_SCHEMA_VERSION, channel.externalChannelId(), serialize(snapshot)));
        return new CreatorPreviewResult(CreatorPreviewResult.Decision.READY, token.rawToken(), token.expiresAt(), candidate(snapshot, null), null);
    }

    @Override
    @Transactional
    public CreatorCreationResult create(CreatorCreateCommand command) {
        UUID adminId = requireAdmin(command);
        AcquiredConfirmationToken token = confirmationTokenUseCase.acquire(
                command.confirmationToken(), adminId, ConfirmationTokenResourceType.CREATOR);
        if (token.isReplay()) {
            Creator creator = findResult(token.resultResourceId());
            return new CreatorCreationResult(candidate(creator), false, token.status() == ConfirmationTokenStatus.DUPLICATE);
        }
        ChannelSnapshot snapshot = deserialize(token);
        Optional<Creator> existing = creatorRepository.findByExternalChannelId(snapshot.externalChannelId());
        if (existing.isPresent()) {
            confirmationTokenUseCase.completeDuplicate(token.tokenId(), existing.get().getId());
            return new CreatorCreationResult(candidate(existing.get()), false, true);
        }
        Creator candidate = new Creator(UUID.randomUUID(), snapshot.externalChannelId(), snapshot.channelName(), snapshot.channelUrl(),
                PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE, ExternalAvailabilityStatus.AVAILABLE,
                snapshot.checkedAt(), null, null, null);
        Optional<Creator> inserted = creatorRepository.insertIfAbsent(candidate);
        if (inserted.isEmpty()) {
            Creator concurrent = creatorRepository.findByExternalChannelId(snapshot.externalChannelId())
                    .orElseThrow(() -> new IllegalStateException("Concurrent creator result was not found."));
            confirmationTokenUseCase.completeDuplicate(token.tokenId(), concurrent.getId());
            return new CreatorCreationResult(candidate(concurrent), false, true);
        }
        confirmationTokenUseCase.completeCreated(token.tokenId(), inserted.get().getId());
        return new CreatorCreationResult(candidate(inserted.get()), true, false);
    }

    private UUID requireAdmin(CreatorPreviewCommand command) {
        if (command == null || command.adminAccountId() == null) throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        return command.adminAccountId();
    }
    private UUID requireAdmin(CreatorCreateCommand command) {
        if (command == null || command.adminAccountId() == null) throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN);
        return command.adminAccountId();
    }
    private URI youtubeChannelUrl(String value) {
        if (value == null) throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "channelUrl is required.");
        if (value.isBlank() || value.length() > 2048) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            boolean allowed = host != null && (host.equalsIgnoreCase("youtube.com") || host.toLowerCase().endsWith(".youtube.com"));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowed || !hasChannelIdentifier(uri)) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE); }
    }

    private boolean hasChannelIdentifier(URI uri) {
        String path = uri.getPath();
        return path != null
                && ((path.startsWith("/channel/") && path.length() > "/channel/".length())
                || (path.startsWith("/@") && path.length() > "/@".length()));
    }
    private String serialize(ChannelSnapshot snapshot) {
        try { return objectMapper.writeValueAsString(snapshot); }
        catch (JacksonException exception) { throw new IllegalStateException("Creator confirmation snapshot could not be serialized.", exception); }
    }
    private ChannelSnapshot deserialize(AcquiredConfirmationToken token) {
        if (token.candidateSchemaVersion() != SNAPSHOT_SCHEMA_VERSION) throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN);
        try {
            ChannelSnapshot snapshot = objectMapper.readValue(token.candidateSnapshot(), ChannelSnapshot.class);
            if (!snapshot.externalChannelId().equals(token.identityKey())) throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN);
            return snapshot;
        } catch (JacksonException | IllegalArgumentException exception) { throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN); }
    }
    private Creator findResult(UUID id) { return creatorRepository.findById(id).orElseThrow(() -> new IllegalStateException("Completed creator result was not found.")); }
    private CreatorCandidate candidate(ChannelSnapshot s, UUID id) { return new CreatorCandidate(id, s.channelName(), s.channelUrl()); }
    private CreatorCandidate candidate(Creator c) { return new CreatorCandidate(c.getId(), c.getChannelName(), c.getChannelUrl()); }
    private ExistingCreator existing(Creator c) { return new ExistingCreator(c.getId(), c.getChannelName(), c.getChannelUrl()); }
    private record ChannelSnapshot(String externalChannelId, String channelName, String channelUrl, OffsetDateTime checkedAt) { }
}
