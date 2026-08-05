package com.masiton.participation.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.notification.application.port.in.CreateNotificationUseCase;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;
import com.masiton.participation.application.port.in.AdminParticipationUseCase;
import com.masiton.participation.application.port.out.AdminParticipationStore;
import com.masiton.participation.application.port.out.ParticipationCompletionReader;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;

@Service
public class AdminParticipationService implements AdminParticipationUseCase {

    private final AdminParticipationStore store;
    private final ParticipationCompletionReader completionReader;
    private final CreateNotificationUseCase createNotificationUseCase;
    private final Clock clock;

    public AdminParticipationService(
            AdminParticipationStore store,
            ParticipationCompletionReader completionReader,
            CreateNotificationUseCase createNotificationUseCase,
            @Qualifier("participationClock") Clock clock) {
        this.store = store;
        this.completionReader = completionReader;
        this.createNotificationUseCase = createNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminParticipationView.Page<AdminParticipationView.Submission> getSubmissions(
            ParticipationStatus status, ParticipationTargetType targetType, int page, int size) {
        return new AdminParticipationView.Page<>(
                store.findSubmissions(status, targetType, size, offset(page, size)), page, size,
                store.countSubmissions(status, targetType));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminParticipationView.Submission getSubmission(UUID requestId) {
        return store.findSubmission(requestId, false).orElseThrow(this::submissionNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminParticipationView.Page<AdminParticipationView.Report> getReports(
            ParticipationStatus status, ParticipationTargetType targetType, int page, int size) {
        return new AdminParticipationView.Page<>(
                store.findReports(status, targetType, size, offset(page, size)), page, size,
                store.countReports(status, targetType));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminParticipationView.Report getReport(UUID requestId) {
        return store.findReport(requestId, false).orElseThrow(this::reportNotFound);
    }

    @Override
    @Transactional
    public AdminParticipationView.Submission updateSubmission(
            UUID requestId, UUID adminId, UpdateStatusCommand command, String traceId) {
        AdminParticipationView.Submission current = store.findSubmission(requestId, true)
                .orElseThrow(this::submissionNotFound);
        if (current.status() == requiredStatus(command)) {
            return current;
        }
        validateTransition(current.status(), command.status());
        ValidatedUpdate update = validateUpdate(
                command, current.targetType(), null, current.updatedAt(), current.candidate());
        OffsetDateTime now = OffsetDateTime.now(clock);
        store.updateSubmission(requestId, command.status(), update.memberReason(), update.internalNote(),
                update.result(), now, terminalAt(command.status(), now));
        store.insertSubmissionHistory(requestId, requiredAdmin(adminId), current.status(), command.status(),
                update.memberReason(), update.internalNote(), update.result(), requiredTraceId(traceId), now);
        if (current.memberId() != null) {
            createNotificationUseCase.create(
                    current.memberId(),
                    NotificationRequestType.SUBMISSION,
                    requestId,
                    NotificationStatus.valueOf(command.status().name()));
        }
        return store.findSubmission(requestId, false).orElseThrow(this::submissionNotFound);
    }

    @Override
    @Transactional
    public AdminParticipationView.Report updateReport(
            UUID requestId, UUID adminId, UpdateStatusCommand command, String traceId) {
        AdminParticipationView.Report current = store.findReport(requestId, true)
                .orElseThrow(this::reportNotFound);
        if (current.status() == requiredStatus(command)) {
            return current;
        }
        validateTransition(current.status(), command.status());
        ValidatedUpdate update = validateUpdate(
                command, current.targetType(), current.targetId(), current.updatedAt(), null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        store.updateReport(requestId, command.status(), update.memberReason(), update.internalNote(),
                update.result(), now, terminalAt(command.status(), now));
        store.insertReportHistory(requestId, requiredAdmin(adminId), current.status(), command.status(),
                update.memberReason(), update.internalNote(), update.result(), requiredTraceId(traceId), now);
        if (current.memberId() != null) {
            createNotificationUseCase.create(
                    current.memberId(),
                    NotificationRequestType.REPORT,
                    requestId,
                    NotificationStatus.valueOf(command.status().name()));
        }
        return store.findReport(requestId, false).orElseThrow(this::reportNotFound);
    }

    private ValidatedUpdate validateUpdate(
            UpdateStatusCommand command, ParticipationTargetType requestTargetType,
            UUID reportTargetId, OffsetDateTime acceptedAt, java.util.Map<String, Object> candidate) {
        String reason = safeText(command.memberReason(), "memberReason", 1000);
        String note = safeText(command.internalNote(), "internalNote", Integer.MAX_VALUE);
        if ((command.status() == ParticipationStatus.REJECTED || command.status() == ParticipationStatus.COMPLETED)
                && reason == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "memberReason", "필수 입력값입니다.");
        }
        AdminParticipationView.Result result = command.result();
        if (command.status() != ParticipationStatus.COMPLETED) {
            if (result != null) {
                throw invalid("result", "COMPLETED 상태에서만 입력할 수 있습니다.");
            }
            return new ValidatedUpdate(reason, note, null);
        }
        if (result == null || result.actionType() == null || result.targetType() == null || result.targetId() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "result", "완료 조치 결과가 필요합니다.");
        }
        if (result.targetType() != requestTargetType
                || (reportTargetId != null && !reportTargetId.equals(result.targetId()))) {
            throw sourceNotCompleted();
        }
        if (!completionReader.isCompleted(
                result.actionType(), result.targetType(), result.targetId(), acceptedAt, candidate)) {
            throw sourceNotCompleted();
        }
        return new ValidatedUpdate(reason, note, result);
    }

    private String safeText(String value, String field, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max || trimmed.indexOf('<') >= 0 || trimmed.indexOf('>') >= 0
                || trimmed.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid(field, "안전한 일반 텍스트를 입력해 주세요.");
        }
        return trimmed;
    }

    private void validateTransition(ParticipationStatus from, ParticipationStatus to) {
        boolean valid = from == ParticipationStatus.RECEIVED && to == ParticipationStatus.IN_REVIEW
                || from == ParticipationStatus.IN_REVIEW
                && (to == ParticipationStatus.ACCEPTED || to == ParticipationStatus.REJECTED)
                || from == ParticipationStatus.ACCEPTED && to == ParticipationStatus.COMPLETED;
        if (!valid) {
            throw new ParticipationException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "허용되지 않는 상태 전이입니다.");
        }
    }

    private ParticipationStatus requiredStatus(UpdateStatusCommand command) {
        if (command == null || command.status() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "status", "필수 입력값입니다.");
        }
        return command.status();
    }

    private UUID requiredAdmin(UUID adminId) {
        if (adminId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return adminId;
    }

    private String requiredTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalStateException("Server traceId is required");
        }
        return traceId;
    }

    private long offset(int page, int size) {
        return (long) (page - 1) * size;
    }

    private OffsetDateTime terminalAt(ParticipationStatus status, OffsetDateTime now) {
        return status == ParticipationStatus.REJECTED || status == ParticipationStatus.COMPLETED ? now : null;
    }

    private ParticipationException submissionNotFound() {
        return new ParticipationException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "제보를 찾을 수 없습니다.");
    }

    private ParticipationException reportNotFound() {
        return new ParticipationException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "신고를 찾을 수 없습니다.");
    }

    private ParticipationException sourceNotCompleted() {
        return new ParticipationException(HttpStatus.CONFLICT, "SOURCE_ACTION_NOT_COMPLETED", "실제 데이터 조치를 확인할 수 없습니다.");
    }

    private BusinessException invalid(String field, String reason) {
        return new BusinessException(ErrorCode.INVALID_FIELD_VALUE, field, reason);
    }

    private record ValidatedUpdate(String memberReason, String internalNote, AdminParticipationView.Result result) {
    }
}
