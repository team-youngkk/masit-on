package com.masiton.participation.application;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.participation.domain.ReportType;

public final class ParticipationView {

    private ParticipationView() {
    }

    public record Submission(
            UUID requestId,
            ParticipationTargetType targetType,
            Map<String, Object> candidate,
            String description,
            String evidenceUrl,
            ParticipationStatus status,
            String memberReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record Report(
            UUID requestId,
            ParticipationTargetType targetType,
            UUID targetId,
            ReportType reportType,
            String description,
            String evidenceUrl,
            ParticipationStatus status,
            String memberReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record Page<T>(java.util.List<T> items, int number, int size, long totalElements) {

        public int totalPages() {
            return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        }

        public boolean hasNext() {
            return number < totalPages();
        }
    }
}
