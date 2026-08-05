package com.masiton.personal.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.masiton.personal.application.port.in.CollectionOption;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionDetail;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionRestaurant;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionSummary;

public interface PersonalCollectionStore {

    CollectionSummary create(UUID memberId, UUID collectionId, String name, OffsetDateTime now);

    List<CollectionSummary> findAll(UUID memberId);

    List<CollectionOption> findOptions(UUID memberId, UUID restaurantId);

    Optional<CollectionDetail> findDetail(UUID memberId, UUID collectionId, int page, int size);

    Optional<CollectionSummary> rename(UUID memberId, UUID collectionId, String name, OffsetDateTime now);

    void delete(UUID memberId, UUID collectionId);

    Optional<CollectionRestaurant> findRestaurant(UUID memberId, UUID collectionId, UUID restaurantId);

    Optional<CollectionRestaurant> addRestaurant(
            UUID memberId, UUID collectionId, UUID restaurantId, OffsetDateTime now);

    void removeRestaurant(UUID memberId, UUID collectionId, UUID restaurantId, OffsetDateTime now);
}
