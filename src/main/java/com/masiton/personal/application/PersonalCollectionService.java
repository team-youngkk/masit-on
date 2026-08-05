package com.masiton.personal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.idempotency.application.IdempotencyActorType;
import com.masiton.common.idempotency.application.IdempotencyApiScope;
import com.masiton.common.idempotency.application.IdempotencyRequest;
import com.masiton.common.idempotency.application.IdempotencyResponse;
import com.masiton.common.idempotency.application.port.in.IdempotentCreationUseCase;
import com.masiton.common.web.BusinessException;
import com.masiton.member.application.port.in.LockActiveMemberUseCase;
import com.masiton.personal.application.port.in.CollectionOption;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase;
import com.masiton.personal.application.port.out.PersonalCollectionQueryPort;
import com.masiton.personal.application.port.out.PersonalCollectionStore;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PersonalCollectionService implements PersonalCollectionUseCase {

    private final PersonalCollectionStore store;
    private final PersonalCollectionQueryPort queries;
    private final LockActiveMemberUseCase activeMembers;
    private final FindRestaurantReferenceUseCase restaurantReferences;
    private final IdempotentCreationUseCase idempotentCreation;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PersonalCollectionService(PersonalCollectionStore store, PersonalCollectionQueryPort queries,
            LockActiveMemberUseCase activeMembers,
            FindRestaurantReferenceUseCase restaurantReferences,
            IdempotentCreationUseCase idempotentCreation, ObjectMapper objectMapper,
            @Qualifier("personalizationClock") Clock clock) {
        this.store = store;
        this.queries = queries;
        this.activeMembers = activeMembers;
        this.restaurantReferences = restaurantReferences;
        this.idempotentCreation = idempotentCreation;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public CreationResult create(UUID memberId, String idempotencyKey, String name) {
        String normalizedName = normalizeName(name);
        IdempotencyRequest request = IdempotencyRequest.of(IdempotencyActorType.MEMBER, memberId,
                IdempotencyApiScope.MEMBER_COLLECTIONS, idempotencyKey, hash(normalizedName));
        return new CreationResult(idempotentCreation.execute(request, () -> {
            activeMembers.lockActiveMember(memberId);
            CollectionSummary created = store.create(memberId, UUID.randomUUID(), normalizedName, now());
            return new IdempotencyResponse(201, serialize(created), created.collectionId());
        }).response().body());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionSummary> getCollections(UUID memberId) {
        return queries.findAll(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionOption> getCollectionOptions(UUID memberId, UUID restaurantId) {
        requirePublicRestaurant(restaurantId);
        return queries.findOptions(memberId, restaurantId);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionDetail getCollection(UUID memberId, UUID collectionId, int page, int size) {
        return queries.findDetail(memberId, collectionId, page, size).orElseThrow(this::notFound);
    }

    @Override
    @Transactional
    public CollectionSummary rename(UUID memberId, UUID collectionId, String name) {
        if (!store.rename(memberId, collectionId, normalizeName(name), now())) {
            throw notFound();
        }
        return queries.findSummary(memberId, collectionId).orElseThrow(this::notFound);
    }

    @Override
    @Transactional
    public void delete(UUID memberId, UUID collectionId) {
        store.delete(memberId, collectionId);
    }

    @Override
    @Transactional
    public CollectionRestaurant addRestaurant(UUID memberId, UUID collectionId, UUID restaurantId) {
        requirePublicRestaurant(restaurantId);
        return store.addRestaurant(memberId, collectionId, restaurantId, now()).orElseThrow(this::notFound);
    }

    @Override
    @Transactional
    public void removeRestaurant(UUID memberId, UUID collectionId, UUID restaurantId) {
        store.removeRestaurant(memberId, collectionId, restaurantId, now());
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 50) {
            throw new BusinessException(com.masiton.common.web.ErrorCode.INVALID_FIELD_VALUE,
                    "name", "앞뒤 공백을 제외하고 1~50자로 입력해 주세요.");
        }
        return normalized;
    }

    private void requirePublicRestaurant(UUID restaurantId) {
        boolean visible = restaurantReferences.findRestaurantReference(restaurantId)
                .map(FindRestaurantReferenceUseCase.RestaurantReference::publiclyVisible).orElse(false);
        if (!visible) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND",
                    "요청한 맛집을 찾을 수 없습니다.");
        }
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND",
                "요청한 컬렉션을 찾을 수 없습니다.");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String serialize(CollectionSummary response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Collection response could not be serialized", exception);
        }
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
