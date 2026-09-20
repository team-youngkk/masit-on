package com.masiton.restaurant.application.port.out;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 자연어 검색이 사용하는 활성 태그 용어 snapshot을 제공한다. */
public interface ActiveTagDictionaryPort {

    ActiveTagDictionarySnapshot getActiveTagDictionary();

    record ActiveTagDictionarySnapshot(List<TagDefinition> definitions) {
        public ActiveTagDictionarySnapshot {
            definitions = definitions.stream()
                    .sorted(Comparator.comparing(TagDefinition::code))
                    .toList();
        }
    }

    record TagDefinition(String code, List<String> terms) {
        public TagDefinition {
            code = Objects.requireNonNull(code, "code");
            terms = terms.stream()
                    .map(term -> Objects.requireNonNull(term, "term"))
                    .sorted()
                    .toList();
        }
    }
}
