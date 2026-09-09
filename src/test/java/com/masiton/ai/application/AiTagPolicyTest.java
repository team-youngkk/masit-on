package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.port.out.AiExtractionResultStore;

import tools.jackson.databind.ObjectMapper;

@DisplayName("AI 태그 정책")
class AiTagPolicyTest {

    @Test
    @DisplayName("한글 라벨도 타입 접두어와 코드 일관성이 있으면 신규 태그 후보가 된다")
    void isNewTagCandidate_한글라벨과영문코드_신규태그후보로허용한다() {
        assertThat(AiTagPolicy.isNewTagCandidate("MENU", "김밥", "김밥", "MENU_KIMBAP")).isTrue();
    }

    @Test
    @DisplayName("원본 라벨과 표시 라벨이 다르면 신규 태그 후보가 되지 않는다")
    void isNewTagCandidate_원본과표시라벨불일치_신규태그후보로허용하지않는다() {
        assertThat(AiTagPolicy.isNewTagCandidate("MENU", "김밥집", "김밥", "MENU_KIMBAP")).isFalse();
    }

    @Test
    @DisplayName("끝 밑줄이나 연속 밑줄이 있는 코드는 신규 태그 후보가 되지 않는다")
    void isNewTagCandidate_잘못된밑줄코드_신규태그후보로허용하지않는다() {
        assertThat(AiTagPolicy.isNewTagCandidate("MENU", "김밥", "김밥", "MENU_KIMBAP_")).isFalse();
        assertThat(AiTagPolicy.isNewTagCandidate("MENU", "김밥", "김밥", "MENU__KIMBAP")).isFalse();
    }

    @Test
    @DisplayName("승인 라벨 비교는 NFKC와 Unicode 공백을 보존한 공통 정규화를 사용한다")
    void matchesApprovedLabel_NFKC와내부공백_공통정규화를사용한다() {
        ObjectMapper objectMapper = new ObjectMapper();
        var definition = new AiExtractionResultStore.TagDefinition(
                java.util.UUID.randomUUID(), "MENU_GALBI", "MENU", "ＡＢＣ 갈비", "[\"양념 갈비\"]", "ACTIVE");

        assertThat(AiTagPolicy.matchesApprovedLabel("ABC\u3000갈비", definition, objectMapper)).isTrue();
        assertThat(AiTagPolicy.matchesApprovedLabel("ABC갈비", definition, objectMapper)).isFalse();
        assertThat(AiTagPolicy.matchesApprovedLabel("양념\u00a0갈비", definition, objectMapper)).isTrue();
    }

    @Test
    @DisplayName("NFKC 결과가 용어 길이 상한을 넘으면 신규 태그 후보가 되지 않는다")
    void isNewTagCandidate_NFKC확장후길이초과_신규태그후보로허용하지않는다() {
        String label = "\ufdfa".repeat(100);

        assertThat(AiTagPolicy.isNewTagCandidate("MENU", label, label, "MENU_EXPANDED")).isFalse();
    }

    @Test
    @DisplayName("원문 라벨은 100자까지 허용하고 101자부터 신규 태그 후보에서 제외한다")
    void isNewTagCandidate_원문라벨100자와101자_저장열상한을지킨다() {
        String maximum = "가".repeat(100);
        String tooLongWithSameNormalizedTerm = maximum + " ";

        assertThat(AiTagPolicy.isNewTagCandidate("MENU", maximum, maximum, "MENU_MAXIMUM")).isTrue();
        assertThat(AiTagPolicy.isNewTagCandidate(
                "MENU", tooLongWithSameNormalizedTerm, maximum, "MENU_RAW_TOO_LONG")).isFalse();
        assertThat(AiTagPolicy.isNewTagCandidate(
                "MENU", maximum, tooLongWithSameNormalizedTerm, "MENU_LABEL_TOO_LONG")).isFalse();
    }
}
