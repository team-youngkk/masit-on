package com.masiton.member.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.masiton.member.application.port.out.MemberActionMailOutboxStore;
import com.masiton.member.application.port.out.MemberActionTokenCipher;
import com.masiton.member.application.port.out.MemberActionTokenDeliveryPort;
import com.masiton.member.domain.model.MemberActionMailOutbox;
import com.masiton.member.domain.model.MemberActionPurpose;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 Action 메일 outbox dispatcher")
class MemberActionMailOutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:10:00Z");

    @Mock
    private MemberActionMailOutboxStore outbox;
    @Mock
    private MemberActionTokenCipher tokenCipher;
    @Mock
    private MemberActionTokenDeliveryPort delivery;

    @Test
    @DisplayName("claim한 PENDING 메일을 복호화해 전송한 뒤 SENT로 완료한다")
    void run_전송성공_SENT완료() {
        MemberActionMailOutboxDelivery work = work();
        given(outbox.claimDue(NOW, 50)).willReturn(List.of(work));
        given(outbox.confirmDelivery(work.outbox().id(), NOW)).willReturn(true);
        given(tokenCipher.decrypt(work.outbox())).willReturn("raw-token");

        service().run();

        verify(outbox).cancelIneligible(NOW);
        verify(delivery).send("member@example.com", MemberActionPurpose.EMAIL_VERIFICATION, "raw-token");
        verify(outbox).markSent(work.outbox().id(), NOW);
        verify(outbox, never()).reschedule(any(), any());
    }

    @Test
    @DisplayName("메일 전송이 실패하면 SENT로 완료하지 않고 재시도하도록 예약한다")
    void run_전송실패_재시도예약() {
        MemberActionMailOutboxDelivery work = work();
        given(outbox.claimDue(NOW, 50)).willReturn(List.of(work));
        given(outbox.confirmDelivery(work.outbox().id(), NOW)).willReturn(true);
        given(tokenCipher.decrypt(work.outbox())).willReturn("raw-token");
        doThrow(new IllegalStateException("smtp unavailable")).when(delivery)
                .send("member@example.com", MemberActionPurpose.EMAIL_VERIFICATION, "raw-token");

        service().run();

        verify(outbox).reschedule(work.outbox().id(), NOW);
        verify(outbox, never()).markSent(any(), any());
    }

    @Test
    @DisplayName("claim 뒤 Token이 무효화되면 암호문을 복호화하거나 메일을 전송하지 않는다")
    void run_Claim뒤Token무효화_전송없음() {
        MemberActionMailOutboxDelivery work = work();
        given(outbox.claimDue(NOW, 50)).willReturn(List.of(work));
        given(outbox.confirmDelivery(work.outbox().id(), NOW)).willReturn(false);

        service().run();

        verify(outbox).confirmDelivery(work.outbox().id(), NOW);
        verifyNoInteractions(tokenCipher, delivery);
        verify(outbox, never()).markSent(any(), any());
        verify(outbox, never()).reschedule(any(), any());
    }

    private MemberActionMailOutboxService service() {
        return new MemberActionMailOutboxService(outbox, tokenCipher, delivery, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private MemberActionMailOutboxDelivery work() {
        return new MemberActionMailOutboxDelivery(new MemberActionMailOutbox(
                UUID.randomUUID(), UUID.randomUUID(), MemberActionPurpose.EMAIL_VERIFICATION,
                new byte[17], new byte[12], "test-1"), "member@example.com");
    }
}
