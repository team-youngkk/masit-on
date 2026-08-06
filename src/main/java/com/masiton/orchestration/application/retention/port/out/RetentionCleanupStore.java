package com.masiton.orchestration.application.retention.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RetentionCleanupStore {
    int unlinkExpiredSubmissionMembers(OffsetDateTime cutoff, OffsetDateTime unlinkedAt, int limit);

    int unlinkExpiredReportMembers(OffsetDateTime cutoff, OffsetDateTime unlinkedAt, int limit);

    int deleteExpiredNotifications(OffsetDateTime cutoff, int limit);

    void unlinkMemberParticipation(UUID memberId, OffsetDateTime unlinkedAt);
}
