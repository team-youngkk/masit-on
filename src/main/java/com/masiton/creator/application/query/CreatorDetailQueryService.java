package com.masiton.creator.application.query;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.creator.application.port.in.CreatorReferenceExceptionFactory;
import com.masiton.creator.application.port.in.GetPublicCreatorDetailUseCase;
import com.masiton.creator.application.port.out.CreatorRepositoryPort;
import com.masiton.creator.domain.model.Creator;
import com.masiton.creator.domain.model.ExternalAvailabilityStatus;
import com.masiton.creator.domain.model.LifecycleStatus;
import com.masiton.creator.domain.model.PublicationStatus;

/**
 * API-CREATOR-DETAIL-001. creator-detail-api.md 3·4절, BR-CREATOR-008: 없음·비공개·삭제·
 * 외부 이용 불가를 모두 같은 404 {@code CREATOR_NOT_FOUND}로 처리해 상태를 구분해 노출하지
 * 않는다. BR-CREATOR-009: 조회 중 YouTube API를 호출하지 않고 저장된 값만 반환한다.
 *
 * <p>출력 Port는 새로 만들지 않고 이미 {@code creator.application.port.out}에 있는
 * {@link CreatorRepositoryPort#findById(UUID)}를 그대로 쓴다.
 * {@code com.masiton.creator.application.CreatorReferenceQueryService}(Visit 등록용 최소
 * Snapshot 조회)가 같은 공개 판정 조건(publication·lifecycle·external availability)에 이미
 * 같은 Port를 쓰고 있어 이 조회에도 같은 방식을 따르는 것이 이 저장소의 기존 관례이며, 거의
 * 동일한 쿼리를 반복 정의하지 않기 위함이다.
 */
@Service
@Transactional(readOnly = true)
public class CreatorDetailQueryService implements GetPublicCreatorDetailUseCase {

    private final CreatorRepositoryPort creatorRepository;

    public CreatorDetailQueryService(CreatorRepositoryPort creatorRepository) {
        this.creatorRepository = creatorRepository;
    }

    @Override
    public CreatorDetailResult getPublicCreatorDetail(UUID creatorId) {
        Creator creator = creatorRepository.findById(creatorId)
                .filter(this::isPubliclyVisible)
                .orElseThrow(CreatorReferenceExceptionFactory::notFound);
        return new CreatorDetailResult(
                creator.getId(),
                creator.getChannelName(),
                creator.getProfileImageUrl(),
                creator.getDescription(),
                creator.getHandle(),
                creator.getChannelUrl());
    }

    private boolean isPubliclyVisible(Creator creator) {
        return creator.getPublicationStatus() == PublicationStatus.PUBLIC
                && creator.getLifecycleStatus() == LifecycleStatus.ACTIVE
                && creator.getExternalAvailabilityStatus() == ExternalAvailabilityStatus.AVAILABLE;
    }
}
