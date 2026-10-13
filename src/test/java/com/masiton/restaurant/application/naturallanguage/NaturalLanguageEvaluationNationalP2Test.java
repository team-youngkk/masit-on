package com.masiton.restaurant.application.naturallanguage;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("전국 지역 P2 보충 Golden 평가")
class NaturalLanguageEvaluationNationalP2Test {

    private static final String PATH = "/eval/nlsearch-national-p2-v1.0.0/";

    @Test
    @DisplayName("전국 보충 자산의 모든 필수 조건·상태·미해결 기대값을 만족한다")
    void evaluate_전국보충Golden_필수사례위반이없다() throws Exception {
        // given
        JsonNode manifest = read("manifest.json");
        JsonNode cases = read("cases.json");
        assertThat(manifest.get("parserVersion").asText()).isEqualTo("P2");
        assertThat(cases.size()).isEqualTo(manifest.get("caseCount").asInt());
        NaturalLanguageRestaurantParser parser = new NaturalLanguageRestaurantParser(
                NaturalLanguageDictionary.national(Map.of(), Map.of("MENU_GUKBAP", List.of("국밥"))),
                new NaturalLanguageRegionResolver(NationalRegionTestFixture.regions()));
        Set<String> ids = new HashSet<>();
        // when & then
        for (JsonNode testCase : cases) {
            String id = testCase.get("id").asText();
            assertThat(ids.add(id)).as(id).isTrue();
            var actual = parser.parse(testCase.get("sentence").asText());
            assertThat(actual.status().name()).as(id).isEqualTo(testCase.get("status").asText());
            assertThat(actual.appliedConditions().regionCode()).as(id).isEqualTo(text(testCase, "regionCode"));
            assertThat(actual.appliedConditions().query()).as(id).isEqualTo(text(testCase, "query"));
            assertThat(actual.appliedConditions().category()).as(id).isEqualTo(text(testCase, "category"));
            assertThat(actual.appliedConditions().district()).as(id).isNull();
            List<String> tags = testCase.has("tags") ? new ObjectMapper().convertValue(testCase.get("tags"),
                    new tools.jackson.core.type.TypeReference<List<String>>() { }) : List.of();
            assertThat(actual.appliedConditions().tags()).as(id).isEqualTo(tags);
            assertThat(actual.interpretation().unresolvedFields().contains(ConditionField.REGION_CODE)).as(id)
                    .isEqualTo(testCase.has("unresolvedRegion") && testCase.get("unresolvedRegion").asBoolean());
            assertThat(actual.interpretation().parserVersion()).as(id).isEqualTo("P2");
        }
    }

    private JsonNode read(String filename) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(PATH + filename)) {
            assertThat(stream).isNotNull();
            return new ObjectMapper().readTree(stream);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }
}
