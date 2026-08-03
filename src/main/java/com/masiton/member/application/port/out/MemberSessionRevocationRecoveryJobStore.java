package com.masiton.member.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.masiton.member.application.MemberSessionRevocation;

public interface MemberSessionRevocationRecoveryJobStore {

    void enqueue(MemberSessionRevocation revocation, Instant now);

    List<MemberSessionRevocation> claimDue(Instant now, int limit);

    void complete(UUID sessionId);

    void reschedule(UUID sessionId, Instant now);

    List<UUID> findUnresolvedBefore(Instant cutoff, Instant now, int limit);
}
