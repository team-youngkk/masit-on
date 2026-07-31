package com.masiton.member.application;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.member.application.port.out.MemberAccountRepository;
import com.masiton.member.domain.model.MemberAccount;
import com.masiton.member.domain.model.MemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("활성 회원 잠금 서비스")
class MemberActiveLockServiceTest {

    private final MemberAccountRepository accounts = mock(MemberAccountRepository.class);
    private final MemberActiveLockService service = new MemberActiveLockService(accounts);

    @Test
    @DisplayName("활성 회원이면 FOR UPDATE 조회만 수행한다")
    void lockActiveMember_activeMember_succeeds() {
        UUID memberId = UUID.randomUUID();
        when(accounts.findByIdForUpdate(memberId)).thenReturn(Optional.of(
                new MemberAccount(memberId, "member@example.com", "hash", MemberStatus.ACTIVE, null, null, null)));

        service.lockActiveMember(memberId);

        verify(accounts).findByIdForUpdate(memberId);
    }

    @Test
    @DisplayName("활성 회원이 아니면 AUTHENTICATION_REQUIRED를 던진다")
    void lockActiveMember_inactiveMember_rejected() {
        UUID memberId = UUID.randomUUID();
        when(accounts.findByIdForUpdate(memberId)).thenReturn(Optional.of(
                new MemberAccount(memberId, "member@example.com", "hash", MemberStatus.DELETION_PENDING, null, null, null)));

        assertThatThrownBy(() -> service.lockActiveMember(memberId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.code()).isEqualTo("AUTHENTICATION_REQUIRED");
                });
    }
}
