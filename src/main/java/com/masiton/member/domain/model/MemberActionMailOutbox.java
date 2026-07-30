package com.masiton.member.domain.model;

import java.util.Arrays;
import java.util.UUID;

public record MemberActionMailOutbox(
        UUID id,
        UUID memberActionTokenId,
        MemberActionPurpose purpose,
        byte[] encryptedToken,
        byte[] encryptionNonce,
        String encryptionKeyId
) {

    public MemberActionMailOutbox {
        encryptedToken = Arrays.copyOf(encryptedToken, encryptedToken.length);
        encryptionNonce = Arrays.copyOf(encryptionNonce, encryptionNonce.length);
    }

    @Override
    public byte[] encryptedToken() {
        return Arrays.copyOf(encryptedToken, encryptedToken.length);
    }

    @Override
    public byte[] encryptionNonce() {
        return Arrays.copyOf(encryptionNonce, encryptionNonce.length);
    }
}
