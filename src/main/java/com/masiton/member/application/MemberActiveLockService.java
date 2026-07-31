package com.masiton.member.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.masiton.common.web.BusinessException;
import com.masiton.member.application.port.in.LockActiveMemberUseCase;
import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.domain.model.MemberAccount;

@Service
class MemberActiveLockService implements LockActiveMemberUseCase {

    private final MemberAccountRepository accounts;

    MemberActiveLockService(MemberAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public void lockActiveMember(UUID memberId) {
        accounts.findByIdForUpdate(memberId)
                .filter(MemberAccount::canAuthenticate)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "인증이 필요합니다."));
    }
}
