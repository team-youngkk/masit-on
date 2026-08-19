package com.masiton.curation.presentation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
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

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.security.LegacyAdminActorResolver;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.curation.application.port.in.AdminCurationUseCase;
import com.masiton.curation.application.port.in.AdminCurationUseCase.CurationDetail;
import com.masiton.curation.application.port.in.AdminCurationUseCase.CurationSummary;
import com.masiton.curation.domain.model.CurationStatus;

@RestController
@RequestMapping("/api/admin/curations")
public class AdminCurationController {

    private static final String PRIVATE_NO_STORE = "private, no-store";
    private static final Set<String> PAGE_FIELDS = Set.of("page", "size", "status");
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);
    private final AdminCurationUseCase useCase;
    private final LegacyAdminActorResolver legacyAdminActorResolver;

    public AdminCurationController(AdminCurationUseCase useCase, LegacyAdminActorResolver legacyAdminActorResolver) {
        this.useCase = useCase;
        this.legacyAdminActorResolver = legacyAdminActorResolver;
    }

    @PostMapping
    public ResponseEntity<String> create(Authentication authentication, HttpServletRequest servletRequest,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ContentRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        String body = useCase.create(adminId(authentication), idempotencyKey, request.title(), request.description(),
                traceId(servletRequest)).responseBody();
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE)
                .contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @GetMapping
    public ResponseEntity<CurationPageResponse> list(@RequestParam MultiValueMap<String, String> query) {
        PageRequest page = page(query);
        AdminCurationUseCase.Page<CurationSummary> result = useCase.getCurations(page.status(), page.number(), page.size());
        return ok(new CurationPageResponse(result.items(), new PageMetadata(result.number(), result.size(),
                result.totalElements(), result.totalPages(), result.hasNext())));
    }

    @GetMapping("/{curationId}")
    public ResponseEntity<CurationDetail> detail(@PathVariable String curationId) {
        return ok(useCase.getCuration(identifier(curationId)));
    }

    @PatchMapping("/{curationId}")
    public ResponseEntity<CurationDetail> update(Authentication authentication, HttpServletRequest servletRequest,
            @PathVariable String curationId, @RequestBody(required = false) ContentRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return ok(useCase.updateContent(identifier(curationId), adminId(authentication), request.title(),
                request.description(), traceId(servletRequest)));
    }

    @PutMapping("/{curationId}/restaurants")
    public ResponseEntity<CurationDetail> replaceRestaurants(Authentication authentication,
            HttpServletRequest servletRequest, @PathVariable String curationId,
            @RequestBody(required = false) RestaurantOrderRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return ok(useCase.replaceRestaurants(identifier(curationId), adminId(authentication),
                identifiers(request.restaurantIds(), "restaurantIds"), traceId(servletRequest)));
    }

    @PutMapping("/{curationId}/publication")
    public ResponseEntity<CurationDetail> publication(Authentication authentication,
            HttpServletRequest servletRequest, @PathVariable String curationId,
            @RequestBody(required = false) PublicationRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return ok(useCase.setPublication(identifier(curationId), adminId(authentication),
                status(request.status(), "status"), traceId(servletRequest)));
    }

    @PutMapping("/main-order")
    public ResponseEntity<CurationListResponse> mainOrder(Authentication authentication,
            HttpServletRequest servletRequest, @RequestBody(required = false) MainOrderRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return ok(new CurationListResponse(useCase.replaceMainOrder(
                adminId(authentication), identifiers(request.curationIds(), "curationIds"), traceId(servletRequest))));
    }

    private PageRequest page(MultiValueMap<String, String> query) {
        for (String field : query.keySet()) {
            List<String> values = query.get(field);
            if (!PAGE_FIELDS.contains(field) || values == null || values.size() != 1) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
        int number = integer(query.getFirst("page"), "page", 1);
        int size = integer(query.getFirst("size"), "size", 20);
        if (number < 1) throw invalid("page", "1 이상이어야 합니다.");
        if (!ALLOWED_SIZES.contains(size)) throw invalid("size", "10, 20, 50 중 하나여야 합니다.");
        CurationStatus status = query.getFirst("status") == null ? null : status(query.getFirst("status"), "status");
        return new PageRequest(number, size, status);
    }

    private int integer(String value, String field, int defaultValue) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw invalid(field, "정수만 허용됩니다."); }
    }

    private CurationStatus status(String value, String field) {
        if (value == null) throw missing(field);
        try { return CurationStatus.valueOf(value); }
        catch (RuntimeException exception) { throw invalid(field, "DRAFT, PUBLISHED 중 하나여야 합니다."); }
    }

    private UUID identifier(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException exception) { throw new BusinessException(ErrorCode.INVALID_IDENTIFIER); }
    }

    private List<UUID> identifiers(List<String> values, String field) {
        if (values == null) throw missing(field);
        return values.stream().map(this::identifier).toList();
    }

    private UUID adminId(Authentication authentication) {
        if (authentication == null) throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        try {
            return legacyAdminActorResolver.resolve(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE);
        if (value instanceof String traceId && !traceId.isBlank()) return traceId;
        throw new IllegalStateException("Server traceId is required");
    }

    private BusinessException invalid(String field, String reason) {
        return new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, reason);
    }
    private BusinessException missing(String field) {
        return new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, field, "필수 입력값입니다.");
    }
    private <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE).body(body);
    }

    public record ContentRequest(String title, String description) { }
    public record RestaurantOrderRequest(List<String> restaurantIds) { }
    public record PublicationRequest(String status) { }
    public record MainOrderRequest(List<String> curationIds) { }
    public record CurationListResponse(List<CurationSummary> items) { }
    public record CurationPageResponse(List<CurationSummary> items, PageMetadata page) { }
    public record PageMetadata(int number, int size, long totalElements, int totalPages, boolean hasNext) { }
    private record PageRequest(int number, int size, CurationStatus status) { }
}
