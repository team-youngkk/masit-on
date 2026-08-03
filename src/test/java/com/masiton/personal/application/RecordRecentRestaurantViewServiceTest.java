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

import com.masiton.member.application.port.in.LockActiveMemberUseCase;
import com.masiton.personal.application.port.out.PersonalRestaurantStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@DisplayName("최근 본 맛집 기록 서비스")
class RecordRecentRestaurantViewServiceTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    private final LockActiveMemberUseCase activeMembers = mock(LockActiveMemberUseCase.class);
    private final PersonalRestaurantStore store = mock(PersonalRestaurantStore.class);
    private final RecordRecentRestaurantViewService service = new RecordRecentRestaurantViewService(
            activeMembers, store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("활성 회원 lock 뒤에 최근 기록 갱신과 초과분 정리를 수행한다")
    void record_locksMemberThenUpsertsAndPrunes() {
        service.record(MEMBER_ID, RESTAURANT_ID);

        OffsetDateTime viewedAt = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        var ordered = inOrder(activeMembers, store);
        ordered.verify(activeMembers).lockActiveMember(MEMBER_ID);
        ordered.verify(store).upsertRecentRestaurant(MEMBER_ID, RESTAURANT_ID, viewedAt);
        ordered.verify(store).pruneRecentRestaurantOverflow(MEMBER_ID, 50);
        verify(store, never()).deleteRecentRestaurantViewsBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("최근 기록 갱신과 초과분 정리는 쓰기 트랜잭션이다")
    void record_usesWriteTransaction() throws NoSuchMethodException {
        Method record = RecordRecentRestaurantViewService.class
                .getMethod("record", UUID.class, UUID.class);

        Transactional transaction = record.getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }
}
