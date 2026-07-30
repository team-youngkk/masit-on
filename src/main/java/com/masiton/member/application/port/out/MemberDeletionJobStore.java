package com.masiton.member.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MemberDeletionJobStore {
    void enqueue(UUID memberId, Instant now);
    List<UUID> claimDue(Instant now, int limit);
    boolean hasExceededOneHour(UUID memberId, Instant now);
    void reschedule(UUID memberId, Instant now);
    void complete(UUID memberId);
}
