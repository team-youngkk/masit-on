package com.masiton.participation.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.masiton.participation.domain.ModerationActionType;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.participation.domain.ReportType;

public final class AdminParticipationView {

    private AdminParticipationView() {
    }

    public record Result(ModerationActionType actionType, ParticipationTargetType targetType, UUID targetId) {
    }

    public record History(
            UUID historyId, UUID adminId, ParticipationStatus fromStatus, ParticipationStatus toStatus,
            String memberReason, String internalNote, Result result, String traceId, OffsetDateTime createdAt) {
    }

    public record Submission(
            UUID requestId, UUID memberId, ParticipationTargetType targetType, Map<String, Object> candidate,
            String description, String evidenceUrl, ParticipationStatus status, String memberReason,
            String internalNote, Result result, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<History> moderationHistory) {
    }

    public record Report(
            UUID requestId, UUID memberId, ParticipationTargetType targetType, UUID targetId,
            ReportType reportType, String description, String evidenceUrl, ParticipationStatus status,
            String memberReason, String internalNote, Result result, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, List<History> moderationHistory) {
    }

    public record Page<T>(List<T> items, int number, int size, long totalElements) {

        public int totalPages() {
            return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        }

        public boolean hasNext() {
            return number < totalPages();
        }
    }
}
