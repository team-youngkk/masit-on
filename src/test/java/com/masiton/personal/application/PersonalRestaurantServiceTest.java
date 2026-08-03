package com.masiton.personal.application;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.personal.application.port.out.PersonalRestaurantQueryPort;
import com.masiton.personal.application.port.out.PersonalRestaurantStore;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;

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
    private final PersonalRestaurantQueryPort queries = mock(PersonalRestaurantQueryPort.class);
    private final FindRestaurantReferenceUseCase restaurantReferences = mock(FindRestaurantReferenceUseCase.class);
    private final PersonalRestaurantService service = new PersonalRestaurantService(
            store,
            queries,
            restaurantReferences,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("공개 맛집만 단건 개인화 대상에 허용한다")
    void addFavorite_publicRestaurant_addsWithCurrentTime() {
        when(restaurantReferences.findRestaurantReference(RESTAURANT_ID))
                .thenReturn(Optional.of(new FindRestaurantReferenceUseCase.RestaurantReference(RESTAURANT_ID, true)));

        service.addFavorite(MEMBER_ID, RESTAURANT_ID);

        verify(store).addFavorite(
                MEMBER_ID,
                RESTAURANT_ID,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("비공개 또는 없는 맛집은 RESTAURANT_NOT_FOUND로 거부한다")
    void addFavorite_nonPublicRestaurant_rejected() {
        when(restaurantReferences.findRestaurantReference(RESTAURANT_ID))
                .thenReturn(Optional.of(new FindRestaurantReferenceUseCase.RestaurantReference(RESTAURANT_ID, false)));

        assertThatThrownBy(() -> service.addFavorite(MEMBER_ID, RESTAURANT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("RESTAURANT_NOT_FOUND"));

        verify(store, never()).addFavorite(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("찜 목록 조회는 orchestration query 포트만 호출한다")
    void getFavorites_delegatesToQueryPort() {
        service.getFavorites(MEMBER_ID, 1, 20);

        verify(queries).findFavorites(MEMBER_ID, 1, 20);
        verify(store, never()).pruneRecentRestaurantOverflow(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("최근 목록 조회는 30일 cutoff와 최신 50개 제한을 query 포트에 전달한다")
    void getRecentRestaurants_delegatesToQueryPort() {
        service.getRecentRestaurants(MEMBER_ID, 2, 10);

        verify(queries).findRecentRestaurants(
                MEMBER_ID,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30),
                50,
                2,
                10);
    }

    @Test
    @DisplayName("목록 조회는 읽기 전용 트랜잭션이다")
    void listQueries_areReadOnlyTransactions() throws NoSuchMethodException {
        Method favorites = PersonalRestaurantService.class
                .getMethod("getFavorites", UUID.class, int.class, int.class);
        Method recent = PersonalRestaurantService.class
                .getMethod("getRecentRestaurants", UUID.class, int.class, int.class);

        Transactional favoritesTransaction = favorites.getAnnotation(Transactional.class);
        Transactional recentTransaction = recent.getAnnotation(Transactional.class);

        assertThat(favoritesTransaction).isNotNull();
        assertThat(favoritesTransaction.readOnly()).isTrue();
        assertThat(recentTransaction).isNotNull();
        assertThat(recentTransaction.readOnly()).isTrue();
    }
}
