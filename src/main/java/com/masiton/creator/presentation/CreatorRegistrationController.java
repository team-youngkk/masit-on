package com.masiton.creator.presentation;

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
import com.masiton.creator.application.port.in.CreatorRegistrationUseCase;

@RestController
@RequestMapping("/api/admin")
public class CreatorRegistrationController {
    private final CreatorRegistrationUseCase creatorRegistrationUseCase;
    public CreatorRegistrationController(CreatorRegistrationUseCase creatorRegistrationUseCase) { this.creatorRegistrationUseCase = creatorRegistrationUseCase; }
    @PostMapping("/creator-registration-previews")
    public ResponseEntity<CreatorRegistrationUseCase.CreatorPreviewResult> preview(Authentication authentication, @RequestBody CreatorPreviewRequest request) {
        return ResponseEntity.ok(creatorRegistrationUseCase.preview(new CreatorRegistrationUseCase.CreatorPreviewCommand(adminId(authentication), request.channelUrl())));
    }
    @PostMapping("/creators")
    public ResponseEntity<?> create(Authentication authentication, @RequestBody CreatorCreateRequest request) {
        CreatorRegistrationUseCase.CreatorCreationResult result = creatorRegistrationUseCase.create(new CreatorRegistrationUseCase.CreatorCreateCommand(adminId(authentication), request.confirmationToken()));
        if (result.duplicate()) {
            CreatorRegistrationUseCase.CreatorCandidate creator = result.creator();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("DUPLICATE_CREATOR", "이미 등록된 유튜버입니다.", java.util.List.of(),
                    new CreatorRegistrationUseCase.ExistingCreator(creator.id(), creator.channelName(), creator.channelUrl()), MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));
        }
        return (result.created() ? ResponseEntity.status(HttpStatus.CREATED) : ResponseEntity.ok()).body(result.creator());
    }
    private UUID adminId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        try { return UUID.fromString(authentication.getName()); }
        catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }
    public record CreatorPreviewRequest(String channelUrl) { }
    public record CreatorCreateRequest(String confirmationToken) { }
}
