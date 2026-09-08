package com.masiton.restaurant.application.naturallanguage;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** 태그 정의 용어와 자연어 사전 별칭이 공유하는 정규화 계약이다. */
final class NaturalLanguageTermNormalizer {

    private static final Pattern UNICODE_WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");

    private NaturalLanguageTermNormalizer() {
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String compatible = Normalizer.normalize(value, Normalizer.Form.NFKC);
        String whitespaceNormalized = UNICODE_WHITESPACE.matcher(compatible).replaceAll(" ").trim();
        StringBuilder result = new StringBuilder(whitespaceNormalized.length());
        whitespaceNormalized.codePoints().forEach(codePoint ->
                result.appendCodePoint(codePoint >= 'A' && codePoint <= 'Z'
                        ? codePoint + ('a' - 'A')
                        : codePoint));
        return result.toString();
    }
}
