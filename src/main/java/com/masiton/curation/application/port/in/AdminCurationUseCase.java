package com.masiton.curation.application.port.in;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.masiton.curation.domain.model.CurationStatus;

public interface AdminCurationUseCase {

    CreationResult create(UUID adminId, String idempotencyKey, String title, String description, String traceId);
    Page<CurationSummary> getCurations(CurationStatus status, int page, int size);
    CurationDetail getCuration(UUID curationId);
    CurationDetail updateContent(UUID curationId, UUID adminId, String title, String description, String traceId);
    CurationDetail replaceRestaurants(UUID curationId, UUID adminId, List<UUID> restaurantIds, String traceId);
    CurationDetail setPublication(UUID curationId, UUID adminId, CurationStatus status, String traceId);
    List<CurationSummary> replaceMainOrder(UUID adminId, List<UUID> curationIds, String traceId);

    record CreationResult(String responseBody) { }
    record CurationSummary(UUID curationId, String title, String description, CurationStatus status,
            Integer mainPosition, int restaurantCount, boolean hasHiddenRestaurants,
            OffsetDateTime publishedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
    record RestaurantItem(UUID restaurantId, int position, String name, String availability, String warning) { }
    record CurationDetail(UUID curationId, String title, String description, CurationStatus status,
            Integer mainPosition, UUID createdBy, UUID updatedBy, OffsetDateTime publishedAt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, List<RestaurantItem> items) { }
    record Page<T>(List<T> items, int number, int size, long totalElements, int totalPages, boolean hasNext) {
        public Page(List<T> items, int number, int size, long totalElements) {
            this(List.copyOf(items), number, size, totalElements,
                    totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size),
                    (long) number * size < totalElements);
        }
    }
}
