package com.masiton.test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

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

    public static String courseRequestJson(UUID startId, UUID destinationId) {
        return "{\"restaurantIds\":[\"" + startId + "\",\"" + destinationId + "\"]}";
    }
}
