package com.masiton.orchestration.presentation.detail;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.orchestration.application.port.in.CreatorEvidenceVideosResult;
import com.masiton.orchestration.application.port.in.CreatorVisitedRestaurantsResult;
import com.masiton.orchestration.application.port.in.GetCreatorEvidenceVideosQuery;
import com.masiton.orchestration.application.port.in.GetCreatorVisitedRestaurantsQuery;

/**
 * API-CREATOR-DETAIL-002·003 유튜버 방문 맛집·근거 영상 조회의 입력 Adapter다. 두 API는 creatorId
 * 404 경계(creator-detail-api.md 3절)와 페이지 파라미터 검증 규칙(pagination-contract.md)을
 * 공유하지만, 각 목록의 페이지 상태는 완전히 독립적이며 한 API 호출이 다른 목록의 페이지를 조회하거나
 * 바꾸지 않는다(creator-detail-api.md 2절). 식별자 형식 검증과 HTTP 변환만 수행하고 조합 로직은
 * Application 입력 Port에 위임한다(dependency-rules.md 3절).
 */
@RestController
@RequestMapping("/api/creators/{creatorId}")
public class CreatorVisitContentController {

    private static final Set<String> KNOWN_FIELDS = Set.of("page", "size");
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);

    private final GetCreatorVisitedRestaurantsQuery getCreatorVisitedRestaurantsQuery;
    private final GetCreatorEvidenceVideosQuery getCreatorEvidenceVideosQuery;

    public CreatorVisitContentController(
            GetCreatorVisitedRestaurantsQuery getCreatorVisitedRestaurantsQuery,
            GetCreatorEvidenceVideosQuery getCreatorEvidenceVideosQuery
    ) {
        this.getCreatorVisitedRestaurantsQuery = getCreatorVisitedRestaurantsQuery;
        this.getCreatorEvidenceVideosQuery = getCreatorEvidenceVideosQuery;
    }

    @GetMapping("/restaurants")
    public CreatorVisitedRestaurantsResponse getVisitedRestaurants(
            @PathVariable String creatorId,
            @RequestParam MultiValueMap<String, String> queryParams
    ) {
        UUID id = parseCreatorId(creatorId);
        PageRequestParams pageParams = parsePageParams(queryParams);
        CreatorVisitedRestaurantsResult result = getCreatorVisitedRestaurantsQuery.getVisitedRestaurants(
                id, pageParams.page(), pageParams.size());
        return CreatorVisitedRestaurantsResponse.from(result);
    }

    @GetMapping("/videos")
    public CreatorEvidenceVideosResponse getEvidenceVideos(
            @PathVariable String creatorId,
            @RequestParam MultiValueMap<String, String> queryParams
    ) {
        UUID id = parseCreatorId(creatorId);
        PageRequestParams pageParams = parsePageParams(queryParams);
        CreatorEvidenceVideosResult result = getCreatorEvidenceVideosQuery.getEvidenceVideos(
                id, pageParams.page(), pageParams.size());
        return CreatorEvidenceVideosResponse.from(result);
    }

    private UUID parseCreatorId(String creatorId) {
        try {
            return UUID.fromString(creatorId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
    }

    /**
     * RestaurantSearchController와 같은 방식으로 알려지지 않은 쿼리 파라미터를 거부하고 page·size를
     * 검증한다. 두 목록 모두 클라이언트 정렬 입력을 받지 않으므로(creator-detail-api.md 7절)
     * 공통 필드는 page·size뿐이다.
     */
    private PageRequestParams parsePageParams(MultiValueMap<String, String> queryParams) {
        validateParamNames(queryParams);
        validateSingleValue(queryParams);
        int page = parsePage(queryParams.getFirst("page"));
        int size = parseSize(queryParams.getFirst("size"));
        return new PageRequestParams(page, size);
    }

    private void validateParamNames(MultiValueMap<String, String> queryParams) {
        for (String name : queryParams.keySet()) {
            if (!KNOWN_FIELDS.contains(name)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }

    private void validateSingleValue(MultiValueMap<String, String> queryParams) {
        for (String field : KNOWN_FIELDS) {
            List<String> values = queryParams.get(field);
            if (values != null && values.size() > 1) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "값은 한 번만 지정할 수 있습니다.");
            }
        }
    }

    private int parsePage(String raw) {
        if (raw == null) {
            return 1;
        }
        int page = parseInt(raw, "page");
        if (page < 1) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "page", "1 이상의 값만 허용합니다.");
        }
        return page;
    }

    private int parseSize(String raw) {
        if (raw == null) {
            return 20;
        }
        int size = parseInt(raw, "size");
        if (!ALLOWED_SIZES.contains(size)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "size", "10, 20, 50 중 하나만 허용합니다.");
        }
        return size;
    }

    private int parseInt(String raw, String field) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "정수 값만 허용합니다.");
        }
    }

    private record PageRequestParams(int page, int size) {
    }
}
