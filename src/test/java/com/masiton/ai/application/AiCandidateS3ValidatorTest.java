package com.masiton.ai.application;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("S3 식당별 후보 계약")
class AiCandidateS3ValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiCandidateValidator validator = new AiCandidateValidator();

    @Test
    @DisplayName("두 식당과 다섯 메뉴의 명시적 결속을 허용한다")
    void 검증_두식당다섯메뉴_허용한다() throws IOException {
        JsonNode payload = fixture();
        assertThat(validator.validateS3(payload).isAutoConfirmable()).isTrue();
        assertThat(payload.at("/registrationUnits/0/menus").size()).isEqualTo(2);
        assertThat(payload.at("/registrationUnits/1/menus").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("중복 식별자와 추가 중첩 키는 구조 오류로 거부한다")
    void 검증_중복식별자와추가키_거부한다() throws IOException {
        JsonNode duplicate = fixture();
        ((ObjectNode) duplicate.at("/registrationUnits/1")).put("candidateUnitId", "a");
        assertThat(validator.validateS3(duplicate).isAutoRejected()).isTrue();
        JsonNode noName = fixture();
        ((ObjectNode) noName.at("/registrationUnits/0")).putNull("restaurantName");
        assertThat(validator.validateS3(noName).isAutoRejected()).isTrue();
        JsonNode wrongType = fixture();
        ((ObjectNode) wrongType.at("/registrationUnits/0/address/evidence")).put("startMs", "bad");
        assertThat(validator.validateS3(wrongType).isAutoRejected()).isTrue();
        JsonNode extra = fixture();
        ((ObjectNode) extra.at("/registrationUnits/0/menus/0")).put("candidateUnitId", "b");
        assertThat(validator.validateS3(extra).isAutoRejected()).isTrue();
    }

    @Test
    @DisplayName("한 식당 주소 결측은 다른 식당에서 채우지 않는다")
    void 검증_한단위주소결측_다른단위값을유지한다() throws IOException {
        JsonNode payload = fixture();
        ((ObjectNode) payload.at("/registrationUnits/0")).putNull("address");
        ((ObjectNode) payload).put("resultCompleteness", "PARTIAL").putArray("missingFields").add("address");
        assertThat(validator.validateS3(payload).isAutoRejected()).isFalse();
        assertThat(validator.validateS3(payload).missingFields()).contains("address");
        assertThat(payload.at("/registrationUnits/1/address/value").asText()).isEqualTo("b 주소");
    }

    @Test
    @DisplayName("새 S3 flat 응답과 301개 후보를 거부하고 300개는 절단 가능성을 표시한다")
    void 검증_flat응답과상한초과_거부한다() throws IOException {
        ObjectNode flat = (ObjectNode) fixture();
        flat.remove("registrationUnits");
        assertThat(validator.validateS3(flat).isAutoRejected()).isTrue();
        JsonNode payload = fixture();
        var menus = (tools.jackson.databind.node.ArrayNode) payload.at("/registrationUnits/0/menus");
        // Fixture has 13 candidates; add 287 to reach the unchanged 300 cap.
        for (int count = 0; count < 287; count++) menus.add(menus.get(0).deepCopy());
        assertThat(validator.validateS3(payload).candidateTruncated()).isTrue();
        menus.add(menus.get(0).deepCopy());
        assertThat(validator.validateS3(payload).isAutoRejected()).isTrue();
    }

    @Test
    @DisplayName("Provider COMPLETE라도 실제 결측과 불완전 근거는 보존하여 차단한다")
    void 검증_COMPLETE실제결측_차단과결측을보존한다() throws IOException {
        for (String field : new String[]{"address", "location", "visitEvidence"}) {
            JsonNode payload = fixture();
            ((ObjectNode) payload.at("/registrationUnits/0")).putNull(field);
            var result = validator.validateS3(payload);
            assertThat(result.isAutoRejected()).isFalse();
            assertThat(result.isAutoConfirmable()).isFalse();
            assertThat(result.missingFields()).contains(field);
        }
        JsonNode payload = fixture();
        ((ObjectNode) payload.at("/registrationUnits/0/address")).putObject("evidence").put("type", "TIMESTAMP");
        assertThat(validator.validateS3(payload).missingFields()).contains("address");
        ((ObjectNode) payload.at("/registrationUnits/0/address")).putObject("evidence").put("type", "TEXT_RANGE");
        assertThat(validator.validateS3(payload).isAutoRejected()).isFalse();
        assertThat(validator.validateS3(payload).missingFields()).contains("address");
        ((ObjectNode) payload.at("/registrationUnits/0/address")).putObject("evidence").put("type", "UNKNOWN");
        assertThat(validator.validateS3(payload).missingFields()).contains("address");
        ((ObjectNode) payload.at("/registrationUnits/0")).putArray("menus");
        assertThat(validator.validateS3(payload).missingFields()).contains("menu");
    }

    @Test
    @DisplayName("Provider PARTIAL 선언과 빈 등록 단위는 자동 확정하지 않는다")
    void 검증_PARTIAL선언과빈단위_차단한다() throws IOException {
        ObjectNode payload = (ObjectNode) fixture();
        payload.put("resultCompleteness", "PARTIAL").putArray("missingFields").add("address");
        assertThat(validator.validateS3(payload).isAutoConfirmable()).isFalse();
        payload.put("resultCompleteness", "COMPLETE").putArray("missingFields");
        payload.putArray("registrationUnits");
        assertThat(validator.validateS3(payload).isAutoConfirmable()).isFalse();
        assertThat(validator.validateS3(payload).missingFields()).isNotEmpty();
    }

    private JsonNode fixture() throws IOException {
        try (var input = getClass().getResourceAsStream("/ai/s3-two-restaurants.json")) {
            return mapper.readTree(input);
        }
    }
}
