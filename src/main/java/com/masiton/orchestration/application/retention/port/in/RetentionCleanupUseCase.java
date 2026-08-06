package com.masiton.orchestration.application.retention.port.in;

public interface RetentionCleanupUseCase {
    int unlinkExpiredParticipationMemberReferences();

    int deleteExpiredNotifications();
}
