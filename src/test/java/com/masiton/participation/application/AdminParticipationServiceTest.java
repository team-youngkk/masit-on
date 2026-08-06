package com.masiton.participation.application;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.common.web.BusinessException;
import com.masiton.participation.application.port.in.AdminParticipationUseCase.UpdateStatusCommand;
import com.masiton.participation.application.port.out.AdminParticipationStore;
import com.masiton.participation.application.port.out.ParticipationCompletionReader;
import com.masiton.participation.domain.ModerationActionType;
import com.masiton.participation.domain.ParticipationStatus;
import com.masiton.participation.domain.ParticipationTargetType;

import com.masiton.notification.application.port.in.CreateNotificationUseCase;
import com.masiton.notification.domain.model.NotificationRequestType;
import com.masiton.notification.domain.model.NotificationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("관리자 제보·신고 검토 서비스")
class AdminParticipationServiceTest {

    private static final UUID REQUEST_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID TARGET_ID = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID MEMBER_ID = UUID.fromString("40000000-0000-4000-8000-000000000004");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-05T12:00:00Z");

    private AdminParticipationStore store;
    private ParticipationCompletionReader completionReader;
    private CreateNotificationUseCase createNotificationUseCase;
    private AdminParticipationService service;

    @BeforeEach
    void setUp() {
        store = mock(AdminParticipationStore.class);
        completionReader = mock(ParticipationCompletionReader.class);
        createNotificationUseCase = mock(CreateNotificationUseCase.class);
        service = new AdminParticipationService(store, completionReader, createNotificationUseCase,
                Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("동일 상태 재요청은 최초 값과 이력을 유지한다")
    void 상태변경_동일상태_아무것도갱신하지않는다() {
        AdminParticipationView.Submission current = submission(ParticipationStatus.IN_REVIEW);
        given(store.findSubmission(REQUEST_ID, true)).willReturn(Optional.of(current));

        AdminParticipationView.Submission result = service.updateSubmission(REQUEST_ID, ADMIN_ID,
                new UpdateStatusCommand(ParticipationStatus.IN_REVIEW, "새 사유", "<새 메모>", null), "trace");

        assertThat(result).isSameAs(current);
        verify(store, never()).updateSubmission(any(), any(), any(), any(), any(), any(), any());
        verify(store, never()).insertSubmissionHistory(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("허용되지 않은 상태 전이는 409 오류를 반환한다")
    void 상태변경_금지전이_충돌오류를반환한다() {
        given(store.findSubmission(REQUEST_ID, true)).willReturn(Optional.of(submission(ParticipationStatus.RECEIVED)));

        assertThatThrownBy(() -> service.updateSubmission(REQUEST_ID, ADMIN_ID,
                new UpdateStatusCommand(ParticipationStatus.ACCEPTED, null, null, null), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_STATUS_TRANSITION"));

        verify(store, never()).updateSubmission(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("완료 결과가 원 신고 대상과 다르면 원본 조치 미완료로 거부한다")
    void 신고완료_다른대상결과_원본조치미완료를반환한다() {
        given(store.findReport(REQUEST_ID, true)).willReturn(Optional.of(report(ParticipationStatus.ACCEPTED)));
        AdminParticipationView.Result result = new AdminParticipationView.Result(
                ModerationActionType.HIDDEN, ParticipationTargetType.RESTAURANT, UUID.randomUUID());

        assertThatThrownBy(() -> service.updateReport(REQUEST_ID, ADMIN_ID,
                new UpdateStatusCommand(ParticipationStatus.COMPLETED, "처리가 완료되었습니다", null, result), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SOURCE_ACTION_NOT_COMPLETED"));

        verify(completionReader, never()).isCompleted(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("검증된 완료 전이는 요청 갱신과 감사 이력을 함께 저장한다")
    void 신고완료_원본조치완료_갱신과감사를저장한다() {
        AdminParticipationView.Report current = report(ParticipationStatus.ACCEPTED);
        AdminParticipationView.Report completed = report(ParticipationStatus.COMPLETED);
        AdminParticipationView.Result result = new AdminParticipationView.Result(
                ModerationActionType.HIDDEN, ParticipationTargetType.RESTAURANT, TARGET_ID);
        given(store.findReport(REQUEST_ID, true)).willReturn(Optional.of(current));
        given(store.findReport(REQUEST_ID, false)).willReturn(Optional.of(completed));
        given(completionReader.isCompleted(ModerationActionType.HIDDEN,
                ParticipationTargetType.RESTAURANT, TARGET_ID, NOW, null)).willReturn(true);

        AdminParticipationView.Report actual = service.updateReport(REQUEST_ID, ADMIN_ID,
                new UpdateStatusCommand(ParticipationStatus.COMPLETED,
                        "  처리가 완료되었습니다  ", "  관리자 확인 메모  ", result), "trace-id");

        assertThat(actual).isEqualTo(completed);
        verify(store).updateReport(REQUEST_ID, ParticipationStatus.COMPLETED,
                "처리가 완료되었습니다", "관리자 확인 메모", result, NOW, NOW);
        verify(store).insertReportHistory(REQUEST_ID, ADMIN_ID, ParticipationStatus.ACCEPTED,
                ParticipationStatus.COMPLETED, "처리가 완료되었습니다", "관리자 확인 메모",
                result, "trace-id", NOW);
    }

    @Test
    @DisplayName("제보 완료 검증은 접수 후보와 승인 시각을 원본 조회에 전달한다")
    void 제보완료_후보연결검증_후보와승인시각을전달한다() {
        AdminParticipationView.Submission current = submission(ParticipationStatus.ACCEPTED);
        AdminParticipationView.Result result = new AdminParticipationView.Result(
                ModerationActionType.CREATED, ParticipationTargetType.RESTAURANT, TARGET_ID);
        given(store.findSubmission(REQUEST_ID, true)).willReturn(Optional.of(current));
        given(store.findSubmission(REQUEST_ID, false)).willReturn(Optional.of(submission(ParticipationStatus.COMPLETED)));
        given(completionReader.isCompleted(ModerationActionType.CREATED,
                ParticipationTargetType.RESTAURANT, TARGET_ID, NOW, current.candidate())).willReturn(true);

        service.updateSubmission(REQUEST_ID, ADMIN_ID,
                new UpdateStatusCommand(ParticipationStatus.COMPLETED, "등록을 완료했습니다", null, result), "trace");

        verify(completionReader).isCompleted(ModerationActionType.CREATED,
                ParticipationTargetType.RESTAURANT, TARGET_ID, NOW, current.candidate());
    }

    @Test
    @DisplayName("회원 사유와 내부 메모의 꺾쇠와 제어 문자를 거부한다")
    void 상태변경_위험텍스트_입력오류를반환한다() {
        given(store.findSubmission(REQUEST_ID, true)).willReturn(Optional.of(submission(ParticipationStatus.RECEIVED)));

        assertThatThrownBy(() -> service.updateSubmission(REQUEST_ID, ADMIN_ID,
                new UpdateStatusCommand(ParticipationStatus.IN_REVIEW, null, "<svg>", null), "trace"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("상태 변경 성공 시 알림 생성을 호출한다")
    void 상태변경_성공_알림생성을호출한다() {
        AdminParticipationView.Submission current = submission(ParticipationStatus.RECEIVED);
        given(store.findSubmission(REQUEST_ID, true)).willReturn(Optional.of(current));
        given(store.findSubmission(REQUEST_ID, false)).willReturn(Optional.of(submission(ParticipationStatus.IN_REVIEW)));

        service.updateSubmission(REQUEST_ID, ADMIN_ID,
                new UpdateStatusCommand(ParticipationStatus.IN_REVIEW, null, "검토 시작", null), "trace");

        verify(createNotificationUseCase).create(
                current.memberId(), NotificationRequestType.SUBMISSION, REQUEST_ID, NotificationStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("회원 연결이 없는 요청은 알림 생성을 호출하지 않는다")
    void 상태변경_회원연결없음_알림생성을호출하지않는다() {
        AdminParticipationView.Submission submissionNoMember = new AdminParticipationView.Submission(
                REQUEST_ID, null, ParticipationTargetType.RESTAURANT, Map.of("name", "후보"),
                "설명입니다 충분합니다", null, ParticipationStatus.RECEIVED, null, null, null, NOW, NOW, List.of());
        given(store.findSubmission(REQUEST_ID, true)).willReturn(Optional.of(submissionNoMember));
        given(store.findSubmission(REQUEST_ID, false)).willReturn(Optional.of(submissionNoMember));

        service.updateSubmission(REQUEST_ID, ADMIN_ID,
                new UpdateStatusCommand(ParticipationStatus.IN_REVIEW, null, "검토 시작", null), "trace");

        verify(createNotificationUseCase, never()).create(any(), any(), any(), any());
    }

    private AdminParticipationView.Submission submission(ParticipationStatus status) {
        return new AdminParticipationView.Submission(REQUEST_ID, MEMBER_ID,
                ParticipationTargetType.RESTAURANT, Map.of("name", "후보"), "설명입니다 충분합니다",
                null, status, null, null, null, NOW, NOW, List.of());
    }

    private AdminParticipationView.Report report(ParticipationStatus status) {
        return new AdminParticipationView.Report(REQUEST_ID, MEMBER_ID,
                ParticipationTargetType.RESTAURANT, TARGET_ID, com.masiton.participation.domain.ReportType.ERROR,
                "설명입니다 충분합니다", null, status, null, null, null, NOW, NOW, List.of());
    }
}
