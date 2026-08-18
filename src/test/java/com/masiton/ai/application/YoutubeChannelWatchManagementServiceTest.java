package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.ai.application.port.out.AiExtractionJobStore;
import com.masiton.ai.application.port.out.TemporaryInputCipher;
import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;
import com.masiton.ai.application.port.out.YoutubeChannelWatchSubscriptionPort;
import com.masiton.ai.application.port.out.YoutubeChannelWatchVerificationTokenPort;
import com.masiton.common.web.BusinessException;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;
import com.masiton.video.application.port.in.ResolveVerifiedVideoUseCase;

@DisplayName("YouTube 채널 감시 관리 서비스")
class YoutubeChannelWatchManagementServiceTest {

    private final FindCreatorReferenceUseCase creatorReferences = mock(FindCreatorReferenceUseCase.class);
    private final YoutubeChannelWatchStore watchStore = mock(YoutubeChannelWatchStore.class);
    private final YoutubeChannelWatchPersistenceService watchPersistence = mock(YoutubeChannelWatchPersistenceService.class);
    private final YoutubeChannelWatchVerificationTokenPort verificationTokens = mock(
            YoutubeChannelWatchVerificationTokenPort.class);
    private final YoutubeChannelWatchSubscriptionPort subscriptions = mock(YoutubeChannelWatchSubscriptionPort.class);
    private final YoutubeChannelWatchManagementService service = new YoutubeChannelWatchManagementService(
            creatorReferences, watchPersistence, verificationTokens, subscriptions);

    @Test
    @DisplayName("여러 유튜버의 감시 상태를 한 번에 조회하고 기존 감시 행도 유지한다")
    void 감시목록조회_여러유튜버_상태를한번에조회하고기존행을유지한다() {
        UUID activeId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        UUID unavailableId = UUID.randomUUID();
        when(watchPersistence.findCandidatePage(20, 0)).thenReturn(new YoutubeChannelWatchStore.WatchCandidatePage(List.of(
                new YoutubeChannelWatchStore.WatchCandidate(activeId, "활성 채널", true, true, "channel-active",
                        Optional.of(new YoutubeChannelWatchStore.WatchDetail(true, "ACTIVE", null, null, null))),
                new YoutubeChannelWatchStore.WatchCandidate(inactiveId, "비활성 채널", true, true, "channel-inactive",
                        Optional.empty()),
                new YoutubeChannelWatchStore.WatchCandidate(unavailableId, "비공개 채널", false, false, "channel-unavailable",
                        Optional.of(new YoutubeChannelWatchStore.WatchDetail(false, "INACTIVE", null, null, "CREATOR_HIDDEN")))), 3));

        var result = service.getStatuses(1, 20);

        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).status().subscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(result.items().get(1).status().subscriptionStatus()).isEqualTo("INACTIVE");
        assertThat(result.items().get(2).channelName()).isEqualTo("비공개 채널");
        verify(watchPersistence).findCandidatePage(20, 0);

        when(watchPersistence.findCandidatePage(50, (long) (Integer.MAX_VALUE - 1) * 50))
                .thenReturn(new YoutubeChannelWatchStore.WatchCandidatePage(List.of(), 3));
        assertThat(service.getStatuses(Integer.MAX_VALUE, 50).items()).isEmpty();
    }

    @Test
    @DisplayName("검증된 Creator에 Watch가 없으면 비활성 초기 상태를 반환한다")
    void 감시조회_검증된Creator에Watch없음_비활성초기상태를반환한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        when(watchPersistence.findDetail("channel-id")).thenReturn(Optional.empty());

        var result = service.getStatus(creatorId);

        assertThat(result.enabled()).isFalse();
        assertThat(result.subscriptionStatus()).isEqualTo("INACTIVE");
        assertThat(result.lastNotificationAt()).isNull();
        assertThat(result.lastRenewedAt()).isNull();
        assertThat(result.lastErrorCategory()).isNull();
        verify(watchPersistence).findDetail("channel-id");
    }

    @Test
    @DisplayName("검증된 Creator의 Watch가 있으면 저장된 상태 필드를 그대로 반환한다")
    void 감시조회_기존Watch_저장된상태필드를그대로반환한다() {
        UUID creatorId = UUID.randomUUID();
        OffsetDateTime notifiedAt = OffsetDateTime.parse("2026-08-12T01:02:03Z");
        OffsetDateTime renewedAt = OffsetDateTime.parse("2026-08-13T04:05:06Z");
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        when(watchPersistence.findDetail("channel-id")).thenReturn(Optional.of(
                new YoutubeChannelWatchStore.WatchDetail(true, "RENEWAL_FAILED", notifiedAt, renewedAt,
                        "SUBSCRIPTION_TIMEOUT")));

        var result = service.getStatus(creatorId);

        assertThat(result.enabled()).isTrue();
        assertThat(result.subscriptionStatus()).isEqualTo("RENEWAL_FAILED");
        assertThat(result.lastNotificationAt()).isEqualTo(notifiedAt);
        assertThat(result.lastRenewedAt()).isEqualTo(renewedAt);
        assertThat(result.lastErrorCategory()).isEqualTo("SUBSCRIPTION_TIMEOUT");
    }

    @Test
    @DisplayName("검증 상태가 바뀐 Creator도 기존 Watch 상태를 조회한다")
    void 감시조회_검증상태변경Creator_기존Watch상태를조회한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, false, false)));
        when(watchPersistence.findDetail("channel-id")).thenReturn(Optional.of(
                new YoutubeChannelWatchStore.WatchDetail(false, "INACTIVE", null, null, null)));

        var result = service.getStatus(creatorId);

        assertThat(result.subscriptionStatus()).isEqualTo("INACTIVE");
        verify(watchPersistence).findDetail("channel-id");
    }

    @Test
    @DisplayName("없는 Creator는 404 CREATOR_NOT_FOUND를 반환한다")
    void 감시조회_없는Creator_404CREATOR_NOT_FOUND를반환한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(creatorId))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("CREATOR_NOT_FOUND");
                });

        verifyNoInteractions(watchPersistence);
    }

    @Test
    @DisplayName("검증된 Creator 감시를 활성화하면 외부 확인 전 UNKNOWN 상태를 저장한다")
    void 감시설정_검증된Creator활성화_외부확인전UNKNOWN상태를저장한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        when(verificationTokens.issue("channel-id")).thenReturn("verify-token");
        OffsetDateTime notifiedAt = OffsetDateTime.parse("2026-08-12T01:00:00Z");
        when(watchPersistence.prepareActivation(eq(creatorId), eq("channel-id"), any())).thenReturn(
                new YoutubeChannelWatchPersistenceService.ActivationPreparation(
                        new YoutubeChannelWatchStore.WatchDetail(true, "UNKNOWN", notifiedAt, null, null), true,
                        Optional.empty(), hashToken("verify-token")));

        var result = service.setEnabled(creatorId, true);

        assertThat(result.enabled()).isTrue();
        assertThat(result.subscriptionStatus()).isEqualTo("UNKNOWN");
        assertThat(result.lastNotificationAt()).isEqualTo(notifiedAt);
        verify(watchPersistence).prepareActivation(eq(creatorId), eq("channel-id"), eq(hashToken("verify-token")));
        verify(subscriptions).subscribe("channel-id", "verify-token");
        InOrder order = inOrder(watchPersistence, subscriptions);
        order.verify(watchPersistence).prepareActivation(eq(creatorId), eq("channel-id"), any());
        order.verify(subscriptions).subscribe("channel-id", "verify-token");
    }

    @Test
    @DisplayName("활성화한 채널은 저장된 해시로 challenge 검증까지 성공한다")
    void 감시설정_활성화후challenge_저장된해시로ACTIVE전환한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        when(verificationTokens.issue("channel-id")).thenReturn("verify-token");
        when(watchPersistence.prepareActivation(eq(creatorId), eq("channel-id"), any()))
                .thenReturn(new YoutubeChannelWatchPersistenceService.ActivationPreparation(
                        new YoutubeChannelWatchStore.WatchDetail(true, "UNKNOWN", null, null, null), true,
                        Optional.empty(), hashToken("verify-token")));

        service.setEnabled(creatorId, true);

        ArgumentCaptor<byte[]> hash = ArgumentCaptor.forClass(byte[].class);
        verify(watchPersistence).prepareActivation(eq(creatorId), eq("channel-id"), hash.capture());
        verify(subscriptions).subscribe("channel-id", "verify-token");
        when(watchStore.findForUpdate("channel-id")).thenReturn(
                Optional.of(new YoutubeChannelWatchStore.Watch("channel-id", true, "UNKNOWN", hash.getValue())));

        AiExtractionJobService jobService = new AiExtractionJobService(
                mock(ResolveVerifiedVideoUseCase.class),
                new AiExtractionJobPersistenceService(mock(AiExtractionJobStore.class)),
                watchStore,
                mock(TemporaryInputCipher.class));

        assertThat(jobService.verifyChallenge("channel-id", "verify-token", "challenge"))
                .isEqualTo("challenge");
        verify(watchStore).markSubscriptionVerified(eq("channel-id"), any());
    }

    @Test
    @DisplayName("이미 ACTIVE인 채널을 중복 활성화해도 기존 구독 상태를 유지한다")
    void 감시설정_이미ACTIVE중복활성화_기존구독상태를유지한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        when(watchPersistence.preserveActive(creatorId, "channel-id"))
                .thenReturn(Optional.of(new YoutubeChannelWatchStore.WatchDetail(true, "ACTIVE", null, null, null)));

        var result = service.setEnabled(creatorId, true);

        assertThat(result.subscriptionStatus()).isEqualTo("ACTIVE");
        verifyNoInteractions(verificationTokens);
        verify(watchPersistence, never()).prepareActivation(any(), any(), any());
        verify(subscriptions, never()).subscribe(any(), any());
    }

    @Test
    @DisplayName("Hub 구독 실패는 오류 범주를 기록하고 신규 Watch를 저장하지 않는다")
    void 감시설정_Hub구독실패_오류범주를기록하고Watch를저장하지않는다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        when(watchPersistence.preserveActive(creatorId, "channel-id")).thenReturn(Optional.empty());
        when(verificationTokens.issue("channel-id")).thenReturn("verify-token");
        when(watchPersistence.prepareActivation(eq(creatorId), eq("channel-id"), any()))
                .thenReturn(new YoutubeChannelWatchPersistenceService.ActivationPreparation(
                        new YoutubeChannelWatchStore.WatchDetail(true, "UNKNOWN", null, null, null), true,
                        Optional.empty(), hashToken("verify-token")));
        org.mockito.Mockito.doThrow(new YoutubeChannelWatchSubscriptionFailedException("SUBSCRIPTION_5XX"))
                .when(subscriptions).subscribe("channel-id", "verify-token");

        assertThatThrownBy(() -> service.setEnabled(creatorId, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("EXTERNAL_SERVICE_ERROR"));

        verify(watchPersistence).compensateExplicitFailure(eq(creatorId), eq("channel-id"), any());
        verify(watchPersistence, never()).recordSubscriptionFailure(any(), any(), any());
    }

    @Test
    @DisplayName("Hub timeout은 pending Watch를 재조정 대기 실패 상태로 남긴다")
    void 감시설정_Hubtimeout_pendingWatch를RENEWAL_FAILED로남긴다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        when(watchPersistence.preserveActive(creatorId, "channel-id")).thenReturn(Optional.empty());
        when(verificationTokens.issue("channel-id")).thenReturn("verify-token");
        when(watchPersistence.prepareActivation(eq(creatorId), eq("channel-id"), any()))
                .thenReturn(new YoutubeChannelWatchPersistenceService.ActivationPreparation(
                        new YoutubeChannelWatchStore.WatchDetail(true, "UNKNOWN", null, null, null), true,
                        Optional.empty(), hashToken("verify-token")));
        org.mockito.Mockito.doThrow(new YoutubeChannelWatchSubscriptionFailedException("SUBSCRIPTION_TIMEOUT"))
                .when(subscriptions).subscribe("channel-id", "verify-token");

        assertThatThrownBy(() -> service.setEnabled(creatorId, true))
                .isInstanceOf(BusinessException.class);

        verify(watchPersistence).recordSubscriptionFailure(eq("channel-id"), eq("SUBSCRIPTION_TIMEOUT"), any());
        verify(watchPersistence, never()).compensateExplicitFailure(any(), any(), any());
    }

    @Test
    @DisplayName("감시를 비활성화하면 기존 메타데이터를 보존한 INACTIVE 상태를 저장한다")
    void 감시설정_비활성화_기존메타데이터를보존한INACTIVE상태를저장한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        OffsetDateTime renewedAt = OffsetDateTime.parse("2026-08-12T01:00:00Z");
        when(watchPersistence.disable(creatorId, "channel-id")).thenReturn(
                new YoutubeChannelWatchStore.WatchDetail(false, "INACTIVE", null, renewedAt, "RENEWAL_FAILED"));

        var result = service.setEnabled(creatorId, false);

        assertThat(result.enabled()).isFalse();
        assertThat(result.subscriptionStatus()).isEqualTo("INACTIVE");
        assertThat(result.lastRenewedAt()).isEqualTo(renewedAt);
        assertThat(result.lastErrorCategory()).isEqualTo("RENEWAL_FAILED");
    }

    @Test
    @DisplayName("없거나 공개 또는 외부 이용이 불가능한 Creator는 저장하지 않고 404 CREATOR_NOT_FOUND를 반환한다")
    void 감시설정_유효하지않은Creator_저장없이404CREATOR_NOT_FOUND를반환한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, false, true)));

        assertThatThrownBy(() -> service.setEnabled(creatorId, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(businessException.code()).isEqualTo("CREATOR_NOT_FOUND");
                });

        verifyNoInteractions(watchPersistence, subscriptions, verificationTokens);
    }

    @Test
    @DisplayName("검증 상태가 바뀐 Creator도 감시를 비활성화할 수 있다")
    void 감시설정_검증상태변경Creator_비활성화할수있다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, false, false)));
        when(watchPersistence.disable(creatorId, "channel-id"))
                .thenReturn(new YoutubeChannelWatchStore.WatchDetail(false, "INACTIVE", null, null, null));

        YoutubeChannelWatchManagementUseCase.WatchStatus result = service.setEnabled(creatorId, false);

        assertThat(result.enabled()).isFalse();
        verify(watchPersistence).disable(creatorId, "channel-id");
    }

    private FindCreatorReferenceUseCase.CreatorReference creator(UUID id, boolean publiclyVisible,
                                                                   boolean externallyAvailable) {
        return new FindCreatorReferenceUseCase.CreatorReference(id, "channel-id", publiclyVisible,
                externallyAvailable);
    }

    private byte[] hashToken(String token) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
