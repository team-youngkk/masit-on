package com.masiton.security.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.security.application.port.out.ConfirmationTokenRepositoryPort;
import com.masiton.security.domain.model.ConfirmationToken;
import com.masiton.security.domain.model.ConfirmationTokenResourceType;
import com.masiton.security.domain.model.ConfirmationTokenStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("확인 토큰 서비스")
class ConfirmationTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ConfirmationTokenRepositoryPort confirmationTokenRepository = mock(ConfirmationTokenRepositoryPort.class);
    private final ConfirmationTokenCleanupService confirmationTokenCleanupService = mock(ConfirmationTokenCleanupService.class);
    private final ConfirmationTokenService service = new ConfirmationTokenService(
            confirmationTokenRepository, confirmationTokenCleanupService, CLOCK, new SecureRandom());

    @Test
    @DisplayName("검증된 미리보기에서 SHA-256 해시만 저장하고 10분 토큰을 발급한다")
    void issue_검증된미리보기_SHA256해시와10분토큰발급() throws Exception {
        UUID adminId = UUID.randomUUID();
        ConfirmationTokenIssueCommand command = new ConfirmationTokenIssueCommand(
                adminId,
                ConfirmationTokenResourceType.RESTAURANT,
                (short) 1,
                "kakao:123",
                "{\"name\":\"맛잇온\"}");

        IssuedConfirmationToken issued = service.issue(command);

        org.mockito.ArgumentCaptor<ConfirmationToken> tokenCaptor =
                org.mockito.ArgumentCaptor.forClass(ConfirmationToken.class);
        verify(confirmationTokenRepository).save(tokenCaptor.capture());
        ConfirmationToken token = tokenCaptor.getValue();
        assertThat(issued.rawToken()).doesNotContain("맛잇온");
        assertThat(token.getTokenHash())
                .containsExactly(MessageDigest.getInstance("SHA-256")
                        .digest(issued.rawToken().getBytes(StandardCharsets.UTF_8)));
        assertThat(token.getTokenHash()).hasSize(32);
        assertThat(issued.expiresAt()).isEqualTo(OffsetDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC));
        assertThat(token.getAdminAccountId()).isEqualTo(adminId);
        assertThat(token.getResourceType()).isEqualTo(ConfirmationTokenResourceType.RESTAURANT);
        assertThat(token.getStatus()).isEqualTo(ConfirmationTokenStatus.ISSUED);
        assertThat(token.getCandidateSnapshot()).isEqualTo("{\"name\":\"맛잇온\"}");
    }

    @Test
    @DisplayName("같은 관리자와 자원의 발급 토큰은 잠근 미리보기 snapshot을 반환한다")
    void acquire_유효한발급토큰_잠긴Snapshot반환() {
        UUID tokenId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ConfirmationToken token = issuedToken(tokenId, adminId, ConfirmationTokenResourceType.CREATOR, NOW.plusSeconds(600));
        when(confirmationTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token));

        AcquiredConfirmationToken acquired = service.acquire(
                "raw-token", adminId, ConfirmationTokenResourceType.CREATOR);

        assertThat(acquired.tokenId()).isEqualTo(tokenId);
        assertThat(acquired.candidateSnapshot()).isEqualTo("{\"name\":\"candidate\"}");
        assertThat(acquired.isReplay()).isFalse();
        verify(confirmationTokenRepository).findByTokenHashForUpdate(any());
    }

    @Test
    @DisplayName("다른 관리자나 자원 종류의 토큰은 유효하지 않은 확인 토큰으로 거부한다")
    void acquire_다른관리자또는자원_유효하지않은확인토큰() {
        UUID ownerId = UUID.randomUUID();
        when(confirmationTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(
                issuedToken(UUID.randomUUID(), ownerId, ConfirmationTokenResourceType.VIDEO, NOW.plusSeconds(600))));

        assertThatThrownBy(() -> service.acquire("raw-token", UUID.randomUUID(), ConfirmationTokenResourceType.VIDEO))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.INVALID_CONFIRMATION_TOKEN.name());
    }

    @Test
    @DisplayName("미사용 만료 토큰은 확인 만료 오류로 거부한다")
    void acquire_미사용만료토큰_확인만료오류() {
        UUID adminId = UUID.randomUUID();
        when(confirmationTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(
                issuedToken(UUID.randomUUID(), adminId, ConfirmationTokenResourceType.VIDEO, NOW)));

        assertThatThrownBy(() -> service.acquire("raw-token", adminId, ConfirmationTokenResourceType.VIDEO))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(ErrorCode.VERIFICATION_EXPIRED.name());
    }

    @Test
    @DisplayName("완료된 토큰은 만료 시각 후에도 결과 식별자를 재생한다")
    void acquire_완료토큰_결과식별자재생() {
        UUID adminId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        ConfirmationToken completed = new ConfirmationToken(
                UUID.randomUUID(), new byte[32], adminId, ConfirmationTokenResourceType.VIDEO, (short) 1,
                "youtube:video", "{\"name\":\"candidate\"}", ConfirmationTokenStatus.CREATED,
                OffsetDateTime.ofInstant(NOW.minusSeconds(1200), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minusSeconds(600), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minusSeconds(900), ZoneOffset.UTC), resourceId);
        when(confirmationTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(completed));

        AcquiredConfirmationToken acquired = service.acquire("raw-token", adminId, ConfirmationTokenResourceType.VIDEO);

        assertThat(acquired.isReplay()).isTrue();
        assertThat(acquired.resultResourceId()).isEqualTo(resourceId);
        assertThat(acquired.status()).isEqualTo(ConfirmationTokenStatus.CREATED);
    }

    @Test
    @DisplayName("완료 상태 갱신은 ISSUED 상태일 때만 성공해야 한다")
    void completeCreated_발급상태만_생성결과로완료() {
        UUID tokenId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        when(confirmationTokenRepository.completeIssuedToken(
                eq(tokenId), eq(ConfirmationTokenStatus.CREATED), eq(resourceId), any())).thenReturn(true);

        service.completeCreated(tokenId, resourceId);

        verify(confirmationTokenRepository).completeIssuedToken(
                eq(tokenId), eq(ConfirmationTokenStatus.CREATED), eq(resourceId), any());
    }

    @Test
    @DisplayName("보관 기한 정리에 실패해도 새 확인 토큰을 발급한다")
    void issue_보관기한정리실패_새토큰을발급한다() {
        doThrow(new RuntimeException("cleanup failed"))
                .when(confirmationTokenCleanupService).deleteExpiredRetentionRecords();
        ConfirmationTokenIssueCommand command = new ConfirmationTokenIssueCommand(
                UUID.randomUUID(),
                ConfirmationTokenResourceType.RESTAURANT,
                (short) 1,
                "kakao:123",
                "{\"name\":\"candidate\"}");

        assertThatCode(() -> service.issue(command)).doesNotThrowAnyException();

        verify(confirmationTokenRepository).save(any());
    }

    private ConfirmationToken issuedToken(
            UUID tokenId,
            UUID adminId,
            ConfirmationTokenResourceType resourceType,
            Instant expiresAt) {
        return new ConfirmationToken(
                tokenId,
                new byte[32],
                adminId,
                resourceType,
                (short) 1,
                "external:identity",
                "{\"name\":\"candidate\"}",
                ConfirmationTokenStatus.ISSUED,
                OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
                null,
                null);
    }
}
