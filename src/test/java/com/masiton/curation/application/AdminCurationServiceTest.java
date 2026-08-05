package com.masiton.curation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.common.idempotency.application.port.in.IdempotentCreationUseCase;
import com.masiton.common.web.BusinessException;
import com.masiton.curation.application.port.out.CurationStore;
import com.masiton.curation.application.port.out.CurationStore.StoredCuration;
import com.masiton.curation.application.port.out.CurationStore.StoredRestaurant;
import com.masiton.curation.domain.model.CurationStatus;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminCurationServiceTest {

    private static final UUID CURATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-05T00:00:00Z");

    @Mock CurationStore store;
    @Mock FindRestaurantReferenceUseCase restaurantReferences;
    @Mock IdempotentCreationUseCase idempotentCreation;
    private AdminCurationService service;

    @BeforeEach
    void setUp() {
        service = new AdminCurationService(store, restaurantReferences, idempotentCreation,
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("구성에 중복 맛집이 있으면 기존 구성을 변경하지 않는다")
    void 구성교체_중복맛집_저장안함() {
        UUID restaurantId = UUID.randomUUID();
        when(store.find(CURATION_ID, true)).thenReturn(Optional.of(draft()));

        assertThatThrownBy(() -> service.replaceRestaurants(CURATION_ID, ADMIN_ID,
                List.of(restaurantId, restaurantId), "trace-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("DUPLICATE_CURATION_RESTAURANT"));

        verify(store, never()).replaceRestaurants(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("구성 맛집은 공개·활성 상태여야 한다")
    void 구성교체_비공개맛집_맛집없음() {
        UUID restaurantId = UUID.randomUUID();
        when(store.find(CURATION_ID, true)).thenReturn(Optional.of(draft()));
        when(restaurantReferences.findRestaurantReferences(List.of(restaurantId)))
                .thenReturn(List.of(new FindRestaurantReferenceUseCase.RestaurantReference(restaurantId, false)));

        assertThatThrownBy(() -> service.replaceRestaurants(CURATION_ID, ADMIN_ID,
                List.of(restaurantId), "trace-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("RESTAURANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("구성 맛집 공개 여부는 식별자 수와 무관하게 한 번에 조회한다")
    void 구성교체_복수맛집_일괄조회() {
        UUID publicRestaurantId = UUID.randomUUID();
        UUID privateRestaurantId = UUID.randomUUID();
        List<UUID> restaurantIds = List.of(publicRestaurantId, privateRestaurantId);
        when(store.find(CURATION_ID, true)).thenReturn(Optional.of(draft()));
        when(restaurantReferences.findRestaurantReferences(restaurantIds)).thenReturn(List.of(
                new FindRestaurantReferenceUseCase.RestaurantReference(publicRestaurantId, true),
                new FindRestaurantReferenceUseCase.RestaurantReference(privateRestaurantId, false)));

        assertThatThrownBy(() -> service.replaceRestaurants(
                CURATION_ID, ADMIN_ID, restaurantIds, "trace-batch"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("RESTAURANT_NOT_FOUND"));

        verify(restaurantReferences, times(1)).findRestaurantReferences(restaurantIds);
        verify(store, never()).replaceRestaurants(any(), any(), any(), any());
    }

    @Test
    @DisplayName("구성 맛집이 20개를 초과하면 저장하지 않는다")
    void 구성교체_21개맛집_상한오류() {
        List<UUID> restaurantIds = java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> UUID.randomUUID())
                .toList();
        when(store.find(CURATION_ID, true)).thenReturn(Optional.of(draft()));

        assertThatThrownBy(() -> service.replaceRestaurants(
                CURATION_ID, ADMIN_ID, restaurantIds, "trace-limit"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("CURATION_RESTAURANT_LIMIT_EXCEEDED"));

        verify(store, never()).replaceRestaurants(any(), any(), any(), any());
    }

    @Test
    @DisplayName("게시 큐레이션이 5개이면 새 게시를 저장하지 않는다")
    void 게시_5개게시중_상한오류() {
        List<StoredCuration> published = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(position -> published(UUID.randomUUID(), position))
                .toList();
        when(store.find(CURATION_ID, true)).thenReturn(Optional.of(draft()));
        when(store.lockPublished()).thenReturn(published);

        assertThatThrownBy(() -> service.setPublication(
                CURATION_ID, ADMIN_ID, CurationStatus.PUBLISHED, "trace-limit"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("PUBLISHED_CURATION_LIMIT_EXCEEDED"));

        verify(store, never()).publish(any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    @DisplayName("메인 순서에 게시 큐레이션이 누락되면 전체 요청을 거부한다")
    void 메인순서_게시대상누락_거부() {
        StoredCuration first = published(CURATION_ID, 1);
        StoredCuration second = published(UUID.randomUUID(), 2);
        when(store.lockPublished()).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.replaceMainOrder(ADMIN_ID, List.of(CURATION_ID), "trace-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_MAIN_CURATION_ORDER"));

        verify(store, never()).replaceMainOrder(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("게시는 전역 메인 순서 잠금 후 대상과 게시 목록을 순서대로 잠금다")
    void 게시_동시요청_전역잠금으로직렬화() {
        when(store.find(CURATION_ID, true)).thenReturn(Optional.of(draft()));
        when(store.lockPublished()).thenReturn(List.of());
        when(store.find(CURATION_ID, false)).thenReturn(Optional.of(published(CURATION_ID, 1)));
        when(store.findRestaurants(CURATION_ID)).thenReturn(List.of());

        service.setPublication(CURATION_ID, ADMIN_ID, CurationStatus.PUBLISHED, "trace-1");

        InOrder order = inOrder(store);
        order.verify(store).lockMainOrder();
        order.verify(store).find(CURATION_ID, true);
        order.verify(store).lockPublished();
        order.verify(store).publish(org.mockito.ArgumentMatchers.eq(CURATION_ID),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(ADMIN_ID), any());
    }

    @Test
    @DisplayName("연결 후 비공개된 맛집은 관계를 보존하고 경고를 반환한다")
    void 관리자상세_맛집비공개_경고반환() {
        UUID restaurantId = UUID.randomUUID();
        when(store.find(CURATION_ID, false)).thenReturn(Optional.of(draft()));
        when(store.findRestaurants(CURATION_ID)).thenReturn(List.of(new StoredRestaurant(restaurantId, 1)));
        when(restaurantReferences.findRestaurantReferences(List.of(restaurantId))).thenReturn(List.of(
                new FindRestaurantReferenceUseCase.RestaurantReference(
                        restaurantId, "맛집", "서울 테스트로 1", "PRIVATE", false)));

        var detail = service.getCuration(CURATION_ID);

        assertThat(detail.items()).singleElement().satisfies(item -> {
            assertThat(item.availability()).isEqualTo("PRIVATE");
            assertThat(item.warning()).isEqualTo("공개 조회에서 숨김");
        });
    }

    private StoredCuration draft() {
        return new StoredCuration(CURATION_ID, "제목", "설명", CurationStatus.DRAFT, null,
                ADMIN_ID, ADMIN_ID, null, NOW, NOW);
    }

    private StoredCuration published(UUID id, int position) {
        return new StoredCuration(id, "제목", "설명", CurationStatus.PUBLISHED, position,
                ADMIN_ID, ADMIN_ID, NOW, NOW, NOW);
    }
}
