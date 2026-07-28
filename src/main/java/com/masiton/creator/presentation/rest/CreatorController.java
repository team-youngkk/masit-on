package com.masiton.creator.presentation.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.creator.application.port.in.GetPublicCreatorSelectionListUseCase;

import jakarta.servlet.http.HttpServletRequest;

/**
 * API-CREATOR-DISCOVERY-001. 검색·페이지네이션을 지원하지 않으므로 쿼리 파라미터가 하나라도
 * 오면 계약대로 400 INVALID_REQUEST로 거부한다.
 */
@RestController
public class CreatorController {

    private final GetPublicCreatorSelectionListUseCase getPublicCreatorSelectionListUseCase;

    public CreatorController(GetPublicCreatorSelectionListUseCase getPublicCreatorSelectionListUseCase) {
        this.getPublicCreatorSelectionListUseCase = getPublicCreatorSelectionListUseCase;
    }

    @GetMapping("/api/creators")
    public CreatorSelectionListResponse getCreators(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<CreatorSelectionItemResponse> items = getPublicCreatorSelectionListUseCase.getPublicSelectionList()
                .stream()
                .map(item -> new CreatorSelectionItemResponse(item.id().toString(), item.channelName()))
                .toList();
        return new CreatorSelectionListResponse(items);
    }
}
