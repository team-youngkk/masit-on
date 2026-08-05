package com.masiton.curation.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.curation.application.port.in.PublicCurationUseCase;
import com.masiton.curation.application.port.out.CurationStore;
import com.masiton.curation.application.port.out.CurationStore.StoredCuration;
import com.masiton.curation.application.port.out.CurationStore.StoredCurationRestaurant;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase.RestaurantReference;

@Service
@Transactional(readOnly = true)
public class PublicCurationService implements PublicCurationUseCase {

    private static final int MAIN_CURATION_LIMIT = 5;

    private final CurationStore store;
    private final FindRestaurantReferenceUseCase restaurantReferences;

    public PublicCurationService(
            CurationStore store,
            FindRestaurantReferenceUseCase restaurantReferences
    ) {
        this.store = store;
        this.restaurantReferences = restaurantReferences;
    }

    @Override
    public List<PublicCuration> getPublishedCurations() {
        List<StoredCuration> curations = store.findPublished(MAIN_CURATION_LIMIT);
        List<StoredCurationRestaurant> relations = store.findRestaurants(
                curations.stream().map(StoredCuration::id).toList());
        return assemble(curations, relations);
    }

    @Override
    public PublicCuration getPublishedCuration(UUID curationId) {
        StoredCuration curation = store.findPublished(curationId).orElseThrow(CurationException::notFound);
        List<StoredCurationRestaurant> relations = store.findRestaurants(List.of(curationId));
        return assemble(List.of(curation), relations).getFirst();
    }

    private List<PublicCuration> assemble(
            List<StoredCuration> curations,
            List<StoredCurationRestaurant> relations
    ) {
        Map<UUID, RestaurantReference> references = references(relations.stream()
                .map(StoredCurationRestaurant::restaurantId)
                .distinct()
                .toList());
        Map<UUID, List<StoredCurationRestaurant>> relationsByCuration = relations.stream()
                .collect(java.util.stream.Collectors.groupingBy(StoredCurationRestaurant::curationId));

        return curations.stream().map(curation -> new PublicCuration(
                curation.id(),
                curation.title(),
                curation.description(),
                relationsByCuration.getOrDefault(curation.id(), List.of()).stream()
                        .sorted(java.util.Comparator.comparingInt(StoredCurationRestaurant::position))
                        .map(relation -> references.get(relation.restaurantId()))
                        .filter(this::isPublic)
                        .map(reference -> new RestaurantItem(
                                reference.id(), reference.name(), reference.roadAddress()))
                        .toList(),
                curation.publishedAt(),
                curation.updatedAt()))
                .toList();
    }

    private Map<UUID, RestaurantReference> references(List<UUID> restaurantIds) {
        Map<UUID, RestaurantReference> references = new HashMap<>();
        if (!restaurantIds.isEmpty()) {
            restaurantReferences.findRestaurantReferences(restaurantIds)
                    .forEach(reference -> references.put(reference.id(), reference));
        }
        return references;
    }

    private boolean isPublic(RestaurantReference reference) {
        return reference != null && reference.publiclyVisible();
    }
}
