package com.masiton.member.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.application.port.out.MemberActionTokenRepository;
import com.masiton.member.application.port.out.MemberDeletionJobStore;

@Service
public class MemberDeletionCleanupCommandService {
    private final MemberDeletionJobStore jobs;
    private final MemberAccountRepository accounts;
    private final MemberActionTokenRepository actionTokens;

    public MemberDeletionCleanupCommandService(MemberDeletionJobStore jobs, MemberAccountRepository accounts,
            MemberActionTokenRepository actionTokens) {
        this.jobs = jobs;
        this.accounts = accounts;
        this.actionTokens = actionTokens;
    }

    @Transactional
    public void cleanup(UUID memberId) {
        actionTokens.deleteByMemberId(memberId);
        accounts.deleteById(memberId);
        jobs.complete(memberId);
    }
}
