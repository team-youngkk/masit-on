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
