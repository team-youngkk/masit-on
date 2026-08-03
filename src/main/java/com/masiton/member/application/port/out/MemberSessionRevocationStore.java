package com.masiton.member.application.port.out;

import com.masiton.member.application.MemberSessionRevocation;

public interface MemberSessionRevocationStore {

    void record(MemberSessionRevocation revocation);

    boolean isRevoked(java.util.UUID sessionId, java.time.Instant now);
}
