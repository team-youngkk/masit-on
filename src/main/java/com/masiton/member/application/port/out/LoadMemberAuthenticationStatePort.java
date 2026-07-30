package com.masiton.member.application.port.out;

import java.time.Instant;
import java.util.UUID;

import com.masiton.member.application.MemberAuthenticationState;

public interface LoadMemberAuthenticationStatePort {

    MemberAuthenticationState load(UUID memberId, UUID sessionId, Instant now);
}
