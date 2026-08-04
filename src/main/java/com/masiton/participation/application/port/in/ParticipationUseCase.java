package com.masiton.participation.application.port.in;

import java.util.UUID;

import com.masiton.participation.application.ParticipationRequest;
import com.masiton.participation.application.ParticipationView;
import com.masiton.participation.domain.ParticipationStatus;

public interface ParticipationUseCase {

    ParticipationView.Submission createSubmission(UUID memberId, ParticipationRequest.Submission request);

    ParticipationView.Report createReport(UUID memberId, ParticipationRequest.Report request);

    ParticipationView.Page<ParticipationView.Submission> getSubmissions(
            UUID memberId, ParticipationStatus status, int page, int size);

    ParticipationView.Submission getSubmission(UUID memberId, UUID requestId);

    ParticipationView.Page<ParticipationView.Report> getReports(
            UUID memberId, ParticipationStatus status, int page, int size);

    ParticipationView.Report getReport(UUID memberId, UUID requestId);
}
