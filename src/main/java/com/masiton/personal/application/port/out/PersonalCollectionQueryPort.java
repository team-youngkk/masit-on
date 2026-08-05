package com.masiton.personal.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.masiton.personal.application.port.in.CollectionOption;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionDetail;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionSummary;

public interface PersonalCollectionQueryPort {

    List<CollectionSummary> findAll(UUID memberId);

    List<CollectionOption> findOptions(UUID memberId, UUID restaurantId);

    Optional<CollectionSummary> findSummary(UUID memberId, UUID collectionId);

    Optional<CollectionDetail> findDetail(UUID memberId, UUID collectionId, int page, int size);
}
