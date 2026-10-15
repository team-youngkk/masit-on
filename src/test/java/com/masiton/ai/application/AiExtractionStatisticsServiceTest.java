package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.masiton.ai.application.port.out.AiExtractionStatisticsPort;
import com.masiton.common.web.BusinessException;

@DisplayName("AI 통계 필터 검증")
class AiExtractionStatisticsServiceTest {
    private final AiExtractionStatisticsPort port = mock(AiExtractionStatisticsPort.class);
    private final AiExtractionStatisticsService service = new AiExtractionStatisticsService(port);
    @Test
    @DisplayName("버전 제어 문자는 조회 포트를 호출하지 않는다")
    void 조회_버전제어문자_포트를호출하지않는다() {
        for (String value : new String[]{"x" + (char) 0, "x" + (char) 1 + "y", "x\n"}) {
            for (int index = 0; index < 3; index++) {
                var filter = new AiExtractionStatisticsPort.Filter(from, from.plusDays(1), null,
                        index == 0 ? value : null, index == 1 ? value : null, index == 2 ? value : null);
                assertThatThrownBy(() -> service.query(filter)).isInstanceOf(BusinessException.class);
            }
        }
        verifyNoInteractions(port);
    }

    private final OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("365일의 정상 범위는 조회 포트에 그대로 전달한다")
    void 조회_정상필터_조회포트로전달한다() {
        var filter = new AiExtractionStatisticsPort.Filter(from, from.plusDays(365), "ADMIN", "m", "P8", "S2");
        service.query(filter);
        verify(port).query(filter);
    }

    @Test
    @DisplayName("빈 역전 초과 기간과 잘못된 차원은 조회 전에 거부한다")
    void 조회_잘못된필터_조회전에거부한다() {
        for (var filter : java.util.List.of(
                new AiExtractionStatisticsPort.Filter(from, from, null, null, null, null),
                new AiExtractionStatisticsPort.Filter(from, from.minusDays(1), null, null, null, null),
                new AiExtractionStatisticsPort.Filter(from, from.plusDays(366), null, null, null, null),
                new AiExtractionStatisticsPort.Filter(from, from.plusDays(1), "unknown", null, null, null),
                new AiExtractionStatisticsPort.Filter(from, from.plusDays(1), null, " ", null, null),
                new AiExtractionStatisticsPort.Filter(from, from.plusDays(1), null, null, "x".repeat(65), null))) {
            assertThatThrownBy(() -> service.query(filter)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }
}
