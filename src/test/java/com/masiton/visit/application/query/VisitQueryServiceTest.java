package com.masiton.visit.application.query;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.visit.application.port.in.CreatorRestaurantCandidates;
import com.masiton.visit.application.port.out.VisitQueryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * VisitQueryPort를 Mockito로 대역해 VisitQueryService의 중복 제거·creatorPublic 조합 로직만
 * 검증한다. 실제 공개·유효 판정 SQL 조건은 VisitQueryIntegrationTest가 검증한다.
 * 맛집 상세 콘텐츠(방문 유튜버·관련 영상) 조회 로직은 orchestration.application.query
 * .VisitContentQueryServiceTest가 검증한다(query-composition.md 5절에 따라 이관됨).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VisitQueryService")
class VisitQueryServiceTest {

    @Mock
    private VisitQueryPort visitQueryPort;

    @Test
    @DisplayName("Creator기준_후보조회_Port가반환한목록을중복없는집합으로변환한다")
    void Creator기준_후보조회_Port가반환한목록을중복없는집합으로변환한다() {
        // given
        VisitQueryService service = new VisitQueryService(visitQueryPort);
        UUID creatorId = UUID.randomUUID();
        UUID restaurantId1 = UUID.randomUUID();
        UUID restaurantId2 = UUID.randomUUID();
        given(visitQueryPort.isCreatorPubliclyVisible(creatorId)).willReturn(true);
        given(visitQueryPort.findDistinctValidRestaurantIdsByCreatorId(creatorId))
                .willReturn(List.of(restaurantId1, restaurantId2, restaurantId1));

        // when
        CreatorRestaurantCandidates result = service.findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.creatorPublic()).isTrue();
        assertThat(result.restaurantIds()).containsExactlyInAnyOrder(restaurantId1, restaurantId2);
        verify(visitQueryPort).findDistinctValidRestaurantIdsByCreatorId(creatorId);
    }

    @Test
    @DisplayName("Creator기준_후보조회_공개Creator이지만관계없으면creatorPublic참에빈집합을반환한다")
    void Creator기준_후보조회_공개Creator이지만관계없으면creatorPublic참에빈집합을반환한다() {
        // given
        VisitQueryService service = new VisitQueryService(visitQueryPort);
        UUID creatorId = UUID.randomUUID();
        given(visitQueryPort.isCreatorPubliclyVisible(creatorId)).willReturn(true);
        given(visitQueryPort.findDistinctValidRestaurantIdsByCreatorId(creatorId)).willReturn(List.of());

        // when
        CreatorRestaurantCandidates result = service.findDistinctValidRestaurantIdsByCreator(creatorId);

        // then: 관계 없음(정상 빈 목록)과 아래의 "존재하지 않거나 비공개" 케이스를 creatorPublic으로 구분한다
        assertThat(result.creatorPublic()).isTrue();
        assertThat(result.restaurantIds()).isEmpty();
    }

    @Test
    @DisplayName("Creator기준_후보조회_존재하지않거나비공개Creator이면creatorPublic거짓을반환한다")
    void Creator기준_후보조회_존재하지않거나비공개Creator이면creatorPublic거짓을반환한다() {
        // given: creator-discovery-api.md 127행 근거 — 이 경우 호출자(WS-01)가 400으로 처리해야 한다
        VisitQueryService service = new VisitQueryService(visitQueryPort);
        UUID creatorId = UUID.randomUUID();
        given(visitQueryPort.isCreatorPubliclyVisible(creatorId)).willReturn(false);
        given(visitQueryPort.findDistinctValidRestaurantIdsByCreatorId(creatorId)).willReturn(List.of());

        // when
        CreatorRestaurantCandidates result = service.findDistinctValidRestaurantIdsByCreator(creatorId);

        // then
        assertThat(result.creatorPublic()).isFalse();
        assertThat(result.restaurantIds()).isEmpty();
    }
}
