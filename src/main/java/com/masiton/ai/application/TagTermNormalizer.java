package com.masiton.ai.application;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class TagTermNormalizer {
    public static final int MAX_RAW_LENGTH = 100;
    public static final int MAX_NORMALIZED_LENGTH = 200;
    private static final Pattern UNICODE_WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");

    private TagTermNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String compatible = Normalizer.normalize(value, Normalizer.Form.NFKC);
        String whitespaceNormalized = UNICODE_WHITESPACE.matcher(compatible).replaceAll(" ").trim();
        StringBuilder result = new StringBuilder(whitespaceNormalized.length());
        whitespaceNormalized.codePoints().forEach(codePoint ->
                result.appendCodePoint(codePoint >= 'A' && codePoint <= 'Z' ? codePoint + ('a' - 'A') : codePoint));
        return result.toString();
    }
}
