package com.masiton.video.application;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.security.application.AcquiredConfirmationToken;
import com.masiton.security.application.ConfirmationTokenIssueCommand;
import com.masiton.security.application.IssuedConfirmationToken;
import com.masiton.security.application.port.in.ConfirmationTokenUseCase;
import com.masiton.security.domain.model.ConfirmationTokenResourceType;
import com.masiton.security.domain.model.ConfirmationTokenStatus;
import com.masiton.video.application.port.in.VideoRegistrationUseCase;
import com.masiton.video.application.port.out.VerifiedVideo;
import com.masiton.video.application.port.out.VideoRepositoryPort;
import com.masiton.video.application.port.out.VideoVerificationPort;
import com.masiton.video.domain.model.ExternalAvailabilityStatus;
import com.masiton.video.domain.model.LifecycleStatus;
import com.masiton.video.domain.model.PublicationStatus;
import com.masiton.video.domain.model.Video;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class VideoRegistrationService implements VideoRegistrationUseCase {
    private static final short SNAPSHOT_SCHEMA_VERSION = 1;
    private final VideoVerificationPort videoVerificationPort;
    private final VideoRepositoryPort videoRepository;
    private final ConfirmationTokenUseCase confirmationTokenUseCase;
    private final ObjectMapper objectMapper;
    public VideoRegistrationService(VideoVerificationPort videoVerificationPort, VideoRepositoryPort videoRepository,
                                    ConfirmationTokenUseCase confirmationTokenUseCase, ObjectMapper objectMapper) {
        this.videoVerificationPort = videoVerificationPort; this.videoRepository = videoRepository;
        this.confirmationTokenUseCase = confirmationTokenUseCase; this.objectMapper = objectMapper;
    }
    @Override
    public VideoPreviewResult preview(VideoPreviewCommand command) {
        UUID adminId = requireAdmin(command);
        Optional<VerifiedVideo> verified;
        try {
            verified = videoVerificationPort.verify(youtubeVideoUrl(command.sourceUrl()));
        } catch (VideoVerificationFailedException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        if (verified.isEmpty()) return new VideoPreviewResult(VideoPreviewResult.Decision.REVIEW_REQUIRED, null, null, null, null);
        VerifiedVideo video = verified.get();
        Optional<Video> existing = videoRepository.findByExternalVideoId(video.externalVideoId());
        if (existing.isPresent()) {
            return new VideoPreviewResult(
                    VideoPreviewResult.Decision.DUPLICATE,
                    null,
                    null,
                    candidate(existing.get(), video.channelName()),
                    existing(existing.get(), video.channelName()));
        }
        VideoSnapshot snapshot = new VideoSnapshot(video.externalVideoId(), video.publisherExternalChannelId(), video.title(), video.thumbnailUrl(),
                video.channelName(), video.sourceUrl(), video.publishedAt(), video.checkedAt());
        IssuedConfirmationToken token = confirmationTokenUseCase.issue(new ConfirmationTokenIssueCommand(adminId, ConfirmationTokenResourceType.VIDEO,
                SNAPSHOT_SCHEMA_VERSION, video.externalVideoId(), serialize(snapshot)));
        return new VideoPreviewResult(VideoPreviewResult.Decision.READY, token.rawToken(), token.expiresAt(), candidate(snapshot, null), null);
    }
    @Override
    @Transactional
    public VideoCreationResult create(VideoCreateCommand command) {
        UUID adminId = requireAdmin(command);
        AcquiredConfirmationToken token = confirmationTokenUseCase.acquire(command.confirmationToken(), adminId, ConfirmationTokenResourceType.VIDEO);
        VideoSnapshot snapshot = deserialize(token);
        if (token.isReplay()) {
            Video video = findResult(token.resultResourceId());
            return new VideoCreationResult(candidate(video, snapshot.channelName()), false, token.status() == ConfirmationTokenStatus.DUPLICATE);
        }
        Optional<Video> existing = videoRepository.findByExternalVideoId(snapshot.externalVideoId());
        if (existing.isPresent()) {
            confirmationTokenUseCase.completeDuplicate(token.tokenId(), existing.get().getId());
            return new VideoCreationResult(candidate(existing.get(), snapshot.channelName()), false, true);
        }
        Video candidate = new Video(UUID.randomUUID(), null, snapshot.externalVideoId(), snapshot.publisherExternalChannelId(), snapshot.title(),
                snapshot.sourceUrl(), snapshot.thumbnailUrl(), snapshot.publishedAt(), PublicationStatus.PUBLIC, LifecycleStatus.ACTIVE,
                ExternalAvailabilityStatus.AVAILABLE, snapshot.checkedAt(), null, null, null);
        Optional<Video> inserted = videoRepository.insertIfAbsent(candidate);
        if (inserted.isEmpty()) {
            Video concurrent = videoRepository.findByExternalVideoId(snapshot.externalVideoId()).orElseThrow(() -> new IllegalStateException("Concurrent video result was not found."));
            confirmationTokenUseCase.completeDuplicate(token.tokenId(), concurrent.getId());
            return new VideoCreationResult(candidate(concurrent, snapshot.channelName()), false, true);
        }
        confirmationTokenUseCase.completeCreated(token.tokenId(), inserted.get().getId());
        return new VideoCreationResult(candidate(inserted.get(), snapshot.channelName()), true, false);
    }
    private UUID requireAdmin(VideoPreviewCommand command) { if (command == null || command.adminAccountId() == null) throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED); return command.adminAccountId(); }
    private UUID requireAdmin(VideoCreateCommand command) { if (command == null || command.adminAccountId() == null) throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN); return command.adminAccountId(); }
    private URI youtubeVideoUrl(String value) {
        if (value == null) throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "sourceUrl is required.");
        if (value.isBlank() || value.length() > 2048) throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE);
        try {
            URI uri = URI.create(value.trim()); String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            boolean allowed = host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowed || !hasVideoIdentifier(uri, host)) throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE); }
    }
    private boolean hasVideoIdentifier(URI uri, String host) {
        if (host.equals("youtu.be")) return uri.getPath() != null && uri.getPath().length() > 1;
        String path = uri.getPath();
        if ("/watch".equals(path) && uri.getQuery() != null) {
            for (String part : uri.getQuery().split("&")) if (part.startsWith("v=") && part.length() > 2) return true;
        }
        return path != null && ((path.startsWith("/shorts/") && path.length() > 8)
                || (path.startsWith("/embed/") && path.length() > 7));
    }
    private String serialize(VideoSnapshot snapshot) { try { return objectMapper.writeValueAsString(snapshot); } catch (JacksonException exception) { throw new IllegalStateException("Video confirmation snapshot could not be serialized.", exception); } }
    private VideoSnapshot deserialize(AcquiredConfirmationToken token) {
        if (token.candidateSchemaVersion() != SNAPSHOT_SCHEMA_VERSION) throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN);
        try { VideoSnapshot snapshot = objectMapper.readValue(token.candidateSnapshot(), VideoSnapshot.class); if (!snapshot.externalVideoId().equals(token.identityKey())) throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN); return snapshot; }
        catch (JacksonException | IllegalArgumentException exception) { throw new BusinessException(ErrorCode.INVALID_CONFIRMATION_TOKEN); }
    }
    private Video findResult(UUID id) { return videoRepository.findById(id).orElseThrow(() -> new IllegalStateException("Completed video result was not found.")); }
    private VideoCandidate candidate(VideoSnapshot snapshot, UUID id) { return new VideoCandidate(id, snapshot.title(), snapshot.thumbnailUrl(), snapshot.channelName(), snapshot.sourceUrl()); }
    private VideoCandidate candidate(Video video, String channelName) { return new VideoCandidate(video.getId(), video.getTitle(), video.getThumbnailUrl(), channelName, video.getSourceUrl()); }
    private ExistingVideo existing(Video video, String channelName) { return new ExistingVideo(video.getId(), video.getTitle(), channelName, video.getSourceUrl()); }
    private record VideoSnapshot(String externalVideoId, String publisherExternalChannelId, String title, String thumbnailUrl, String channelName,
                                 String sourceUrl, OffsetDateTime publishedAt, OffsetDateTime checkedAt) { }
}
