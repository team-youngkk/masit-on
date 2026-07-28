package com.masiton.orchestration.application.command;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;
import com.masiton.orchestration.application.port.in.RegisterVisitRelationshipUseCase;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;
import com.masiton.security.application.AdminPrincipal;
import com.masiton.video.application.port.in.FindVideoReferenceUseCase;
import com.masiton.video.application.port.in.ResolveVideoCreatorUseCase;
import com.masiton.visit.application.port.in.RegisterVisitUseCase;

/**
 * Restaurant, Creator, Video의 공개 입력 Port만 조합해 Visit 등록의 원자적 경계를 소유한다.
 */
@Service
public class RegisterVisitService implements RegisterVisitRelationshipUseCase {

    private final FindRestaurantReferenceUseCase restaurantReferences;
    private final FindCreatorReferenceUseCase creatorReferences;
    private final FindVideoReferenceUseCase videoReferences;
    private final ResolveVideoCreatorUseCase videoCreatorResolver;
    private final RegisterVisitUseCase visitRegistration;

    public RegisterVisitService(
            FindRestaurantReferenceUseCase restaurantReferences,
            FindCreatorReferenceUseCase creatorReferences,
            FindVideoReferenceUseCase videoReferences,
            ResolveVideoCreatorUseCase videoCreatorResolver,
            RegisterVisitUseCase visitRegistration
    ) {
        this.restaurantReferences = restaurantReferences;
        this.creatorReferences = creatorReferences;
        this.videoReferences = videoReferences;
        this.videoCreatorResolver = videoCreatorResolver;
        this.visitRegistration = visitRegistration;
    }

    @Override
    @Transactional
    public RegisteredVisitRelationship register(RegisterVisitRelationshipCommand command, AdminPrincipal adminPrincipal) {
        requireAdminPrincipal(adminPrincipal);
        requireEvidence(command);

        FindRestaurantReferenceUseCase.RestaurantReference restaurant = restaurantReferences
                .findRestaurantReference(command.restaurantId())
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND"));
        FindCreatorReferenceUseCase.CreatorReference creator = creatorReferences
                .findCreatorReference(command.creatorId())
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "CREATOR_NOT_FOUND"));
        FindVideoReferenceUseCase.VideoReference video = videoReferences
                .findVideoReference(command.videoId())
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND"));

        if (!restaurant.publiclyVisible() || !creator.publiclyVisible() || !video.publiclyVisible()
                || !creator.externallyAvailable() || !video.externallyAvailable()) {
            throw new BusinessException(ErrorCode.REFERENCE_NOT_PUBLIC);
        }
        if (!creator.externalChannelId().equals(video.publisherExternalChannelId())) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "VIDEO_CHANNEL_MISMATCH");
        }

        FindVideoReferenceUseCase.VideoReference resolvedVideo = video.creatorId() == null
                ? videoCreatorResolver.resolveCreator(video.id(), creator.id())
                : video;
        if (!creator.id().equals(resolvedVideo.creatorId())) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "VIDEO_CHANNEL_MISMATCH");
        }

        RegisterVisitUseCase.VisitRegistrationResult result = visitRegistration.register(
                new RegisterVisitUseCase.RegisterVisitCommand(
                        restaurant.id(), creator.id(), resolvedVideo.id(), command.visitEvidenceConfirmed()));
        if (!result.created()) {
            throw failure(HttpStatus.CONFLICT, "DUPLICATE_VISIT_RELATIONSHIP");
        }
        return new RegisteredVisitRelationship(result.id(), restaurant.id(), creator.id(), resolvedVideo.id());
    }

    private void requireEvidence(RegisterVisitRelationshipCommand command) {
        if (command == null || !command.visitEvidenceConfirmed()) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "VISIT_EVIDENCE_INSUFFICIENT");
        }
    }

    private void requireAdminPrincipal(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null || adminPrincipal.adminId() == null || adminPrincipal.adminId().isBlank()
                || !adminPrincipal.hasAdminRole()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private BusinessException failure(HttpStatus status, String code) {
        return new BusinessException(status, code, switch (code) {
            case "RESTAURANT_NOT_FOUND" -> "요청한 맛집을 찾을 수 없습니다.";
            case "CREATOR_NOT_FOUND" -> "요청한 유튜버를 찾을 수 없습니다.";
            case "VIDEO_NOT_FOUND" -> "요청한 영상을 찾을 수 없습니다.";
            case "VIDEO_CHANNEL_MISMATCH" -> "영상의 게시 채널과 유튜버 채널이 일치하지 않습니다.";
            case "DUPLICATE_VISIT_RELATIONSHIP" -> "동일한 방문 관계가 이미 등록되어 있습니다.";
            case "VISIT_EVIDENCE_INSUFFICIENT" -> "방문 근거 확인이 필요합니다.";
            default -> "요청을 처리할 수 없습니다.";
        });
    }
}
