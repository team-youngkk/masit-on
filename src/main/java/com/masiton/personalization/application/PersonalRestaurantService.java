package com.masiton.personalization.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.personalization.application.port.in.PersonalRestaurantPage;
import com.masiton.personalization.application.port.in.PersonalRestaurantUseCase;
import com.masiton.personalization.application.port.in.RecordRecentRestaurantViewUseCase;
import com.masiton.personalization.application.port.out.PersonalRestaurantStore;

@Service
public class PersonalRestaurantService implements PersonalRestaurantUseCase, RecordRecentRestaurantViewUseCase {

    private static final int RECENT_LIMIT = 50;
    private final PersonalRestaurantStore store;
    private final Clock clock;

    @Autowired
    public PersonalRestaurantService(PersonalRestaurantStore store) {
        this(store, Clock.systemUTC());
    }

    PersonalRestaurantService(PersonalRestaurantStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean addFavorite(UUID memberId, UUID restaurantId) {
        requirePublicRestaurant(restaurantId);
        store.addFavorite(memberId, restaurantId, now());
        return true;
    }

    @Override
    @Transactional
    public boolean removeFavorite(UUID memberId, UUID restaurantId) {
        store.removeFavorite(memberId, restaurantId);
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFavorite(UUID memberId, UUID restaurantId) {
        requirePublicRestaurant(restaurantId);
        return store.existsFavorite(memberId, restaurantId);
    }

    @Override
    @Transactional(readOnly = true)
    public PersonalRestaurantPage getFavorites(UUID memberId, int page, int size) {
        return store.findFavorites(memberId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PersonalRestaurantPage getRecentRestaurants(UUID memberId, int page, int size) {
        OffsetDateTime cutoff = now().minusDays(30);
        return store.findRecentRestaurants(memberId, cutoff, RECENT_LIMIT, page, size);
    }

    @Override
    @Transactional
    public boolean removeRecentRestaurant(UUID memberId, UUID restaurantId) {
        store.removeRecentRestaurant(memberId, restaurantId);
        return false;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID memberId, UUID restaurantId, OffsetDateTime viewedAt) {
        store.upsertRecentRestaurant(memberId, restaurantId, viewedAt);
        store.pruneRecentRestaurantOverflow(memberId, RECENT_LIMIT);
    }

    private void requirePublicRestaurant(UUID restaurantId) {
        if (!store.isPublicRestaurant(restaurantId)) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "요청한 맛집을 찾을 수 없습니다.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
