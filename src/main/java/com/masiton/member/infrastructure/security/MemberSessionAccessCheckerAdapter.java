package com.masiton.member.infrastructure.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.masiton.common.security.MemberSessionAccessChecker;
import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.application.port.out.MemberSessionRevocationStore;
import com.masiton.member.domain.model.MemberAccount;

@Component
public class MemberSessionAccessCheckerAdapter implements MemberSessionAccessChecker {
    private final MemberAccountRepository accounts;
    private final MemberSessionRevocationStore revocations;

    public MemberSessionAccessCheckerAdapter(
            MemberAccountRepository accounts,
            MemberSessionRevocationStore revocations
    ) {
        this.accounts = accounts;
        this.revocations = revocations;
    }

    @Override
    public AccessDecision check(String memberId, String sessionId) {
        try {
            UUID accountId = UUID.fromString(memberId);
            UUID parsedSessionId = UUID.fromString(sessionId);
            boolean allowed = accounts.findById(accountId).filter(MemberAccount::canAuthenticate).isPresent()
                    && !revocations.isRevoked(parsedSessionId, Instant.now());
            return allowed ? AccessDecision.ALLOWED : AccessDecision.DENIED;
        } catch (IllegalArgumentException exception) {
            return AccessDecision.DENIED;
        } catch (RuntimeException exception) {
            return AccessDecision.UNAVAILABLE;
        }
    }
}
