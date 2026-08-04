package com.masiton.participation.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.masiton.participation.application.ParticipationView;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.participation.domain.ReportType;

public interface ParticipationStore {

    void lockMember(UUID memberId);

    long countCreated(UUID memberId, OffsetDateTime from, OffsetDateTime until);

    Optional<ParticipationView.Submission> findOpenSubmission(
            UUID memberId, ParticipationTargetType targetType, byte[] fingerprint);

    ParticipationView.Submission insertSubmission(
            UUID id, UUID memberId, ParticipationTargetType targetType, Map<String, Object> candidate,
            byte[] fingerprint, String description, String evidenceUrl, OffsetDateTime now);

    Optional<ParticipationView.Report> findOpenReport(
            UUID memberId, ParticipationTargetType targetType, UUID targetId, ReportType reportType);

    ParticipationView.Report insertReport(
            UUID id, UUID memberId, ParticipationTargetType targetType, UUID targetId,
            ReportType reportType, String description, String evidenceUrl, OffsetDateTime now);

    boolean targetExists(ParticipationTargetType targetType, UUID targetId);

    List<ParticipationView.Submission> findSubmissions(
            UUID memberId, ParticipationStatus status, int limit, long offset);

    long countSubmissions(UUID memberId, ParticipationStatus status);

    Optional<ParticipationView.Submission> findSubmission(UUID memberId, UUID requestId);

    List<ParticipationView.Report> findReports(
            UUID memberId, ParticipationStatus status, int limit, long offset);

    long countReports(UUID memberId, ParticipationStatus status);

    Optional<ParticipationView.Report> findReport(UUID memberId, UUID requestId);
}
