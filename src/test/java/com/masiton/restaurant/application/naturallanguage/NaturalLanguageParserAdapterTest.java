package com.masiton.restaurant.application.naturallanguage;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.masiton.common.web.BusinessException;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort.ActiveTagDictionarySnapshot;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort.TagDefinition;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("자연어 parser 동적 태그 사전 연결")
class NaturalLanguageParserAdapterTest {

    @Test
    @DisplayName("운영 연결은 활성 snapshot의 태그만 사용하고 seed로 대체하지 않는다")
    void parse_활성Snapshot_동적태그만해석한다() {
        // given
        ActiveTagDictionaryPort dictionaryPort = () -> new ActiveTagDictionarySnapshot(List.of(
                new TagDefinition("TASTE_SMOKY", List.of("불향"))));
        NaturalLanguageParserAdapter adapter = new NaturalLanguageParserAdapter(List::of, dictionaryPort);

        // when
        var dynamic = adapter.parse("불향 맛집");
        var seedOnly = adapter.parse("냉면 맛집");

        // then
        assertThat(dynamic.appliedConditions().tags()).containsExactly("TASTE_SMOKY");
        assertThat(seedOnly.appliedConditions().tags()).isEmpty();
    }

    @Test
    @DisplayName("태그 사전 DB 실패는 NATURAL_LANGUAGE_UNAVAILABLE 503으로 변환한다")
    void parse_태그사전실패_503BusinessException을던진다() {
        // given
        ActiveTagDictionaryPort dictionaryPort = () -> {
            throw new ActiveTagDictionaryUnavailableException(new IllegalStateException("database unavailable"));
        };
        NaturalLanguageParserAdapter adapter = new NaturalLanguageParserAdapter(List::of, dictionaryPort);

        // when & then
        assertThatThrownBy(() -> adapter.parse("성수 맛집"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo("NATURAL_LANGUAGE_UNAVAILABLE");
                });
    }
}
