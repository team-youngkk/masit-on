package com.masiton.member.application.port.out;

import java.time.Instant;
import java.util.List;

import com.masiton.member.application.MemberSessionRevocation;

public interface MemberSessionRevocationRecoveryQueue {

    void enqueue(MemberSessionRevocation revocation, Instant now);

    List<MemberSessionRevocation> claimDue(Instant now, int limit);

    void complete(MemberSessionRevocation revocation);
}
