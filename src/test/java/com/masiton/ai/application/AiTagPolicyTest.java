package com.masiton.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
