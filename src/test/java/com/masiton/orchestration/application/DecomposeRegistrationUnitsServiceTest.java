package com.masiton.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase.DecomposeRegistrationUnitsCommand;
import com.masiton.orchestration.application.port.in.DecomposeRegistrationUnitsUseCase.RegistrationUnitBundle;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("BR-AIEXTRACT-001 등록 단위 분해")
class DecomposeRegistrationUnitsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DecomposeRegistrationUnitsService service = new DecomposeRegistrationUnitsService();

    @Test
    @DisplayName("모든 필드가 후보 1건이면 등록 단위 하나로 묶는다")
    void decompose_모든필드후보1건_등록단위하나로묶는다() {
        JsonNode fields = json("""
                {
                  "restaurantName": "행복식당",
                  "address": "서울특별시 마포구 월드컵로 1",
                  "menu": "냉면",
                  "visitEvidence": "직접 방문했습니다"
                }
                """);
        JsonNode confidences = json("""
                {"restaurantName":0.9,"address":0.8,"menu":0.7,"visitEvidence":0.95}
                """);
        JsonNode evidence = json("""
                {
                  "restaurantName": {"type":"TIMESTAMP","startMs":1,"endMs":2},
                  "address": {"type":"TEXT_RANGE","startOffset":1,"endOffset":5,"sourceHash":"hash"},
                  "menu": {"type":"TIMESTAMP","startMs":3,"endMs":4},
                  "visitEvidence": {"type":"TIMESTAMP","startMs":5,"endMs":6}
                }
                """);

        List<RegistrationUnitBundle> bundles = service.decompose(
                new DecomposeRegistrationUnitsCommand(fields, confidences, evidence));

        assertThat(bundles).hasSize(1);
        RegistrationUnitBundle bundle = bundles.get(0);
        assertThat(bundle.unitIndex()).isEqualTo(1);
        assertThat(bundle.restaurantName().value()).isEqualTo("행복식당");
        assertThat(bundle.restaurantName().confidence()).isEqualByComparingTo(BigDecimal.valueOf(0.9));
        assertThat(bundle.address().value()).isEqualTo("서울특별시 마포구 월드컵로 1");
        assertThat(bundle.menu().value()).isEqualTo("냉면");
        assertThat(bundle.visitEvidence().value()).isEqualTo("직접 방문했습니다");
    }

    @Test
    @DisplayName("맛집명 후보가 없으면 빈 목록을 반환한다")
    void decompose_맛집명후보없음_빈목록을반환한다() {
        JsonNode fields = json("""
                {"address": "서울특별시 마포구 월드컵로 1"}
                """);

        List<RegistrationUnitBundle> bundles = service.decompose(
                new DecomposeRegistrationUnitsCommand(fields, json("{}"), json("{}")));

        assertThat(bundles).isEmpty();
    }

    @Test
    @DisplayName("결속된 필드 후보가 없으면 그 단위의 값은 결측으로 남긴다")
    void decompose_결속필드후보없음_결측으로남긴다() {
        JsonNode fields = json("""
                {"restaurantName": "행복식당"}
                """);

        List<RegistrationUnitBundle> bundles = service.decompose(
                new DecomposeRegistrationUnitsCommand(fields, json("{}"), json("{}")));

        assertThat(bundles).hasSize(1);
        RegistrationUnitBundle bundle = bundles.get(0);
        assertThat(bundle.restaurantName().value()).isEqualTo("행복식당");
        assertThat(bundle.address()).isNull();
        assertThat(bundle.menu()).isNull();
        assertThat(bundle.visitEvidence()).isNull();
    }

    @Test
    @DisplayName("맛집명과 주소 후보가 모두 복수면 같은 순번끼리 결속한다")
    void decompose_복수후보_같은순번끼리결속한다() {
        JsonNode fields = json("""
                {
                  "restaurantName": [
                    {"value":"첫 맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"value":"둘째 맛집","confidence":0.94,"evidence":{"type":"TIMESTAMP","startMs":30,"endMs":40}}
                  ],
                  "address": [
                    {"value":"서울특별시 마포구 월드컵로 1","confidence":0.9,"evidence":{"type":"TIMESTAMP","startMs":11,"endMs":21}},
                    {"value":"서울특별시 영등포구 여의대로 10","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":31,"endMs":41}}
                  ],
                  "menu": "냉면"
                }
                """);

        List<RegistrationUnitBundle> bundles = service.decompose(
                new DecomposeRegistrationUnitsCommand(fields, json("{}"), json("{}")));

        assertThat(bundles).hasSize(2);
        RegistrationUnitBundle first = bundles.get(0);
        assertThat(first.unitIndex()).isEqualTo(1);
        assertThat(first.restaurantName().value()).isEqualTo("첫 맛집");
        assertThat(first.address().value()).isEqualTo("서울특별시 마포구 월드컵로 1");
        assertThat(first.menu().value()).isEqualTo("냉면");

        RegistrationUnitBundle second = bundles.get(1);
        assertThat(second.unitIndex()).isEqualTo(2);
        assertThat(second.restaurantName().value()).isEqualTo("둘째 맛집");
        assertThat(second.address().value()).isEqualTo("서울특별시 영등포구 여의대로 10");
        assertThat(second.menu().value()).isEqualTo("냉면");
    }

    @Test
    @DisplayName("결속 대상 배열이 맛집명 후보 수보다 짧으면 초과 순번은 결측으로 남긴다")
    void decompose_결속배열이더짧으면_초과순번은결측으로남긴다() {
        JsonNode fields = json("""
                {
                  "restaurantName": [
                    {"value":"첫 맛집","confidence":0.95,"evidence":{"type":"TIMESTAMP","startMs":10,"endMs":20}},
                    {"value":"둘째 맛집","confidence":0.94,"evidence":{"type":"TIMESTAMP","startMs":30,"endMs":40}},
                    {"value":"셋째 맛집","confidence":0.90,"evidence":{"type":"TIMESTAMP","startMs":50,"endMs":60}}
                  ],
                  "address": [
                    {"value":"서울특별시 마포구 월드컵로 1","confidence":0.9,"evidence":{"type":"TIMESTAMP","startMs":11,"endMs":21}},
                    {"value":"서울특별시 영등포구 여의대로 10","confidence":0.88,"evidence":{"type":"TIMESTAMP","startMs":31,"endMs":41}}
                  ]
                }
                """);

        List<RegistrationUnitBundle> bundles = service.decompose(
                new DecomposeRegistrationUnitsCommand(fields, json("{}"), json("{}")));

        assertThat(bundles).hasSize(3);
        assertThat(bundles.get(0).address().value()).isEqualTo("서울특별시 마포구 월드컵로 1");
        assertThat(bundles.get(1).address().value()).isEqualTo("서울특별시 영등포구 여의대로 10");
        assertThat(bundles.get(2).address()).isNull();
    }

    private JsonNode json(String value) {
        return objectMapper.readTree(value);
    }
}
