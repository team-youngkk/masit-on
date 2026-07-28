package com.masiton.orchestration.presentation;

import java.util.UUID;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.orchestration.application.port.in.RegisterVisitRelationshipUseCase;
import com.masiton.security.application.AdminPrincipal;
import com.masiton.security.application.AdminRole;

@RestController
@RequestMapping("/api/admin/visit-relationships")
public class VisitRelationshipRegistrationController {

    private final RegisterVisitRelationshipUseCase registerVisitRelationshipUseCase;

    public VisitRelationshipRegistrationController(RegisterVisitRelationshipUseCase registerVisitRelationshipUseCase) {
        this.registerVisitRelationshipUseCase = registerVisitRelationshipUseCase;
    }

    @PostMapping
    public ResponseEntity<VisitRelationshipResponse> register(
            Authentication authentication,
            @RequestBody VisitRelationshipRequest request) {
        UUID restaurantId = requiredIdentifier(request == null ? null : request.restaurantId(), "restaurantId");
        UUID creatorId = requiredIdentifier(request == null ? null : request.creatorId(), "creatorId");
        UUID videoId = requiredIdentifier(request == null ? null : request.videoId(), "videoId");
        boolean evidenceConfirmed = request != null && Boolean.TRUE.equals(request.visitEvidenceConfirmed());
        if (!evidenceConfirmed) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VISIT_EVIDENCE_INSUFFICIENT", "방문 근거 확인이 필요합니다.");
        }
        RegisterVisitRelationshipUseCase.RegisteredVisitRelationship registered = registerVisitRelationshipUseCase.register(
                new RegisterVisitRelationshipUseCase.RegisterVisitRelationshipCommand(
                        restaurantId, creatorId, videoId, evidenceConfirmed),
                toAdminPrincipal(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(new VisitRelationshipResponse(
                registered.id(), registered.restaurantId(), registered.creatorId(), registered.videoId()));
    }

    private AdminPrincipal toAdminPrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        Set<AdminRole> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(AdminRole.ADMIN.name()::equals)
                .map(ignored -> AdminRole.ADMIN)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AdminPrincipal(authentication.getName(), roles);
    }

    private UUID requiredIdentifier(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, field, "필수 입력값입니다.");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER, field, "UUID 형식이 아닙니다.");
        }
    }

    public record VisitRelationshipRequest(
            String restaurantId,
            String creatorId,
            String videoId,
            Boolean visitEvidenceConfirmed
    ) { }

    public record VisitRelationshipResponse(UUID id, UUID restaurantId, UUID creatorId, UUID videoId) { }
}
