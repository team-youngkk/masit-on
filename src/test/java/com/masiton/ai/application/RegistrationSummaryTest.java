package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.masiton.ai.application.port.out.AiRegistrationUnitStore.RegistrationUnitRow;

class RegistrationSummaryTest {
    @Test
    @DisplayName("6개 등록과 4개 보류를 현재 등록 6개와 부분 등록으로 요약한다")
    void 집계_6개등록4개보류_부분등록() {
        // Given
        List<RegistrationUnitRow> units = new ArrayList<>();
        for (int i = 0; i < 6; i++) units.add(unit("AUTO_CONFIRMED", true, null, null));
        for (int i = 0; i < 4; i++) units.add(unit("AUTO_BLOCKED", false, null, null));
        // When
        RegistrationSummary result = RegistrationSummary.from(units);
        // Then
        assertThat(result).isEqualTo(new RegistrationSummary(10, 6, 4, 0, 0, 6, true));
    }
    @Test
    @DisplayName("전체 등록과 전체 보류 및 등록 단위 0개를 구분한다")
    void 집계_전체및빈목록_등록결과구분() {
        // Given / When / Then
        assertThat(RegistrationSummary.from(List.of(unit("AUTO_CONFIRMED", true, null, null))))
                .isEqualTo(new RegistrationSummary(1, 1, 0, 0, 0, 1, false));
        assertThat(RegistrationSummary.from(List.of(unit("AUTO_BLOCKED", false, null, null))))
                .isEqualTo(new RegistrationSummary(1, 0, 1, 0, 0, 0, false));
        assertThat(RegistrationSummary.from(List.of()))
                .isEqualTo(new RegistrationSummary(0, 0, 0, 0, 0, 0, false));
    }
    @Test
    @DisplayName("수동 등록은 포함하고 롤백과 폐기 및 거부는 현재 등록에서 제외한다")
    void 집계_수동종결혼합_등록유지만포함() {
        // Given
        OffsetDateTime now = OffsetDateTime.now();
        List<RegistrationUnitRow> units = List.of(unit("MANUAL_OVERRIDE", true, null, null),
                unit("MANUAL_OVERRIDE", false, now, null), unit("MANUAL_OVERRIDE", false, null, now),
                unit("AUTO_REJECTED", false, null, null));
        // When
        RegistrationSummary result = RegistrationSummary.from(units);
        // Then
        assertThat(result).isEqualTo(new RegistrationSummary(4, 0, 0, 1, 3, 1, true));
    }
    private RegistrationUnitRow unit(String status, boolean registered, OffsetDateTime rollback, OffsetDateTime discard) {
        UUID id = registered ? UUID.randomUUID() : null;
        return new RegistrationUnitRow(UUID.randomUUID(), UUID.randomUUID(), 0, "맛집", status, null, null, null,
                id, id, id, id, List.of(), "WORKER", null, rollback, discard);
    }
}
