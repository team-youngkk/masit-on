package com.masiton.participation.application;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.masiton.common.web.BusinessException;
import com.masiton.participation.application.port.out.ParticipationStore;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;
import com.masiton.participation.domain.ReportType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@DisplayName("회원 제보·신고 서비스")
class ParticipationServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-04T03:00:00Z");

    private ParticipationStore store;
    private ParticipationService service;

    @BeforeEach
    void setUp() {
        store = mock(ParticipationStore.class);
        service = new ParticipationService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("접수는 회원 행을 잠근 뒤 합산 한도와 열린 중복을 검사하고 저장한다")
    void 제보접수_정상입력_잠금후저장한다() {
        ParticipationRequest.Submission request = submission("새로운 맛집 등록을 제안합니다.");
        ParticipationView.Submission created = submissionView();
        given(store.findOpenSubmission(eq(MEMBER_ID), eq(ParticipationTargetType.RESTAURANT), any()))
                .willReturn(Optional.empty());
        given(store.insertSubmission(any(), eq(MEMBER_ID), eq(ParticipationTargetType.RESTAURANT),
                any(), any(), any(), any(), any())).willReturn(created);

        ParticipationView.Submission result = service.createSubmission(MEMBER_ID, request);

        assertThat(result).isEqualTo(created);
        InOrder order = inOrder(store);
        order.verify(store).lockMember(MEMBER_ID);
        order.verify(store).countCreated(eq(MEMBER_ID), any(), any());
        order.verify(store).findOpenSubmission(eq(MEMBER_ID), eq(ParticipationTargetType.RESTAURANT), any());
        order.verify(store).insertSubmission(any(), eq(MEMBER_ID), eq(ParticipationTargetType.RESTAURANT),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("제보와 신고 합산이 이미 5건이면 저장하지 않고 429를 반환한다")
    void 접수_일일다섯건_저장하지않는다() {
        given(store.countCreated(eq(MEMBER_ID), any(), any())).willReturn(5L);

        assertThatThrownBy(() -> service.createSubmission(MEMBER_ID, submission("새 맛집 등록을 제안합니다.")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("DAILY_REQUEST_LIMIT_EXCEEDED");
                    assertThat(exception.retryAfterSeconds()).isPositive();
                });
        verify(store, never()).insertSubmission(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("스크립트와 제어 문자는 필드 오류로 거부한다")
    void 제보접수_악성설명_거부한다() {
        assertThatThrownBy(() -> service.createSubmission(
                MEMBER_ID, submission("<script>alert(1)</script>")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_FIELD_VALUE"));
        verify(store, never()).lockMember(any());
    }

    @Test
    @DisplayName("줄바꿈과 탭 제어 문자는 로그 위조를 막기 위해 거부한다")
    void 제보접수_줄바꿈과탭_거부한다() {
        assertThatThrownBy(() -> service.createSubmission(
                MEMBER_ID, submission("정상 설명\r\nWARN 위조 로그")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_FIELD_VALUE"));
        assertThatThrownBy(() -> service.createSubmission(
                MEMBER_ID, submission("정상 설명\t숨은 필드")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("대상 유형과 맞지 않는 신고 유형은 대상 조회 전에 거부한다")
    void 신고접수_잘못된유형조합_거부한다() {
        ParticipationRequest.Report request = new ParticipationRequest.Report(
                ParticipationTargetType.RESTAURANT,
                UUID.randomUUID(),
                ReportType.WRONG_RELATIONSHIP,
                "잘못 연결된 정보라고 생각합니다.",
                null);

        assertThatThrownBy(() -> service.createReport(MEMBER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_FIELD_VALUE"));
        verify(store, never()).targetExists(any(), any());
    }

    @Test
    @DisplayName("신고 대상 존재 확인보다 회원 행 검증을 먼저 수행한다")
    void 신고접수_없는회원과대상_회원검증을먼저한다() {
        UUID targetId = UUID.randomUUID();
        ParticipationRequest.Report request = new ParticipationRequest.Report(
                ParticipationTargetType.RESTAURANT, targetId, ReportType.ERROR,
                "잘못된 맛집 정보라고 생각합니다.", null);
        given(store.targetExists(ParticipationTargetType.RESTAURANT, targetId)).willReturn(false);

        assertThatThrownBy(() -> service.createReport(MEMBER_ID, request))
                .isInstanceOf(ParticipationException.class);

        InOrder order = inOrder(store);
        order.verify(store).lockMember(MEMBER_ID);
        order.verify(store).targetExists(ParticipationTargetType.RESTAURANT, targetId);
    }

    @Test
    @DisplayName("채널 URL path 대소문자는 지문에서 보존한다")
    void 제보접수_대소문자가다른채널경로_서로다른지문을만든다() {
        ParticipationView.Submission created = submissionView();
        given(store.findOpenSubmission(eq(MEMBER_ID), eq(ParticipationTargetType.CREATOR), any()))
                .willReturn(Optional.empty());
        given(store.insertSubmission(any(), eq(MEMBER_ID), eq(ParticipationTargetType.CREATOR),
                any(), any(), any(), any(), any())).willReturn(created);
        ParticipationRequest.Submission first = new ParticipationRequest.Submission(
                ParticipationTargetType.CREATOR, Map.of("channelUrl", "https://example.com/ChannelA"),
                "새 채널 등록을 제안합니다.", null);
        ParticipationRequest.Submission second = new ParticipationRequest.Submission(
                ParticipationTargetType.CREATOR, Map.of("channelUrl", "https://example.com/channela"),
                "새 채널 등록을 제안합니다.", null);
        ParticipationRequest.Submission hostCase = new ParticipationRequest.Submission(
                ParticipationTargetType.CREATOR, Map.of("channelUrl", "https://EXAMPLE.com:443/ChannelA"),
                "새 채널 등록을 제안합니다.", null);

        service.createSubmission(MEMBER_ID, first);
        service.createSubmission(MEMBER_ID, second);
        service.createSubmission(MEMBER_ID, hostCase);

        ArgumentCaptor<byte[]> fingerprints = ArgumentCaptor.forClass(byte[].class);
        verify(store, times(3)).findOpenSubmission(
                eq(MEMBER_ID), eq(ParticipationTargetType.CREATOR), fingerprints.capture());
        assertThat(fingerprints.getAllValues().get(0))
                .isNotEqualTo(fingerprints.getAllValues().get(1));
        assertThat(fingerprints.getAllValues().get(0))
                .isEqualTo(fingerprints.getAllValues().get(2));
    }

    @Test
    @DisplayName("다른 회원이 소유한 상세은 없는 요청과 같은 404로 숨긴다")
    void 상세조회_소유행없음_기능404를반환한다() {
        UUID requestId = UUID.randomUUID();
        given(store.findReport(MEMBER_ID, requestId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReport(MEMBER_ID, requestId))
                .isInstanceOfSatisfying(ParticipationException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("REPORT_NOT_FOUND");
                });
    }

    private ParticipationRequest.Submission submission(String description) {
        return new ParticipationRequest.Submission(
                ParticipationTargetType.RESTAURANT,
                Map.of("name", "새 맛집", "roadAddress", "서울특별시 테스트로 1"),
                description,
                "https://example.com/evidence");
    }

    private ParticipationView.Submission submissionView() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new ParticipationView.Submission(
                UUID.randomUUID(), ParticipationTargetType.RESTAURANT,
                Map.of("name", "새 맛집", "roadAddress", "서울특별시 테스트로 1"),
                "새로운 맛집 등록을 제안합니다.", "https://example.com/evidence",
                ParticipationStatus.RECEIVED, null, now, now);
    }
}
