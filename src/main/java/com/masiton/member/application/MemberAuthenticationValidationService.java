package com.masiton.member.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.masiton.member.application.port.out.LoadMemberAuthenticationStatePort;

@Service
public class MemberAuthenticationValidationService {

    private final LoadMemberAuthenticationStatePort loadMemberAuthenticationStatePort;

    public MemberAuthenticationValidationService(
            LoadMemberAuthenticationStatePort loadMemberAuthenticationStatePort
    ) {
        this.loadMemberAuthenticationStatePort = loadMemberAuthenticationStatePort;
    }

    public Optional<MemberPrincipal> validate(String memberIdClaim, String sessionIdClaim, Instant now) {
        UUID memberId = parseUuid(memberIdClaim);
        UUID sessionId = parseUuid(sessionIdClaim);
        if (memberId == null || sessionId == null) {
            return Optional.empty();
        }
        MemberAuthenticationState state = loadMemberAuthenticationStatePort.load(memberId, sessionId, now);
        if (!state.active() || state.revoked()) {
            return Optional.empty();
        }
        return Optional.of(new MemberPrincipal(memberId.toString(), sessionId.toString()));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
