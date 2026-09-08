package com.masiton.restaurant.application.naturallanguage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.masiton.ai.application.TagTermNormalizer;

class NaturalLanguageTermNormalizerTest {

    @Test
    @DisplayName("자연어 태그 용어 정규화는 태그 정의 정규화 corpus와 같다")
    void 자연어와태그정의_정규화corpus가같다() {
        List<String> corpus = List.of(
                "ＡＢＣ\u00a0가족",
                " 가족\u3000외식 ",
                "A\tB\nC",
                "Straße",
                "한글　공백",
                "\ufdfa");

        assertThat(corpus)
                .allSatisfy(term -> assertThat(NaturalLanguageTermNormalizer.normalize(term))
                        .isEqualTo(TagTermNormalizer.normalize(term)));
        assertThat(NaturalLanguageTermNormalizer.normalize(null))
                .isEqualTo(TagTermNormalizer.normalize(null));
    }
}
