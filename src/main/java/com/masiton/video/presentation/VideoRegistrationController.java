package com.masiton.video.presentation;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.ErrorResponse;
import com.masiton.video.application.port.in.VideoRegistrationUseCase;

@RestController
@RequestMapping("/api/admin")
public class VideoRegistrationController {
    private final VideoRegistrationUseCase videoRegistrationUseCase;
    public VideoRegistrationController(VideoRegistrationUseCase videoRegistrationUseCase) {
        this.videoRegistrationUseCase = videoRegistrationUseCase;
    }

    @PostMapping("/video-registration-previews")
    public ResponseEntity<VideoPreviewResponse> preview(Authentication authentication, @RequestBody VideoPreviewRequest request) {
        VideoRegistrationUseCase.VideoPreviewResult result = videoRegistrationUseCase.preview(
                new VideoRegistrationUseCase.VideoPreviewCommand(adminId(authentication), request.sourceUrl()));
        return ResponseEntity.ok(new VideoPreviewResponse(
                result.decision(),
                result.confirmationToken(),
                result.expiresAt(),
                result.candidate() == null ? null : toPreviewCandidate(result.candidate()),
                result.existingResource()));
    }
    @PostMapping("/videos")
    public ResponseEntity<?> create(Authentication authentication, @RequestBody VideoCreateRequest request) {
        VideoRegistrationUseCase.VideoCreationResult result = videoRegistrationUseCase.create(new VideoRegistrationUseCase.VideoCreateCommand(adminId(authentication), request.confirmationToken()));
        if (result.duplicate()) {
            VideoRegistrationUseCase.VideoCandidate video = result.video();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("DUPLICATE_VIDEO", "이미 등록된 영상입니다.", java.util.List.of(),
                    new VideoRegistrationUseCase.ExistingVideo(video.id(), video.title(), video.channelName(), video.sourceUrl()), MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));
        }
        return (result.created() ? ResponseEntity.status(HttpStatus.CREATED) : ResponseEntity.ok())
                .body(toVideoResponse(result.video()));
    }
    private UUID adminId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private VideoPreviewCandidate toPreviewCandidate(VideoRegistrationUseCase.VideoCandidate candidate) {
        return new VideoPreviewCandidate(
                candidate.title(), candidate.thumbnailUrl(), candidate.channelName(), candidate.sourceUrl());
    }

    private VideoResponse toVideoResponse(VideoRegistrationUseCase.VideoCandidate video) {
        return new VideoResponse(video.id(), video.title(), video.thumbnailUrl(), video.channelName(), video.sourceUrl());
    }

    public record VideoPreviewRequest(String sourceUrl) { }
    public record VideoCreateRequest(String confirmationToken) { }
    public record VideoPreviewResponse(
            VideoRegistrationUseCase.VideoPreviewResult.Decision decision,
            String confirmationToken,
            OffsetDateTime expiresAt,
            VideoPreviewCandidate candidate,
            VideoRegistrationUseCase.ExistingVideo existingResource) { }
    public record VideoPreviewCandidate(String title, String thumbnailUrl, String channelName, String sourceUrl) { }
    public record VideoResponse(UUID id, String title, String thumbnailUrl, String channelName, String sourceUrl) { }
}
