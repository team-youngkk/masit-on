package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.masiton.ai.application.port.in.YoutubeChannelWatchManagementUseCase;
import com.masiton.ai.application.port.out.YoutubeChannelWatchStore;
import com.masiton.common.web.BusinessException;
import com.masiton.creator.application.port.in.FindCreatorReferenceUseCase;

@DisplayName("YouTube 채널 감시 관리 서비스")
class YoutubeChannelWatchManagementServiceTest {

    private final FindCreatorReferenceUseCase creatorReferences = mock(FindCreatorReferenceUseCase.class);
    private final YoutubeChannelWatchStore watchStore = mock(YoutubeChannelWatchStore.class);
    private final YoutubeChannelWatchManagementService service = new YoutubeChannelWatchManagementService(
            creatorReferences, watchStore);

    @Test
    @DisplayName("검증된 Creator 감시를 활성화하면 ACTIVE 상태를 저장하고 메타데이터를 반환한다")
    void 감시설정_검증된Creator활성화_ACTIVE상태를저장하고메타데이터를반환한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        OffsetDateTime notifiedAt = OffsetDateTime.parse("2026-08-12T01:00:00Z");
        when(watchStore.upsert(creatorId, "channel-id", true, "ACTIVE")).thenReturn(
                new YoutubeChannelWatchStore.WatchDetail(true, "ACTIVE", notifiedAt, null, null));

        var result = service.setEnabled(creatorId, true);

        assertThat(result.enabled()).isTrue();
        assertThat(result.subscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(result.lastNotificationAt()).isEqualTo(notifiedAt);
        verify(watchStore).upsert(creatorId, "channel-id", true, "ACTIVE");
    }

    @Test
    @DisplayName("감시를 비활성화하면 기존 메타데이터를 보존한 INACTIVE 상태를 저장한다")
    void 감시설정_비활성화_기존메타데이터를보존한INACTIVE상태를저장한다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, true, true)));
        OffsetDateTime renewedAt = OffsetDateTime.parse("2026-08-12T01:00:00Z");
        when(watchStore.upsert(creatorId, "channel-id", false, "INACTIVE")).thenReturn(
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

        verifyNoInteractions(watchStore);
    }

    @Test
    @DisplayName("검증 상태가 바뀐 Creator도 감시를 비활성화할 수 있다")
    void 감시설정_검증상태변경Creator_비활성화할수있다() {
        UUID creatorId = UUID.randomUUID();
        when(creatorReferences.findCreatorReference(creatorId)).thenReturn(Optional.of(creator(creatorId, false, false)));
        when(watchStore.upsert(creatorId, "channel-id", false, "INACTIVE"))
                .thenReturn(new YoutubeChannelWatchStore.WatchDetail(false, "INACTIVE", null, null, null));

        YoutubeChannelWatchManagementUseCase.WatchStatus result = service.setEnabled(creatorId, false);

        assertThat(result.enabled()).isFalse();
        verify(watchStore).upsert(creatorId, "channel-id", false, "INACTIVE");
    }

    private FindCreatorReferenceUseCase.CreatorReference creator(UUID id, boolean publiclyVisible,
                                                                   boolean externallyAvailable) {
        return new FindCreatorReferenceUseCase.CreatorReference(id, "channel-id", publiclyVisible,
                externallyAvailable);
    }
}
