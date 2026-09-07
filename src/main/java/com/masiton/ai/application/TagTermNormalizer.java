package com.masiton.ai.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TagTermNormalizer {
    private static final Pattern UNICODE_WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");

    private TagTermNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String compatible = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return UNICODE_WHITESPACE.matcher(compatible).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }
}
