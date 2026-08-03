package com.masiton.personal.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.member.application.port.in.LockActiveMemberUseCase;
import com.masiton.personal.application.port.in.RecordRecentRestaurantViewUseCase;
import com.masiton.personal.application.port.out.PersonalRestaurantStore;

@Service
public class RecordRecentRestaurantViewService implements RecordRecentRestaurantViewUseCase {
    private static final int RECENT_LIMIT = 50;

    private final LockActiveMemberUseCase activeMembers;
    private final PersonalRestaurantStore store;
    private final Clock clock;

    public RecordRecentRestaurantViewService(
            LockActiveMemberUseCase activeMembers,
            PersonalRestaurantStore store,
            @Qualifier("personalizationClock") Clock clock
    ) {
        this.activeMembers = activeMembers;
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(UUID memberId, UUID restaurantId) {
        OffsetDateTime viewedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        activeMembers.lockActiveMember(memberId);
        store.upsertRecentRestaurant(memberId, restaurantId, viewedAt);
        store.pruneRecentRestaurantOverflow(memberId, RECENT_LIMIT);
    }
}
