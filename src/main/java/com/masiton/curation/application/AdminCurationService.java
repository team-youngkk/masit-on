package com.masiton.curation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.idempotency.application.IdempotencyActorType;
import com.masiton.common.idempotency.application.IdempotencyApiScope;
import com.masiton.common.idempotency.application.IdempotencyRequest;
import com.masiton.common.idempotency.application.IdempotencyResponse;
import com.masiton.common.idempotency.application.port.in.IdempotentCreationUseCase;
import com.masiton.common.observability.OperationAuditLogger;
import com.masiton.common.observability.OperationAuditLogger.Entry;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.SafeTextPolicy;
import com.masiton.curation.application.port.in.AdminCurationUseCase;
import com.masiton.curation.application.port.out.CurationStore;
import com.masiton.curation.application.port.out.CurationStore.StoredCuration;
import com.masiton.curation.application.port.out.CurationStore.StoredCurationRestaurant;
import com.masiton.curation.domain.model.CurationStatus;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase.RestaurantReference;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminCurationService implements AdminCurationUseCase {

    private static final int RESTAURANT_LIMIT = 20;
    private static final int PUBLISHED_LIMIT = 5;

    private final CurationStore store;
    private final FindRestaurantReferenceUseCase restaurantReferences;
    private final IdempotentCreationUseCase idempotentCreation;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminCurationService(CurationStore store,
            FindRestaurantReferenceUseCase restaurantReferences, IdempotentCreationUseCase idempotentCreation,
            ObjectMapper objectMapper, @Qualifier("curationClock") Clock clock) {
        this.store = store;
        this.restaurantReferences = restaurantReferences;
        this.idempotentCreation = idempotentCreation;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public CreationResult create(UUID adminId, String idempotencyKey, String title, String description,
            String traceId) {
        UUID actor = requiredAdmin(adminId);
        String safeTitle = title(title);
        String safeDescription = description(description);
        String safeTraceId = requiredTraceId(traceId);
        IdempotencyRequest request = IdempotencyRequest.of(IdempotencyActorType.ADMIN, actor,
                IdempotencyApiScope.ADMIN_CURATIONS, idempotencyKey,
                hash(safeTitle + "\u0000" + safeDescription));
        return new CreationResult(idempotentCreation.execute(request, () -> {
            UUID id = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now(clock);
            store.create(id, safeTitle, safeDescription, actor, now);
            CurationDetail created = detail(store.find(id, false).orElseThrow(CurationException::notFound));
            log("CREATE", actor, id, "NONE", "DRAFT", 0, safeTraceId);
            return new IdempotencyResponse(201, serialize(created), id);
        }).response().body());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CurationSummary> getCurations(CurationStatus status, int page, int size) {
        List<CurationSummary> summaries = store.findPage(status, size, (long) (page - 1) * size);
        List<StoredCurationRestaurant> relations = store.findRestaurants(
                summaries.stream().map(CurationSummary::curationId).toList());
        Map<UUID, RestaurantReference> references = references(
                relations.stream().map(StoredCurationRestaurant::restaurantId).distinct().toList());
        Set<UUID> hiddenCurations = relations.stream()
                .filter(relation -> !isPublic(references.get(relation.restaurantId())))
                .map(StoredCurationRestaurant::curationId)
                .collect(java.util.stream.Collectors.toSet());
        List<CurationSummary> items = summaries.stream()
                .map(summary -> new CurationSummary(summary.curationId(), summary.title(), summary.description(),
                        summary.status(), summary.mainPosition(), summary.restaurantCount(),
                        hiddenCurations.contains(summary.curationId()), summary.publishedAt(), summary.updatedAt()))
                .toList();
        return new Page<>(items, page, size, store.count(status));
    }

    @Override
    @Transactional(readOnly = true)
    public CurationDetail getCuration(UUID curationId) {
        return detail(store.find(curationId, false).orElseThrow(CurationException::notFound));
    }

    @Override
    @Transactional
    public CurationDetail updateContent(UUID curationId, UUID adminId, String newTitle, String newDescription,
            String traceId) {
        StoredCuration current = locked(curationId);
        UUID actor = requiredAdmin(adminId);
        if (newTitle == null && newDescription == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "body", "title과 description 중 하나 이상이 필요합니다.");
        }
        String safeTitle = newTitle == null ? current.title() : title(newTitle);
        String safeDescription = newDescription == null ? current.description() : description(newDescription);
        OffsetDateTime now = OffsetDateTime.now(clock);
        store.updateContent(curationId, safeTitle, safeDescription, actor, now);
        log("CONTENT_UPDATE", actor, curationId, contentMetadata(current.title(), current.description()),
                contentMetadata(safeTitle, safeDescription), null, requiredTraceId(traceId));
        return getCuration(curationId);
    }

    @Override
    @Transactional
    public CurationDetail replaceRestaurants(UUID curationId, UUID adminId, List<UUID> restaurantIds,
            String traceId) {
        locked(curationId);
        UUID actor = requiredAdmin(adminId);
        List<UUID> ids = restaurantIds == null ? null : List.copyOf(restaurantIds);
        if (ids == null) throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "restaurantIds", "필수 입력값입니다.");
        if (ids.size() > RESTAURANT_LIMIT) throw CurationException.restaurantLimit();
        if (ids.stream().anyMatch(java.util.Objects::isNull)) throw CurationException.restaurantNotFound();
        if (new HashSet<>(ids).size() != ids.size()) throw CurationException.duplicateRestaurant();
        Map<UUID, RestaurantReference> references = references(ids);
        if (references.size() != ids.size() || ids.stream().anyMatch(id -> !isPublic(references.get(id)))) {
            throw CurationException.restaurantNotFound();
        }
        List<UUID> beforeIds = store.findRestaurants(curationId).stream()
                .map(CurationStore.StoredRestaurant::restaurantId).toList();
        store.replaceRestaurants(curationId, ids, actor, OffsetDateTime.now(clock));
        log("RESTAURANTS_REPLACE", actor, curationId, orderedIds(beforeIds), orderedIds(ids),
                ids.size(), requiredTraceId(traceId));
        return getCuration(curationId);
    }

    @Override
    @Transactional
    public CurationDetail setPublication(UUID curationId, UUID adminId, CurationStatus target, String traceId) {
        UUID actor = requiredAdmin(adminId);
        if (target == null) throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "status", "필수 입력값입니다.");
        store.lockMainOrder();
        StoredCuration current = locked(curationId);
        if (current.status() == target) return detail(current);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (target == CurationStatus.PUBLISHED) {
            List<StoredCuration> published = store.lockPublished();
            if (published.size() >= PUBLISHED_LIMIT) throw CurationException.publicationLimit();
            store.publish(curationId, published.size() + 1, actor, now);
        } else {
            store.unpublish(curationId, current.mainPosition(), actor, now);
        }
        CurationDetail updated = getCuration(curationId);
        log("PUBLICATION_UPDATE", actor, curationId,
                "{status=" + current.status() + ",mainPosition=" + current.mainPosition() + "}",
                "{status=" + updated.status() + ",mainPosition=" + updated.mainPosition() + "}",
                null, requiredTraceId(traceId));
        return updated;
    }

    @Override
    @Transactional
    public List<CurationSummary> replaceMainOrder(UUID adminId, List<UUID> curationIds, String traceId) {
        UUID actor = requiredAdmin(adminId);
        if (curationIds == null || curationIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(curationIds).size() != curationIds.size()) {
            throw CurationException.invalidMainOrder();
        }
        store.lockMainOrder();
        List<StoredCuration> published = store.lockPublished();
        Set<UUID> actual = published.stream().map(StoredCuration::id).collect(java.util.stream.Collectors.toSet());
        if (curationIds.size() != actual.size() || !actual.equals(new HashSet<>(curationIds))) {
            throw CurationException.invalidMainOrder();
        }
        String before = published.stream().sorted(java.util.Comparator.comparing(StoredCuration::mainPosition))
                .map(value -> value.id().toString()).collect(java.util.stream.Collectors.joining(","));
        store.replaceMainOrder(curationIds, actor, OffsetDateTime.now(clock));
        log("MAIN_ORDER_REPLACE", actor, "MAIN_ORDER", before, orderedIds(curationIds),
                curationIds.size(), requiredTraceId(traceId));
        return getCurations(CurationStatus.PUBLISHED, 1, PUBLISHED_LIMIT).items().stream()
                .sorted(java.util.Comparator.comparing(CurationSummary::mainPosition)).toList();
    }

    private StoredCuration locked(UUID id) {
        return store.find(id, true).orElseThrow(CurationException::notFound);
    }

    private CurationDetail detail(StoredCuration value) {
        List<CurationStore.StoredRestaurant> relations = store.findRestaurants(value.id());
        Map<UUID, RestaurantReference> projections = references(
                relations.stream().map(CurationStore.StoredRestaurant::restaurantId).toList());
        List<RestaurantItem> items = relations.stream().map(relation -> {
            RestaurantReference restaurant = projections.get(relation.restaurantId());
            String availability = restaurant == null ? "INACTIVE" : restaurant.availability();
            String name = restaurant == null ? null : restaurant.name();
            String warning = isPublic(restaurant) ? null : "공개 조회에서 숨김";
            return new RestaurantItem(relation.restaurantId(), relation.position(), name, availability, warning);
        }).toList();
        return new CurationDetail(value.id(), value.title(), value.description(), value.status(),
                value.mainPosition(), value.publishedAt(), value.updatedAt(), items);
    }

    private Map<UUID, RestaurantReference> references(List<UUID> restaurantIds) {
        Map<UUID, RestaurantReference> references = new HashMap<>();
        if (restaurantIds.isEmpty()) return references;
        restaurantReferences.findRestaurantReferences(restaurantIds)
                .forEach(reference -> references.put(reference.id(), reference));
        return references;
    }

    private boolean isPublic(RestaurantReference reference) {
        return reference != null && reference.publiclyVisible();
    }

    private String title(String value) { return normalized(value, "title", 1, 100); }
    private String description(String value) { return normalized(value == null ? "" : value, "description", 0, 1000); }
    private String normalized(String value, String field, int min, int max) {
        String normalized = SafeTextPolicy.requireSafe(value, field);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < min || length > max) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field,
                    "앞뒤 공백을 제외하고 " + min + "~" + max + "자로 입력해 주세요.");
        }
        return normalized;
    }
    private UUID requiredAdmin(UUID id) { if (id == null) throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED); return id; }
    private String requiredTraceId(String value) { if (value == null || value.isBlank()) throw new IllegalStateException("Server traceId is required"); return value; }
    private byte[] hash(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private String serialize(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JacksonException e) { throw new IllegalStateException("Curation response could not be serialized", e); } }
    private String contentMetadata(String title, String description) {
        return "{titleLengthBucket=" + lengthBucket(title)
                + ",descriptionLengthBucket=" + lengthBucket(description) + "}";
    }
    private String lengthBucket(String value) {
        int length = value.codePointCount(0, value.length());
        if (length == 0) return "0";
        if (length <= 20) return "1-20";
        if (length <= 50) return "21-50";
        if (length <= 100) return "51-100";
        if (length <= 250) return "101-250";
        if (length <= 500) return "251-500";
        return "501-1000";
    }
    private String orderedIds(List<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
    private void log(String action, UUID adminId, Object targetId, String before, String after,
            Integer count, String traceId) {
        OperationAuditLogger.write(new Entry(action, "ADMIN", adminId, "CURATION", targetId,
                before, after, null, count, traceId));
    }
}
