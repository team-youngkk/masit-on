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

import com.masiton.personal.application.port.out.PersonalRestaurantStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("최근 본 맛집 기록 서비스")
class RecordRecentRestaurantViewServiceTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    private final PersonalRestaurantStore store = mock(PersonalRestaurantStore.class);
    private final RecordRecentRestaurantViewService service = new RecordRecentRestaurantViewService(
            store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("주입된 시계로 최근 기록을 갱신한 뒤 회원별 최신 50건만 유지한다")
    void 최근기록_정상조회_현재시각으로갱신하고최신50건만유지한다() {
        // when
        service.record(MEMBER_ID, RESTAURANT_ID);

        // then
        OffsetDateTime viewedAt = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        var ordered = inOrder(store);
        ordered.verify(store).lockMember(MEMBER_ID);
        ordered.verify(store).upsertRecentRestaurant(MEMBER_ID, RESTAURANT_ID, viewedAt);
        ordered.verify(store).pruneRecentRestaurantOverflow(MEMBER_ID, 50);
        verify(store, never()).deleteRecentRestaurantViewsBefore(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("최근 기록 갱신과 초과분 정리는 하나의 쓰기 트랜잭션에서 실행한다")
    void 최근기록_트랜잭션설정_쓰기트랜잭션이다() throws NoSuchMethodException {
        // given
        Method record = RecordRecentRestaurantViewService.class
                .getMethod("record", UUID.class, UUID.class);

        // when
        Transactional transaction = record.getAnnotation(Transactional.class);

        // then
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }
}
