package com.masiton.participation.application.port.in;

import java.util.UUID;

import com.masiton.participation.application.AdminParticipationView;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;

public interface AdminParticipationUseCase {

    AdminParticipationView.Page<AdminParticipationView.Submission> getSubmissions(
            ParticipationStatus status, ParticipationTargetType targetType, int page, int size);

    AdminParticipationView.Submission getSubmission(UUID requestId);

    AdminParticipationView.Page<AdminParticipationView.Report> getReports(
            ParticipationStatus status, ParticipationTargetType targetType, int page, int size);

    AdminParticipationView.Report getReport(UUID requestId);

    AdminParticipationView.Submission updateSubmission(
            UUID requestId, UUID adminId, UpdateStatusCommand command, String traceId);

    AdminParticipationView.Report updateReport(
            UUID requestId, UUID adminId, UpdateStatusCommand command, String traceId);

    record UpdateStatusCommand(
            ParticipationStatus status, String memberReason, String internalNote,
            AdminParticipationView.Result result) {
    }
}
