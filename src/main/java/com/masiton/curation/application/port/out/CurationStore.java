package com.masiton.curation.application.port.out;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.masiton.curation.application.port.in.AdminCurationUseCase.CurationSummary;
import com.masiton.curation.domain.model.CurationStatus;

public interface CurationStore {
    void create(UUID id, String title, String description, UUID adminId, OffsetDateTime now);
    Optional<StoredCuration> find(UUID id, boolean lock);
    List<StoredCuration> findPublished(int limit);
    Optional<StoredCuration> findPublished(UUID id);
    List<CurationSummary> findPage(CurationStatus status, int limit, long offset);
    long count(CurationStatus status);
    List<StoredRestaurant> findRestaurants(UUID curationId);
    List<StoredCurationRestaurant> findRestaurants(Collection<UUID> curationIds);
    void updateContent(UUID id, String title, String description, UUID adminId, OffsetDateTime now);
    void replaceRestaurants(UUID id, List<UUID> restaurantIds, UUID adminId, OffsetDateTime now);
    void lockMainOrder();
    List<StoredCuration> lockPublished();
    void publish(UUID id, int position, UUID adminId, OffsetDateTime now);
    void unpublish(UUID id, int oldPosition, UUID adminId, OffsetDateTime now);
    void replaceMainOrder(List<UUID> orderedIds, UUID adminId, OffsetDateTime now);

    record StoredCuration(UUID id, String title, String description, CurationStatus status,
            Integer mainPosition, UUID createdBy, UUID updatedBy, OffsetDateTime publishedAt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
    record StoredRestaurant(UUID restaurantId, int position) { }
    record StoredCurationRestaurant(UUID curationId, UUID restaurantId, int position) { }
}
