package com.masiton.orchestration.presentation.detail;

import java.util.List;

import com.masiton.orchestration.application.port.in.CreatorEvidenceVideoItem;
import com.masiton.orchestration.application.port.in.CreatorEvidenceVideosResult;

/**
 * API-CREATOR-DETAIL-003 성공 응답 계약이다. response-contract.md·pagination-contract.md의
 * 목록 응답 형태와 정확히 일치해야 한다.
 */
record CreatorEvidenceVideosResponse(List<Item> items, PageInfo page) {

    static CreatorEvidenceVideosResponse from(CreatorEvidenceVideosResult result) {
        List<Item> items = result.items().stream().map(Item::from).toList();
        return new CreatorEvidenceVideosResponse(
                items,
                new PageInfo(
                        result.pageNumber(),
                        result.pageSize(),
                        result.totalElements(),
                        result.totalPages(),
                        result.hasNext()));
    }

    record Item(String id, String title, String thumbnailUrl, String sourceUrl) {

        static Item from(CreatorEvidenceVideoItem item) {
            return new Item(item.id().toString(), item.title(), item.thumbnailUrl(), item.sourceUrl());
        }
    }

    record PageInfo(int number, int size, long totalElements, int totalPages, boolean hasNext) {
    }
}
