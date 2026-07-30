package com.masiton.personalization.application;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.personalization.application.port.out.PersonalRestaurantStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("최근 본 맛집 보존 정리 서비스")
class RecentRestaurantViewCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    private final PersonalRestaurantStore store = mock(PersonalRestaurantStore.class);
    private final RecentRestaurantViewCleanupService service = new RecentRestaurantViewCleanupService(
            store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("주입된 시계 기준 30일 이전 기록 전체 삭제를 요청하고 삭제 건수를 반환한다")
    void 보존정리_실행_30일이전기록삭제건수를반환한다() {
        // given
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30);
        when(store.deleteRecentRestaurantViewsBefore(cutoff)).thenReturn(3);

        // when
        int deletedCount = service.cleanupExpiredViews();

        // then
        assertThat(deletedCount).isEqualTo(3);
        verify(store).deleteRecentRestaurantViewsBefore(cutoff);
    }

    @Test
    @DisplayName("보존 정리 Command는 쓰기 트랜잭션에서 실행한다")
    void 보존정리_트랜잭션설정_쓰기트랜잭션이다() throws NoSuchMethodException {
        // given
        Method cleanup = RecentRestaurantViewCleanupService.class
                .getMethod("cleanupExpiredViews");

        // when
        Transactional transaction = cleanup.getAnnotation(Transactional.class);

        // then
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }
}
