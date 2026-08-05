package com.masiton.participation.presentation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masiton.common.observability.TraceIdFilter;
import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.participation.application.AdminParticipationView;
import com.masiton.participation.application.port.in.AdminParticipationUseCase;
import com.masiton.participation.domain.ModerationActionType;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;

@RestController
@RequestMapping("/api/admin")
public class AdminParticipationController {

    private static final Set<String> PAGE_FIELDS = Set.of("page", "size", "status", "targetType");
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50);
    private static final String PRIVATE_NO_STORE = "private, no-store";

    private final AdminParticipationUseCase useCase;

    public AdminParticipationController(AdminParticipationUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/submissions")
    public ResponseEntity<AdminPage<AdminParticipationView.Submission>> submissions(
            @RequestParam MultiValueMap<String, String> query) {
        PageRequest page = page(query);
        return ok(AdminPage.from(useCase.getSubmissions(page.status(), page.targetType(), page.number(), page.size())));
    }

    @GetMapping("/submissions/{requestId}")
    public ResponseEntity<AdminParticipationView.Submission> submission(@PathVariable String requestId) {
        return ok(useCase.getSubmission(identifier(requestId)));
    }

    @PutMapping("/submissions/{requestId}/status")
    public ResponseEntity<AdminParticipationView.Submission> updateSubmission(
            Authentication authentication, HttpServletRequest request, @PathVariable String requestId,
            @RequestBody StatusBody body) {
        return ok(useCase.updateSubmission(identifier(requestId), adminId(authentication), command(body), traceId(request)));
    }

    @GetMapping("/reports")
    public ResponseEntity<AdminPage<AdminParticipationView.Report>> reports(
            @RequestParam MultiValueMap<String, String> query) {
        PageRequest page = page(query);
        return ok(AdminPage.from(useCase.getReports(page.status(), page.targetType(), page.number(), page.size())));
    }

    @GetMapping("/reports/{requestId}")
    public ResponseEntity<AdminParticipationView.Report> report(@PathVariable String requestId) {
        return ok(useCase.getReport(identifier(requestId)));
    }

    @PutMapping("/reports/{requestId}/status")
    public ResponseEntity<AdminParticipationView.Report> updateReport(
            Authentication authentication, HttpServletRequest request, @PathVariable String requestId,
            @RequestBody StatusBody body) {
        return ok(useCase.updateReport(identifier(requestId), adminId(authentication), command(body), traceId(request)));
    }

    private AdminParticipationUseCase.UpdateStatusCommand command(StatusBody body) {
        if (body == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        AdminParticipationView.Result result = body.result() == null ? null : new AdminParticipationView.Result(
                enumValue(ModerationActionType.class, body.result().actionType(), "result.actionType"),
                enumValue(ParticipationTargetType.class, body.result().targetType(), "result.targetType"),
                identifier(body.result().targetId()));
        return new AdminParticipationUseCase.UpdateStatusCommand(
                enumValue(ParticipationStatus.class, body.status(), "status"),
                body.memberReason(), body.internalNote(), result);
    }

    private PageRequest page(MultiValueMap<String, String> query) {
        for (String field : query.keySet()) {
            if (!PAGE_FIELDS.contains(field) || query.get(field) == null || query.get(field).size() != 1) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
        int number = integer(query.getFirst("page"), "page", 1);
        int size = integer(query.getFirst("size"), "size", 20);
        if (number < 1) throw invalid("page", "1 이상이어야 합니다.");
        if (!ALLOWED_SIZES.contains(size)) throw invalid("size", "10, 20, 50 중 하나여야 합니다.");
        ParticipationStatus status = optionalEnum(ParticipationStatus.class, query.getFirst("status"), "status");
        ParticipationTargetType targetType = optionalEnum(
                ParticipationTargetType.class, query.getFirst("targetType"), "targetType");
        return new PageRequest(number, size, status, targetType);
    }

    private int integer(String raw, String field, int defaultValue) {
        if (raw == null) return defaultValue;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw invalid(field, "정수만 허용됩니다.");
        }
    }

    private UUID identifier(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_IDENTIFIER);
        }
    }

    private UUID adminId(Authentication authentication) {
        if (authentication == null) throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE);
        if (value instanceof String traceId && !traceId.isBlank()) return traceId;
        throw new IllegalStateException("Server traceId is required");
    }

    private <E extends Enum<E>> E optionalEnum(Class<E> type, String value, String field) {
        return value == null ? null : enumValue(type, value, field);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException exception) {
            throw invalid(field, "허용되지 않는 값입니다.");
        }
    }

    private BusinessException invalid(String field, String reason) {
        return new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, reason);
    }

    private <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE).body(body);
    }

    public record StatusBody(String status, String memberReason, String internalNote, ResultBody result) {
    }

    public record ResultBody(String actionType, String targetType, String targetId) {
    }

    public record PageMetadata(int number, int size, long totalElements, int totalPages, boolean hasNext) {
    }

    public record AdminPage<T>(List<T> items, PageMetadata page) {
        static <T> AdminPage<T> from(AdminParticipationView.Page<T> source) {
            return new AdminPage<>(source.items(), new PageMetadata(source.number(), source.size(),
                    source.totalElements(), source.totalPages(), source.hasNext()));
        }
    }

    private record PageRequest(
            int number, int size, ParticipationStatus status, ParticipationTargetType targetType) {
    }
}
