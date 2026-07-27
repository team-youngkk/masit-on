package com.masiton.security.infrastructure.configuration;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class JwtKeyParser {

    private JwtKeyParser() {
    }

    static RSAPrivateKey privateKey(String pem) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(decode(pem, "PRIVATE KEY"))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT private key configuration", exception);
        }
    }

    static RSAPublicKey publicKey(String pem) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(decode(pem, "PUBLIC KEY"))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT public key configuration", exception);
        }
    }

    private static byte[] decode(String pem, String type) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("JWT %s must be configured".formatted(type.toLowerCase()));
        }
        String content = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN %s-----".formatted(type), "")
                .replace("-----END %s-----".formatted(type), "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(content);
    }
}
