package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;
import com.masiton.common.web.BusinessException;

@DisplayName("관리자 AI 추출 검토 서비스")
class AdminAiExtractionQueryServiceTest {
    private final AiExtractionAdminQueryPort port = mock(AiExtractionAdminQueryPort.class);
    private final AiRegistrationUnitStore registrationUnitStore = mock(AiRegistrationUnitStore.class);
    private final RegistrationUnitCommandService registrationUnitCommandService =
            mock(RegistrationUnitCommandService.class);
    private final AdminAiExtractionQueryService service = new AdminAiExtractionQueryService(
            port, registrationUnitStore, registrationUnitCommandService);

    @Test
    @DisplayName("등록 단위가 있으면 최상위 reviewStatus를 요약값으로 계산해 반환한다")
    void detail_등록단위존재_최상위reviewStatus를요약값으로계산한다() {
        UUID jobId = UUID.randomUUID();
        AiExtractionJobView job = new AiExtractionJobView(jobId, "ADMIN", "channel", "video",
                "https://www.youtube.com/watch?v=video", "SUCCEEDED", "COMPLETE", "AUTO_BLOCKED",
                "GOOGLE_GEMINI", "gemini-3.5-flash-lite", "P8", "S2", 1,
                OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), false);
        AiExtractionAdminQueryPort.Detail detail = new AiExtractionAdminQueryPort.Detail(
                job, null, null, null, null, null, false, null, null, List.of());
        given(port.detail(jobId)).willReturn(Optional.of(detail));
        AiRegistrationUnitStore.RegistrationUnitRow confirmedUnit = new AiRegistrationUnitStore.RegistrationUnitRow(
                UUID.randomUUID(), UUID.randomUUID(), 1, "확정 맛집", "AUTO_CONFIRMED", null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), "WORKER",
                OffsetDateTime.now(), null, null);
        AiRegistrationUnitStore.RegistrationUnitRow blockedUnit = new AiRegistrationUnitStore.RegistrationUnitRow(
                UUID.randomUUID(), UUID.randomUUID(), 2, "차단 맛집", "AUTO_BLOCKED", "PLACE_AMBIGUOUS", null, null,
                null, null, null, null, List.of(), "WORKER", OffsetDateTime.now(), null, null);
        given(registrationUnitStore.findByJobId(jobId)).willReturn(List.of(confirmedUnit, blockedUnit));

        AdminAiExtractionQueryService.AdminJobDetail result = service.detail(jobId);

        assertThat(result.topReviewStatus()).isEqualTo("AUTO_BLOCKED");
        assertThat(result.registrationUnits()).hasSize(2);
    }

    @Test
    @DisplayName("등록 단위가 없으면 Snapshot 판정값을 그대로 최상위 reviewStatus로 쓴다")
    void detail_등록단위없음_snapshot판정값을그대로쓴다() {
        UUID jobId = UUID.randomUUID();
        AiExtractionJobView job = new AiExtractionJobView(jobId, "ADMIN", "channel", "video",
                "https://www.youtube.com/watch?v=video", "SUCCEEDED", "PARTIAL", "AUTO_REJECTED",
                "GOOGLE_GEMINI", "gemini-3.5-flash-lite", "P8", "S2", 1,
                OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), false);
        AiExtractionAdminQueryPort.Detail detail = new AiExtractionAdminQueryPort.Detail(
                job, null, null, null, null, null, false, null, null, List.of());
        given(port.detail(jobId)).willReturn(Optional.of(detail));
        given(registrationUnitStore.findByJobId(jobId)).willReturn(List.of());

        AdminAiExtractionQueryService.AdminJobDetail result = service.detail(jobId);

        assertThat(result.topReviewStatus()).isEqualTo("AUTO_REJECTED");
        assertThat(result.registrationUnits()).isEmpty();
    }

    @Test
    @DisplayName("review 호출은 등록 단위 명령 서비스로 그대로 위임한다")
    void review_등록단위명령서비스로위임한다() {
        UUID jobId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        service.review(jobId, "ROLLBACK", "unit-1", "오등록", null, null, List.of(), adminId);

        verify(registrationUnitCommandService).review(jobId, "ROLLBACK", "unit-1", "오등록", null, null, List.of(),
                adminId);
    }

    @Test
    @DisplayName("실행 상태가 FAILED이면 재시도 가능하다")
    void retryUrl_실행상태FAILED_재시도가능하다() {
        UUID jobId = UUID.randomUUID();
        AiExtractionAdminQueryPort.RetryTarget target = new AiExtractionAdminQueryPort.RetryTarget(
                "https://www.youtube.com/watch?v=video", "FAILED", null);
        given(port.retryTarget(jobId)).willReturn(Optional.of(target));

        String videoUrl = service.retryUrl(jobId);

        assertThat(videoUrl).isEqualTo("https://www.youtube.com/watch?v=video");
    }

    @Test
    @DisplayName("실행 상태가 SUCCEEDED이고 완결성이 PARTIAL이면 재시도 가능하다")
    void retryUrl_실행상태SUCCEEDED_완결성PARTIAL_재시도가능하다() {
        UUID jobId = UUID.randomUUID();
        AiExtractionAdminQueryPort.RetryTarget target = new AiExtractionAdminQueryPort.RetryTarget(
                "https://www.youtube.com/watch?v=video", "SUCCEEDED", "PARTIAL");
        given(port.retryTarget(jobId)).willReturn(Optional.of(target));

        String videoUrl = service.retryUrl(jobId);

        assertThat(videoUrl).isEqualTo("https://www.youtube.com/watch?v=video");
    }

    @Test
    @DisplayName("실행 상태가 SUCCEEDED/COMPLETE이어도 등록 단위 중 하나가 AUTO_BLOCKED이면 재시도 가능하다")
    void retryUrl_완결이어도등록단위AUTO_BLOCKED존재하면_재시도가능하다() {
        UUID jobId = UUID.randomUUID();
        AiExtractionAdminQueryPort.RetryTarget target = new AiExtractionAdminQueryPort.RetryTarget(
                "https://www.youtube.com/watch?v=video", "SUCCEEDED", "COMPLETE");
        given(port.retryTarget(jobId)).willReturn(Optional.of(target));
        AiRegistrationUnitStore.RegistrationUnitRow confirmedUnit = new AiRegistrationUnitStore.RegistrationUnitRow(
                UUID.randomUUID(), UUID.randomUUID(), 1, "확정 맛집", "AUTO_CONFIRMED", null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), "WORKER",
                OffsetDateTime.now(), null, null);
        AiRegistrationUnitStore.RegistrationUnitRow blockedUnit = new AiRegistrationUnitStore.RegistrationUnitRow(
                UUID.randomUUID(), UUID.randomUUID(), 2, "차단 맛집", "AUTO_BLOCKED", "MISSING_REQUIRED_FIELD", null,
                null, null, null, null, null, List.of(), "WORKER", OffsetDateTime.now(), null, null);
        given(registrationUnitStore.findByJobId(jobId)).willReturn(List.of(confirmedUnit, blockedUnit));

        String videoUrl = service.retryUrl(jobId);

        assertThat(videoUrl).isEqualTo("https://www.youtube.com/watch?v=video");
    }

    @Test
    @DisplayName("실행 상태가 SUCCEEDED/COMPLETE이고 등록 단위가 모두 AUTO_BLOCKED가 아니면 재시도를 거절한다")
    void retryUrl_완결이고등록단위전부AUTO_BLOCKED아니면_재시도거절한다() {
        UUID jobId = UUID.randomUUID();
        AiExtractionAdminQueryPort.RetryTarget target = new AiExtractionAdminQueryPort.RetryTarget(
                "https://www.youtube.com/watch?v=video", "SUCCEEDED", "COMPLETE");
        given(port.retryTarget(jobId)).willReturn(Optional.of(target));
        AiRegistrationUnitStore.RegistrationUnitRow confirmedUnit = new AiRegistrationUnitStore.RegistrationUnitRow(
                UUID.randomUUID(), UUID.randomUUID(), 1, "확정 맛집", "AUTO_CONFIRMED", null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), "WORKER",
                OffsetDateTime.now(), null, null);
        given(registrationUnitStore.findByJobId(jobId)).willReturn(List.of(confirmedUnit));

        assertThatThrownBy(() -> service.retryUrl(jobId))
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::code)
                .isEqualTo("AIEXTRACT_RETRY_BLOCKED");
    }

    @Test
    @DisplayName("registerUnit 호출은 등록 단위 명령 서비스로 그대로 위임한다")
    void registerUnit_등록단위명령서비스로위임한다() {
        UUID jobId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        RegistrationUnitCommandService.RegistrationExecutionView expected =
                new RegistrationUnitCommandService.RegistrationExecutionView(
                        unitId, "AUTO_CONFIRMED", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), List.of(), null, null);
        given(registrationUnitCommandService.registerUnit(jobId, unitId)).willReturn(expected);

        RegistrationUnitCommandService.RegistrationExecutionView result = service.registerUnit(jobId, unitId);

        assertThat(result).isSameAs(expected);
    }
}
