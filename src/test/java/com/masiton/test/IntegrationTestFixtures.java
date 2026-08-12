package com.masiton.test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public final class IntegrationTestFixtures {

    private IntegrationTestFixtures() {
    }

    public static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    public static String courseRequestJson(UUID... restaurantIds) {
        String ids = Arrays.stream(restaurantIds)
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        return "{\"restaurantIds\":[" + ids + "]}";
    }
}
