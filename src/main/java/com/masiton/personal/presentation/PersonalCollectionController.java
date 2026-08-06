package com.masiton.personal.presentation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionDetail;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionSummary;

@RestController
@RequestMapping("/api/me/collections")
public class PersonalCollectionController {

    private static final String PRIVATE_NO_STORE = "private, no-store";
    private static final Set<String> PAGE_FIELDS = Set.of("page", "size");
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);

    private final PersonalCollectionUseCase useCase;

    public PersonalCollectionController(PersonalCollectionUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<String> create(Authentication authentication,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody NameRequest request) {
        String body = useCase.create(memberId(authentication), idempotencyKey, request.name()).responseBody();
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE)
                .contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @GetMapping
    public ResponseEntity<CollectionListResponse> getCollections(Authentication authentication) {
        List<CollectionListItem> items = useCase.getCollections(memberId(authentication)).stream()
                .map(CollectionListItem::from).toList();
        return ok(new CollectionListResponse(items));
    }

    @GetMapping("/{collectionId}")
    public ResponseEntity<CollectionDetailResponse> getCollection(Authentication authentication,
            @PathVariable String collectionId, @RequestParam MultiValueMap<String, String> query) {
        PageRequest page = page(query);
        return ok(CollectionDetailResponse.from(useCase.getCollection(memberId(authentication),
                identifier(collectionId), page.number(), page.size())));
    }

    @PatchMapping("/{collectionId}")
    public ResponseEntity<CollectionSummary> rename(Authentication authentication,
            @PathVariable String collectionId, @RequestBody NameRequest request) {
        UUID memberId = memberId(authentication);
        UUID id = identifier(collectionId);
        useCase.rename(memberId, id, request.name());
        return ok(useCase.getSummary(memberId, id));
    }

    @DeleteMapping("/{collectionId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable String collectionId) {
        useCase.delete(memberId(authentication), identifier(collectionId));
        return noContent();
    }

    @PutMapping("/{collectionId}/restaurants/{restaurantId}")
    public ResponseEntity<PersonalCollectionUseCase.CollectionRestaurant> addRestaurant(
            Authentication authentication, @PathVariable String collectionId,
            @PathVariable String restaurantId) {
        return ok(useCase.addRestaurant(memberId(authentication), identifier(collectionId),
                identifier(restaurantId)));
    }

    @DeleteMapping("/{collectionId}/restaurants/{restaurantId}")
    public ResponseEntity<Void> removeRestaurant(Authentication authentication,
            @PathVariable String collectionId, @PathVariable String restaurantId) {
        useCase.removeRestaurant(memberId(authentication), identifier(collectionId), identifier(restaurantId));
        return noContent();
    }

    private <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE).body(body);
    }

    private ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE).build();
    }

    private UUID memberId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private UUID identifier(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
    }

    private PageRequest page(MultiValueMap<String, String> query) {
        for (String field : query.keySet()) {
            if (!PAGE_FIELDS.contains(field)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            List<String> values = query.get(field);
            if (values != null && values.size() > 1) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field,
                        "값은 한 번만 지정할 수 있습니다.");
            }
        }
        int number = integer(query.getFirst("page"), "page", 1);
        int size = integer(query.getFirst("size"), "size", 20);
        if (number < 1) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "page", "1 이상이어야 합니다.");
        }
        if (!ALLOWED_SIZES.contains(size)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "size",
                    "10, 20, 50 중 하나여야 합니다.");
        }
        return new PageRequest(number, size);
    }

    private int integer(String raw, String field, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "정수만 허용합니다.");
        }
    }

    public record NameRequest(String name) {
    }

    public record CollectionListResponse(List<CollectionListItem> items) {
    }

    public record CollectionListItem(String collectionId, String name, long restaurantCount,
                                     java.time.OffsetDateTime updatedAt) {
        static CollectionListItem from(CollectionSummary source) {
            return new CollectionListItem(source.collectionId().toString(), source.name(),
                    source.restaurantCount(), source.updatedAt());
        }
    }

    public record CollectionDetailResponse(String collectionId, String name, long restaurantCount,
            java.time.OffsetDateTime updatedAt, List<PersonalCollectionUseCase.RestaurantItem> items,
            PageResponse page) {
        static CollectionDetailResponse from(CollectionDetail source) {
            return new CollectionDetailResponse(source.collectionId().toString(), source.name(),
                    source.restaurantCount(), source.updatedAt(), source.items(),
                    new PageResponse(source.pageNumber(), source.pageSize(), source.totalElements(),
                            source.totalPages(), source.hasNext()));
        }
    }

    public record PageResponse(int number, int size, long totalElements, int totalPages, boolean hasNext) {
    }

    private record PageRequest(int number, int size) {
    }
}
