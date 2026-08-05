package com.masiton.participation.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.web.ErrorResponse;
import com.masiton.participation.application.port.in.ParticipationUseCase;
import com.masiton.participation.application.port.out.ParticipationStore;
import com.masiton.participation.application.port.out.ParticipationTargetReader;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;

@Service
public class ParticipationService implements ParticipationUseCase {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int DAILY_LIMIT = 5;

    private final ParticipationStore store;
    private final ParticipationTargetReader targetReader;
    private final Clock clock;

    public ParticipationService(
            ParticipationStore store,
            ParticipationTargetReader targetReader,
            @Qualifier("participationClock") Clock clock
    ) {
        this.store = store;
        this.targetReader = targetReader;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ParticipationView.Submission createSubmission(
            UUID memberId,
            ParticipationRequest.Submission request
    ) {
        requireRequest(request);
        Map<String, Object> candidate = normalizeCandidate(request.targetType(), request.candidate());
        String description = description(request.description());
        String evidenceUrl = evidenceUrl(request.evidenceUrl());
        byte[] fingerprint = fingerprint(request.targetType(), candidate);
        OffsetDateTime now = OffsetDateTime.now(clock);

        store.lockMember(memberId);
        enforceDailyLimit(memberId, now);
        store.findOpenSubmission(memberId, request.targetType(), fingerprint).ifPresent(existing -> {
            throw ParticipationException.duplicateSubmission(minimal(existing.requestId(), existing.status().name()));
        });
        return store.insertSubmission(
                UUID.randomUUID(), memberId, request.targetType(), candidate, fingerprint,
                description, evidenceUrl, now);
    }

    @Override
    @Transactional
    public ParticipationView.Report createReport(UUID memberId, ParticipationRequest.Report request) {
        requireRequest(request);
        store.lockMember(memberId);
        if (!request.reportType().supports(request.targetType())) {
            throw invalid("reportType", "대상 유형과 맞지 않는 신고 유형입니다.");
        }
        if (!targetReader.targetExists(request.targetType(), request.targetId())) {
            throw new ParticipationException(
                    HttpStatus.NOT_FOUND, "PARTICIPATION_TARGET_NOT_FOUND", "신고 대상을 찾을 수 없습니다.");
        }
        String description = description(request.description());
        String evidenceUrl = evidenceUrl(request.evidenceUrl());
        OffsetDateTime now = OffsetDateTime.now(clock);

        enforceDailyLimit(memberId, now);
        store.findOpenReport(memberId, request.targetType(), request.targetId(), request.reportType())
                .ifPresent(existing -> {
                    throw ParticipationException.duplicateReport(
                            minimal(existing.requestId(), existing.status().name()));
                });
        return store.insertReport(
                UUID.randomUUID(), memberId, request.targetType(), request.targetId(), request.reportType(),
                description, evidenceUrl, now);
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipationView.Page<ParticipationView.Submission> getSubmissions(
            UUID memberId, ParticipationStatus status, int page, int size
    ) {
        return new ParticipationView.Page<>(
                store.findSubmissions(memberId, status, size, ((long) page - 1) * size),
                page, size, store.countSubmissions(memberId, status));
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipationView.Submission getSubmission(UUID memberId, UUID requestId) {
        return store.findSubmission(memberId, requestId).orElseThrow(() -> new ParticipationException(
                HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "제보를 찾을 수 없습니다."));
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipationView.Page<ParticipationView.Report> getReports(
            UUID memberId, ParticipationStatus status, int page, int size
    ) {
        return new ParticipationView.Page<>(
                store.findReports(memberId, status, size, ((long) page - 1) * size),
                page, size, store.countReports(memberId, status));
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipationView.Report getReport(UUID memberId, UUID requestId) {
        return store.findReport(memberId, requestId).orElseThrow(() -> new ParticipationException(
                HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "신고를 찾을 수 없습니다."));
    }

    private void enforceDailyLimit(UUID memberId, OffsetDateTime now) {
        LocalDate date = now.atZoneSameInstant(SEOUL).toLocalDate();
        OffsetDateTime from = date.atStartOfDay(SEOUL).toOffsetDateTime();
        OffsetDateTime until = date.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime();
        if (store.countCreated(memberId, from, until) >= DAILY_LIMIT) {
            long retryAfter = java.time.Duration.between(now.toInstant(), until.toInstant()).toSeconds();
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "DAILY_REQUEST_LIMIT_EXCEEDED",
                    "오늘 접수할 수 있는 제보·신고 수를 모두 사용했습니다.",
                    Math.max(1, retryAfter));
        }
    }

    private Map<String, Object> normalizeCandidate(
            ParticipationTargetType targetType,
            Map<String, Object> raw
    ) {
        if (raw == null) {
            throw invalid("candidate", "대상 후보 정보가 필요합니다.");
        }
        LinkedHashMap<String, Object> candidate = new LinkedHashMap<>();
        switch (targetType) {
            case RESTAURANT -> {
                requireOnly(raw, Set.of("name", "roadAddress"));
                candidate.put("name", shortText(raw, "name", 1, 200));
                candidate.put("roadAddress", shortText(raw, "roadAddress", 1, 500));
            }
            case CREATOR -> {
                requireOnly(raw, Set.of("channelUrl"));
                candidate.put("channelUrl", httpsValue(raw, "channelUrl"));
            }
            case VIDEO -> {
                requireOnly(raw, Set.of("videoUrl"));
                candidate.put("videoUrl", httpsValue(raw, "videoUrl"));
            }
            case VISIT_RELATIONSHIP -> {
                requireOnly(raw, Set.of("restaurantId", "creatorId", "videoId"));
                candidate.put("restaurantId", uuidValue(raw, "restaurantId"));
                candidate.put("creatorId", uuidValue(raw, "creatorId"));
                candidate.put("videoId", uuidValue(raw, "videoId"));
            }
        }
        return Map.copyOf(candidate);
    }

    private void requireOnly(Map<String, Object> raw, Set<String> expected) {
        if (!raw.keySet().equals(expected)) {
            throw invalid("candidate", "대상 유형에 필요한 필드만 입력해 주세요.");
        }
    }

    private String shortText(Map<String, Object> raw, String field, int min, int max) {
        Object value = raw.get(field);
        if (!(value instanceof String text)) {
            throw invalid("candidate." + field, "문자열 값을 입력해 주세요.");
        }
        String normalized = safeText(text, field);
        if (normalized.length() < min || normalized.length() > max) {
            throw invalid("candidate." + field, min + "~" + max + "자로 입력해 주세요.");
        }
        return normalized;
    }

    private String httpsValue(Map<String, Object> raw, String field) {
        Object value = raw.get(field);
        if (!(value instanceof String text)) {
            throw invalid("candidate." + field, "HTTPS URL을 입력해 주세요.");
        }
        String normalized = validateHttps(text, field);
        if (normalized.length() > 2048) {
            throw invalid("candidate." + field, "2048자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private String uuidValue(Map<String, Object> raw, String field) {
        try {
            return UUID.fromString(String.valueOf(raw.get(field))).toString();
        } catch (RuntimeException exception) {
            throw invalid("candidate." + field, "식별자 형식이 올바르지 않습니다.");
        }
    }

    private String description(String raw) {
        String value = safeText(raw, "description");
        if (value.length() < 10 || value.length() > 2000) {
            throw invalid("description", "10~2000자로 입력해 주세요.");
        }
        return value;
    }

    private String evidenceUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = validateHttps(raw, "evidenceUrl");
        if (value.length() > 2048) {
            throw invalid("evidenceUrl", "2048자 이하로 입력해 주세요.");
        }
        return value;
    }

    private String validateHttps(String raw, String field) {
        String value = safeText(raw, field);
        try {
            URI uri = new URI(value);
            if (!value.startsWith("https://") || !"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw invalid(field, "사용자 정보가 없는 HTTPS URL만 허용합니다.");
            }
            return canonicalHttpsUri(uri.normalize());
        } catch (URISyntaxException exception) {
            throw invalid(field, "HTTPS URL 형식이 올바르지 않습니다.");
        }
    }

    private String canonicalHttpsUri(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        StringBuilder value = new StringBuilder("https://").append(host);
        if (uri.getPort() >= 0 && uri.getPort() != 443) {
            value.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null) {
            value.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            value.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            value.append('#').append(uri.getRawFragment());
        }
        return value.toString();
    }

    private String safeText(String raw, String field) {
        if (raw == null) {
            throw invalid(field, "필수 입력값입니다.");
        }
        String value = raw.trim();
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0
                || value.codePoints().anyMatch(this::isForbiddenControl)) {
            throw invalid(field, "실행성 문자열이나 제어 문자는 입력할 수 없습니다.");
        }
        return value;
    }

    private boolean isForbiddenControl(int codePoint) {
        return Character.isISOControl(codePoint);
    }

    private byte[] fingerprint(ParticipationTargetType type, Map<String, Object> candidate) {
        StringBuilder canonical = new StringBuilder(type.name());
        candidate.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> canonical
                .append('|').append(entry.getKey()).append('=')
                .append(canonicalFingerprintValue(type, entry.getKey(), entry.getValue())));
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String canonicalFingerprintValue(
            ParticipationTargetType type,
            String field,
            Object value
    ) {
        String normalized = String.valueOf(value).trim();
        if ((type == ParticipationTargetType.CREATOR && field.equals("channelUrl"))
                || (type == ParticipationTargetType.VIDEO && field.equals("videoUrl"))) {
            return normalized;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private ErrorResponse.ResourceReference minimal(UUID requestId, String status) {
        return new ErrorResponse.ResourceReference(requestId.toString(), status);
    }

    private BusinessException invalid(String field, String reason) {
        return new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, reason);
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
