package com.masiton.creator.application.port.in;

import java.util.List;

/**
 * FR-CREATOR-003 유튜버 필터 선택 목록 조회 유스케이스다.
 */
public interface GetPublicCreatorSelectionListUseCase {

    List<CreatorSelectionItem> getPublicSelectionList();
}
