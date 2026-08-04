package com.masiton.personal.application.port.in;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PersonalCollectionUseCase {

    CreationResult create(UUID memberId, String idempotencyKey, String name);

    List<CollectionSummary> getCollections(UUID memberId);

    CollectionDetail getCollection(UUID memberId, UUID collectionId, int page, int size);

    CollectionSummary rename(UUID memberId, UUID collectionId, String name);

    void delete(UUID memberId, UUID collectionId);

    CollectionRestaurant addRestaurant(UUID memberId, UUID collectionId, UUID restaurantId);

    void removeRestaurant(UUID memberId, UUID collectionId, UUID restaurantId);

    record CreationResult(String responseBody) {
    }

    record CollectionSummary(UUID collectionId, String name, long restaurantCount,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    record CollectionRestaurant(UUID collectionId, UUID restaurantId, OffsetDateTime addedAt) {
    }

    record RestaurantItem(UUID restaurantId, String name, String roadAddress, OffsetDateTime addedAt) {
    }

    record CollectionDetail(UUID collectionId, String name, long restaurantCount,
                            OffsetDateTime updatedAt, List<RestaurantItem> items,
                            int pageNumber, int pageSize, long totalElements,
                            int totalPages, boolean hasNext) {
    }
}
