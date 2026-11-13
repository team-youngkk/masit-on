package com.masiton.ai.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.AdminAiExtractionQueryService;
import com.masiton.ai.application.port.out.AiExtractionAdminQueryPort;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore;
import com.masiton.ai.application.port.out.dto.AiExtractionJobView;

import tools.jackson.databind.ObjectMapper;

@DisplayName("관리자 AI 영상 추출 상세 응답")
class AdminAiVideoExtractionControllerTest {

    @Test
    @DisplayName("로컬 차단과 제공자 HTTP 403은 서로 다른 사유로 표시한다")
    void failureDiagnostic_로컬차단과제공자403_서로다른사유를반환한다() {
        var local = AdminAiVideoExtractionController.FailureDiagnostic.from("PROVIDER_BLOCKED", null,
                OffsetDateTime.now());
        var provider = AdminAiVideoExtractionController.FailureDiagnostic.from("PROVIDER_BLOCKED", 403,
                OffsetDateTime.now());

        assertThat(local.reasonCode()).isEqualTo("PROVIDER_CONFIGURATION_OR_POLICY_BLOCKED");
        assertThat(provider.reasonCode()).isEqualTo("PROVIDER_ACCESS_DENIED");
    }

    @Test
    @DisplayName("과거 UPSTREAM은 상세 원인 미기록으로, 현재 HTTP 실패는 상태와 함께 표시한다")
    void 진단_과거오류와현재HTTP실패를_구분한다() {
        OffsetDateTime now = OffsetDateTime.now();
        var legacy = AdminAiVideoExtractionController.FailureDiagnostic.from("UPSTREAM", null, now);
        var payment = AdminAiVideoExtractionController.FailureDiagnostic.from("PAYMENT_REQUIRED", 402, now);
        var quota = AdminAiVideoExtractionController.FailureDiagnostic.from("QUOTA_HARD_STOP", null, now);

        assertThat(legacy.reasonCode()).isEqualTo("DETAIL_NOT_RECORDED");
        assertThat(legacy.httpStatus()).isNull();
        assertThat(payment.reasonCode()).isEqualTo("PROVIDER_PAYMENT_REQUIRED");
        assertThat(payment.httpStatus()).isEqualTo(402);
        assertThat(payment.providerCode()).isNull();
        assertThat(quota.service()).isEqualTo("APPLICATION");
        assertThat(quota.httpStatus()).isNull();
    }

    @Test
    @DisplayName("식당별 주소 누락은 같은 식당의 후보만 조회한다")
    void 차단진단_다른식당주소를_사용하지않는다() {
        ObjectMapper mapper = new ObjectMapper();
        var candidates = mapper.readTree("""
                {"registrationUnits":[
                  {"restaurantName":{"value":"식당A"},"visitEvidence":{"value":"방문"}},
                  {"restaurantName":{"value":"식당B"},"address":{"value":"서울"},
                   "location":{"value":"서울"},"visitEvidence":{"value":"방문"}}]}
                """);
        var blocked = new AiRegistrationUnitStore.RegistrationUnitRow(
                UUID.randomUUID(), UUID.randomUUID(), 1, "식당A", "AUTO_BLOCKED", "MISSING_REQUIRED_FIELD",
                null, null, null, null, null, null, List.of(), "WORKER", OffsetDateTime.now(), null, null);

        var response = AdminAiVideoExtractionController.RegistrationUnitResponse.from(blocked, mapper, candidates);

        assertThat(response.blockDiagnostic().reasonCode()).isEqualTo("MISSING_REQUIRED_FIELD");
        assertThat(response.blockDiagnostic().missingFields()).contains("address", "location");
    }

    @Test
    @DisplayName("기존에 저장된 장소 판정 legacy matchedBy를 상세 응답에서 그대로 보존한다")
    void detail_기존legacyMatchedBy를_응답에서그대로보존한다() {
        UUID jobId = UUID.randomUUID();
        AiExtractionJobView job = new AiExtractionJobView(jobId, "ADMIN", "channel", "video",
                "https://www.youtube.com/watch?v=video", "SUCCEEDED", "COMPLETE", "AUTO_CONFIRMED",
                "GOOGLE_GEMINI", "gemini-3.5-flash-lite", "P8", "S2", 1,
                OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), false);
        AiExtractionAdminQueryPort.Detail detail = new AiExtractionAdminQueryPort.Detail(
                job, null, null, null, null, null, false, null, null, List.of());
        AiRegistrationUnitStore.RegistrationUnitRow row = new AiRegistrationUnitStore.RegistrationUnitRow(
                UUID.randomUUID(), UUID.randomUUID(), 1, "우래옥", "AUTO_CONFIRMED", null,
                "{\"matchedBy\":\"NAME_CONTAINS_AND_DISTRICT_AND_CATEGORY\"}",
                "{\"resolvedBy\":\"MENU_EXPRESSION\"}", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), List.of(), "WORKER", OffsetDateTime.now(), null, null);
        AdminAiExtractionQueryService.AdminJobDetail jobDetail =
                new AdminAiExtractionQueryService.AdminJobDetail(detail, List.of(row), "AUTO_CONFIRMED");

        AdminAiVideoExtractionController.DetailResponse response =
                AdminAiVideoExtractionController.DetailResponse.from(jobDetail, new ObjectMapper());

        assertThat(response.registrationUnits()).hasSize(1);
        assertThat(response.registrationUnits().get(0).placeDecision().get("matchedBy").asText())
                .isEqualTo("NAME_CONTAINS_AND_DISTRICT_AND_CATEGORY");
    }
}
