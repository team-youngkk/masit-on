package com.masiton.orchestration.application.query;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.creator.application.port.in.CreatorReferenceExceptionFactory;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;
import com.masiton.orchestration.application.port.in.CreatorEvidenceVideoItem;
import com.masiton.orchestration.application.port.in.CreatorEvidenceVideosResult;
import com.masiton.orchestration.application.port.in.GetCreatorEvidenceVideosQuery;
import com.masiton.orchestration.application.port.out.CreatorEvidenceVideoPageResult;
import com.masiton.orchestration.application.port.out.CreatorEvidenceVideoQueryPort;
import com.masiton.orchestration.application.port.out.CreatorEvidenceVideoRow;

/**
 * API-CREATOR-DETAIL-003 유튜버 근거 영상 조회를 조합하는 전용 Application Query 책임이다.
 * query-composition.md 1절과 같은 방식으로 orchestration이 Creator·Restaurant·Visit·Video의
 * JPA Entity·Repository를 소유하지 않고 Port만 호출한다.
 *
 * <p>BR-VISIT-005·PRD 9절: 목록이 비어 있는 것(200 + 빈 items)과 Creator 자체가 없음·비공개·
 * 삭제·외부 이용 불가(404 CREATOR_NOT_FOUND)를 구분해야 하므로, 목록 조회 전에 Creator 유효성을
 * 먼저 확인한다. 이 판정은 Creator가 소유하므로(module-boundaries.md 2절) orchestration이 같은
 * 조건을 다시 작성하지 않고 Creator의 공개 입력 Port({@link FindCreatorReferenceUseCase})를
 * 호출한다(dependency-rules.md 3절). 사용자 조회 중에는 YouTube API를 호출하지 않고 저장된 값만
 * 조합한다(BR-CREATOR-011).
 */
@Service
@Transactional(readOnly = true)
public class CreatorEvidenceVideosQueryService implements GetCreatorEvidenceVideosQuery {

    private final FindCreatorReferenceUseCase findCreatorReferenceUseCase;
    private final CreatorEvidenceVideoQueryPort creatorEvidenceVideoQueryPort;

    public CreatorEvidenceVideosQueryService(
            FindCreatorReferenceUseCase findCreatorReferenceUseCase,
            CreatorEvidenceVideoQueryPort creatorEvidenceVideoQueryPort
    ) {
        this.findCreatorReferenceUseCase = findCreatorReferenceUseCase;
        this.creatorEvidenceVideoQueryPort = creatorEvidenceVideoQueryPort;
    }

    @Override
    public CreatorEvidenceVideosResult getEvidenceVideos(UUID creatorId, int page, int size) {
        if (!isPubliclyVisibleCreator(creatorId)) {
            throw CreatorReferenceExceptionFactory.notFound();
        }

        CreatorEvidenceVideoPageResult pageResult = creatorEvidenceVideoQueryPort.findPage(creatorId, page, size);

        List<CreatorEvidenceVideoItem> items = pageResult.rows().stream()
                .map(CreatorEvidenceVideosQueryService::toItem)
                .toList();

        int totalPages = pageResult.totalElements() == 0
                ? 0
                : (int) Math.ceil((double) pageResult.totalElements() / size);
        boolean hasNext = page < totalPages;

        return new CreatorEvidenceVideosResult(
                items, page, size, pageResult.totalElements(), totalPages, hasNext);
    }

    private boolean isPubliclyVisibleCreator(UUID creatorId) {
        return findCreatorReferenceUseCase.findCreatorReference(creatorId)
                .filter(reference -> reference.publiclyVisible() && reference.externallyAvailable())
                .isPresent();
    }

    private static CreatorEvidenceVideoItem toItem(CreatorEvidenceVideoRow row) {
        return new CreatorEvidenceVideoItem(row.id(), row.title(), row.thumbnailUrl(), row.sourceUrl());
    }
}
