package com.masiton.member.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.application.port.out.MemberActionTokenRepository;
import com.masiton.member.application.port.out.MemberDeletionJobStore;
import com.masiton.member.application.port.out.MemberParticipationUnlinkPort;

@Service
public class MemberDeletionCleanupCommandService {
    private final MemberDeletionJobStore jobs;
    private final MemberAccountRepository accounts;
    private final MemberActionTokenRepository actionTokens;
    private final MemberParticipationUnlinkPort participationUnlink;
    private final Clock clock;

    public MemberDeletionCleanupCommandService(MemberDeletionJobStore jobs, MemberAccountRepository accounts,
            MemberActionTokenRepository actionTokens, MemberParticipationUnlinkPort participationUnlink,
            Clock memberSessionClock) {
        this.jobs = jobs;
        this.accounts = accounts;
        this.actionTokens = actionTokens;
        this.participationUnlink = participationUnlink;
        this.clock = memberSessionClock;
    }

    @Transactional
    public void cleanup(UUID memberId) {
        actionTokens.deleteByMemberId(memberId);
        participationUnlink.unlinkMemberParticipation(memberId, OffsetDateTime.now(clock));
        accounts.deleteById(memberId);
        jobs.complete(memberId);
    }
}
