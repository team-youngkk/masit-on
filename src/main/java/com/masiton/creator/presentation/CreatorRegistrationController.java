package com.masiton.creator.presentation;

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
import com.masiton.common.security.LegacyAdminActorResolver;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.ErrorResponse;
import com.masiton.creator.application.port.in.CreatorRegistrationUseCase;

@RestController
@RequestMapping("/api/admin")
public class CreatorRegistrationController {
    private final CreatorRegistrationUseCase creatorRegistrationUseCase;
    private final LegacyAdminActorResolver legacyAdminActorResolver;

    public CreatorRegistrationController(
            CreatorRegistrationUseCase creatorRegistrationUseCase,
            LegacyAdminActorResolver legacyAdminActorResolver
    ) {
        this.creatorRegistrationUseCase = creatorRegistrationUseCase;
        this.legacyAdminActorResolver = legacyAdminActorResolver;
    }

    @PostMapping("/creator-registration-previews")
    public ResponseEntity<CreatorPreviewResponse> preview(Authentication authentication, @RequestBody CreatorPreviewRequest request) {
        CreatorRegistrationUseCase.CreatorPreviewResult result = creatorRegistrationUseCase.preview(
                new CreatorRegistrationUseCase.CreatorPreviewCommand(adminId(authentication), request.channelUrl()));
        return ResponseEntity.ok(new CreatorPreviewResponse(
                result.decision(),
                result.confirmationToken(),
                result.expiresAt(),
                result.candidate() == null ? null : toPreviewCandidate(result.candidate()),
                result.existingResource()));
    }
    @PostMapping("/creators")
    public ResponseEntity<?> create(Authentication authentication, @RequestBody CreatorCreateRequest request) {
        CreatorRegistrationUseCase.CreatorCreationResult result = creatorRegistrationUseCase.create(new CreatorRegistrationUseCase.CreatorCreateCommand(adminId(authentication), request.confirmationToken()));
        if (result.duplicate()) {
            CreatorRegistrationUseCase.CreatorCandidate creator = result.creator();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("DUPLICATE_CREATOR", "이미 등록된 유튜버입니다.", java.util.List.of(),
                    new CreatorRegistrationUseCase.ExistingCreator(creator.id(), creator.channelName(), creator.channelUrl()), MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));
        }
        return (result.created() ? ResponseEntity.status(HttpStatus.CREATED) : ResponseEntity.ok())
                .body(toCreatorResponse(result.creator()));
    }
    private UUID adminId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        try {
            return legacyAdminActorResolver.resolve(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private CreatorPreviewCandidate toPreviewCandidate(CreatorRegistrationUseCase.CreatorCandidate candidate) {
        return new CreatorPreviewCandidate(candidate.channelName(), candidate.channelUrl());
    }

    private CreatorResponse toCreatorResponse(CreatorRegistrationUseCase.CreatorCandidate creator) {
        return new CreatorResponse(creator.id(), creator.channelName(), creator.channelUrl());
    }

    public record CreatorPreviewRequest(String channelUrl) { }
    public record CreatorCreateRequest(String confirmationToken) { }
    public record CreatorPreviewResponse(
            CreatorRegistrationUseCase.CreatorPreviewResult.Decision decision,
            String confirmationToken,
            OffsetDateTime expiresAt,
            CreatorPreviewCandidate candidate,
            CreatorRegistrationUseCase.ExistingCreator existingResource) { }
    public record CreatorPreviewCandidate(String channelName, String channelUrl) { }
    public record CreatorResponse(UUID id, String channelName, String channelUrl) { }
}
