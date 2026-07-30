package com.masiton.personal.application;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.personal.application.port.out.PersonalRestaurantStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("회원 개인 맛집 서비스")
class PersonalRestaurantServiceTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    private final PersonalRestaurantStore store = mock(PersonalRestaurantStore.class);
    private final PersonalRestaurantService service = new PersonalRestaurantService(
            store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("공개 맛집을 찜하면 현재 시각으로 저장을 요청한다")
    void 찜추가_공개맛집_현재시각으로저장한다() {
        // given
        when(store.isPublicRestaurant(RESTAURANT_ID)).thenReturn(true);

        // when
        service.addFavorite(MEMBER_ID, RESTAURANT_ID);

        // then
        verify(store).addFavorite(
                MEMBER_ID, RESTAURANT_ID, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("비공개이거나 없는 맛집은 찜을 저장하지 않고 찾을 수 없음으로 처리한다")
    void 찜추가_공개맛집아님_저장하지않고예외를던진다() {
        // given
        when(store.isPublicRestaurant(RESTAURANT_ID)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.addFavorite(MEMBER_ID, RESTAURANT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("RESTAURANT_NOT_FOUND"));
        verify(store, never()).addFavorite(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("찜 목록 조회는 정리 명령 없이 목록만 조회한다")
    void 찜목록조회_정상요청_목록만조회한다() {
        // when
        service.getFavorites(MEMBER_ID, 1, 20);

        // then
        verify(store).findFavorites(MEMBER_ID, 1, 20);
        verify(store, never()).pruneRecentRestaurantOverflow(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("최근 목록 조회는 삭제 없이 현재 시각 기준 30일과 최신 50건을 필터링한다")
    void 최근목록조회_정상요청_조회조건만전달한다() {
        // when
        service.getRecentRestaurants(MEMBER_ID, 2, 10);

        // then
        verify(store).findRecentRestaurants(
                MEMBER_ID, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30), 50, 2, 10);
        verify(store, never()).pruneRecentRestaurantOverflow(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("찜과 최근 목록 조회는 읽기 전용 트랜잭션이다")
    void 목록조회_트랜잭션설정_읽기전용이다() throws NoSuchMethodException {
        // given
        Method favorites = PersonalRestaurantService.class
                .getMethod("getFavorites", UUID.class, int.class, int.class);
        Method recent = PersonalRestaurantService.class
                .getMethod("getRecentRestaurants", UUID.class, int.class, int.class);

        // when
        Transactional favoritesTransaction = favorites.getAnnotation(Transactional.class);
        Transactional recentTransaction = recent.getAnnotation(Transactional.class);

        // then
        assertThat(favoritesTransaction).isNotNull();
        assertThat(favoritesTransaction.readOnly()).isTrue();
        assertThat(recentTransaction).isNotNull();
        assertThat(recentTransaction.readOnly()).isTrue();
    }
}
