package com.masiton.participation.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.masiton.participation.application.AdminParticipationView;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;

public interface AdminParticipationStore {

    List<AdminParticipationView.Submission> findSubmissions(
            ParticipationStatus status, ParticipationTargetType targetType, int limit, long offset);

    long countSubmissions(ParticipationStatus status, ParticipationTargetType targetType);

    Optional<AdminParticipationView.Submission> findSubmission(UUID requestId, boolean lock);

    List<AdminParticipationView.Report> findReports(
            ParticipationStatus status, ParticipationTargetType targetType, int limit, long offset);

    long countReports(ParticipationStatus status, ParticipationTargetType targetType);

    Optional<AdminParticipationView.Report> findReport(UUID requestId, boolean lock);

    void updateSubmission(UUID requestId, ParticipationStatus status, String memberReason,
                          String internalNote, AdminParticipationView.Result result,
                          OffsetDateTime updatedAt, OffsetDateTime terminalAt);

    void updateReport(UUID requestId, ParticipationStatus status, String memberReason,
                      String internalNote, AdminParticipationView.Result result,
                      OffsetDateTime updatedAt, OffsetDateTime terminalAt);

    void insertSubmissionHistory(UUID requestId, UUID adminId, ParticipationStatus fromStatus,
                                 ParticipationStatus toStatus, String memberReason, String internalNote,
                                 AdminParticipationView.Result result, String traceId, OffsetDateTime createdAt);

    void insertReportHistory(UUID requestId, UUID adminId, ParticipationStatus fromStatus,
                             ParticipationStatus toStatus, String memberReason, String internalNote,
                             AdminParticipationView.Result result, String traceId, OffsetDateTime createdAt);
}
