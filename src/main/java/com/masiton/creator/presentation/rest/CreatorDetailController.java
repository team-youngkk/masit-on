package com.masiton.creator.presentation.rest;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.creator.application.port.in.GetPublicCreatorDetailUseCase;

/**
 * API-CREATOR-DETAIL-001 유튜버 기본 상세 조회의 입력 Adapter다. 식별자 형식 검증과 HTTP
 * 변환만 수행하고 공개 판정·조회는 입력 Port({@link GetPublicCreatorDetailUseCase})에
 * 위임한다. RestaurantDetailController.parseRestaurantId와 같은 방식으로 UUID 형식이
 * 아닌 식별자를 400 INVALID_IDENTIFIER로 거부한다.
 */
@RestController
public class CreatorDetailController {

    private final GetPublicCreatorDetailUseCase getPublicCreatorDetailUseCase;

    public CreatorDetailController(GetPublicCreatorDetailUseCase getPublicCreatorDetailUseCase) {
        this.getPublicCreatorDetailUseCase = getPublicCreatorDetailUseCase;
    }

    @GetMapping("/api/creators/{creatorId}")
    public CreatorDetailResponse getCreatorDetail(@PathVariable String creatorId) {
        UUID id = parseCreatorId(creatorId);
        return CreatorDetailResponse.from(getPublicCreatorDetailUseCase.getPublicCreatorDetail(id));
    }

    private UUID parseCreatorId(String creatorId) {
        try {
            return UUID.fromString(creatorId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
    }
}
