package com.masiton.participation.presentation;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.idempotency.application.IdempotencyActorType;
import com.masiton.common.idempotency.application.IdempotencyApiScope;
import com.masiton.common.idempotency.application.IdempotencyExecutionResult;
import com.masiton.common.idempotency.application.IdempotencyRequest;
import com.masiton.common.idempotency.application.IdempotencyResponse;
import com.masiton.common.idempotency.application.port.in.IdempotentCreationUseCase;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.participation.application.ParticipationException;
import com.masiton.participation.application.ParticipationRequest;
import com.masiton.participation.application.ParticipationView;
import com.masiton.participation.application.port.in.ParticipationUseCase;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.participation.domain.ReportType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/me")
public class ParticipationController {

    private static final String PRIVATE_NO_STORE = "private, no-store";
    private static final Set<String> PAGE_FIELDS = Set.of("page", "size", "status");
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);

    private final ParticipationUseCase useCase;
    private final IdempotentCreationUseCase idempotency;
    private final ObjectMapper objectMapper;

    public ParticipationController(
            ParticipationUseCase useCase,
            IdempotentCreationUseCase idempotency,
            ObjectMapper objectMapper
    ) {
        this.useCase = useCase;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/submissions")
    public ResponseEntity<JsonNode> createSubmission(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody SubmissionBody body
    ) {
        UUID memberId = memberId(authentication);
        ParticipationRequest.Submission command = new ParticipationRequest.Submission(
                targetType(body.targetType()), body.candidate(), body.description(), body.evidenceUrl());
        IdempotencyExecutionResult result = execute(
                memberId, key, IdempotencyApiScope.MEMBER_SUBMISSIONS, canonical(body), () -> {
                    ParticipationView.Submission created = useCase.createSubmission(memberId, command);
                    return response(created.requestId(), created);
                });
        return created(result);
    }

    @PostMapping("/reports")
    public ResponseEntity<JsonNode> createReport(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ReportBody body
    ) {
        UUID memberId = memberId(authentication);
        ParticipationRequest.Report command = new ParticipationRequest.Report(
                targetType(body.targetType()), identifier(body.targetId()), reportType(body.reportType()),
                body.description(), body.evidenceUrl());
        IdempotencyExecutionResult result = execute(
                memberId, key, IdempotencyApiScope.MEMBER_REPORTS, canonical(body), () -> {
                    ParticipationView.Report created = useCase.createReport(memberId, command);
                    return response(created.requestId(), created);
                });
        return created(result);
    }

    @GetMapping("/submissions")
    public ResponseEntity<MemberPage<ParticipationView.Submission>> submissions(
            Authentication authentication,
            @RequestParam MultiValueMap<String, String> query
    ) {
        PageRequest page = page(query);
        return privateOk(MemberPage.from(useCase.getSubmissions(
                memberId(authentication), page.status(), page.number(), page.size())));
    }

    @GetMapping("/submissions/{requestId}")
    public ResponseEntity<ParticipationView.Submission> submission(
            Authentication authentication, @PathVariable String requestId
    ) {
        return privateOk(useCase.getSubmission(
                memberId(authentication),
                requestIdentifier(requestId, "SUBMISSION_NOT_FOUND", "제보를 찾을 수 없습니다.")));
    }

    @GetMapping("/reports")
    public ResponseEntity<MemberPage<ParticipationView.Report>> reports(
            Authentication authentication,
            @RequestParam MultiValueMap<String, String> query
    ) {
        PageRequest page = page(query);
        return privateOk(MemberPage.from(useCase.getReports(
                memberId(authentication), page.status(), page.number(), page.size())));
    }

    @GetMapping("/reports/{requestId}")
    public ResponseEntity<ParticipationView.Report> report(
            Authentication authentication, @PathVariable String requestId
    ) {
        return privateOk(useCase.getReport(
                memberId(authentication),
                requestIdentifier(requestId, "REPORT_NOT_FOUND", "신고를 찾을 수 없습니다.")));
    }

    private IdempotencyExecutionResult execute(
            UUID memberId,
            String key,
            IdempotencyApiScope scope,
            Object canonicalBody,
            IdempotentCreationUseCase.CreationAction action
    ) {
        IdempotencyRequest request = IdempotencyRequest.of(
                IdempotencyActorType.MEMBER, memberId, scope, key, hash(canonicalBody));
        return idempotency.execute(request, action);
    }

    private IdempotencyResponse response(UUID id, Object body) {
        try {
            return new IdempotencyResponse(201, objectMapper.writeValueAsString(body), id);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Participation response cannot be encoded", exception);
        }
    }

    private ResponseEntity<JsonNode> created(IdempotencyExecutionResult result) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE)
                    .body(objectMapper.readTree(result.response().body()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored participation response is invalid", exception);
        }
    }

    private <T> ResponseEntity<T> privateOk(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE).body(body);
    }

    private Object canonical(SubmissionBody body) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("targetType", body.targetType());
        value.put("candidate", body.candidate());
        value.put("description", body.description());
        value.put("evidenceUrl", body.evidenceUrl());
        return canonicalValue(value);
    }

    private Object canonical(ReportBody body) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("targetType", body.targetType());
        value.put("targetId", body.targetId());
        value.put("reportType", body.reportType());
        value.put("description", body.description());
        value.put("evidenceUrl", body.evidenceUrl());
        return canonicalValue(value);
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), canonicalValue(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalValue).toList();
        }
        return value;
    }

    private byte[] hash(Object value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(value));
        } catch (NoSuchAlgorithmException | JacksonException exception) {
            throw new IllegalStateException("Participation request cannot be hashed", exception);
        }
    }

    private UUID memberId(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private UUID identifier(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
    }

    private UUID requestIdentifier(String value, String code, String message) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new ParticipationException(HttpStatus.NOT_FOUND, code, message);
        }
    }

    private ParticipationTargetType targetType(String value) {
        try {
            return ParticipationTargetType.valueOf(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "targetType", "허용되지 않는 대상 유형입니다.");
        }
    }

    private ReportType reportType(String value) {
        try {
            return ReportType.valueOf(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "reportType", "허용되지 않는 신고 유형입니다.");
        }
    }

    private PageRequest page(MultiValueMap<String, String> query) {
        for (String field : query.keySet()) {
            if (!PAGE_FIELDS.contains(field) || query.get(field) == null || query.get(field).size() != 1) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
        int number = integer(query.getFirst("page"), "page", 1);
        int size = integer(query.getFirst("size"), "size", 20);
        if (number < 1) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "page", "1 이상의 값만 허용합니다.");
        }
        if (!ALLOWED_SIZES.contains(size)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "size", "10, 20, 50 중 하나만 허용합니다.");
        }
        ParticipationStatus status = null;
        String rawStatus = query.getFirst("status");
        if (rawStatus != null) {
            try {
                status = ParticipationStatus.valueOf(rawStatus);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "status", "허용되지 않는 상태입니다.");
            }
        }
        return new PageRequest(number, size, status);
    }

    private int integer(String raw, String field, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, "정수 값만 허용합니다.");
        }
    }

    public record SubmissionBody(
            String targetType,
            Map<String, Object> candidate,
            String description,
            String evidenceUrl
    ) {
    }

    public record ReportBody(
            String targetType,
            String targetId,
            String reportType,
            String description,
            String evidenceUrl
    ) {
    }

    public record PageMetadata(
            int number, int size, long totalElements, int totalPages, boolean hasNext
    ) {
    }

    public record MemberPage<T>(List<T> items, PageMetadata page) {

        static <T> MemberPage<T> from(ParticipationView.Page<T> source) {
            return new MemberPage<>(source.items(), new PageMetadata(
                    source.number(), source.size(), source.totalElements(),
                    source.totalPages(), source.hasNext()));
        }
    }

    private record PageRequest(int number, int size, ParticipationStatus status) {
    }
}
