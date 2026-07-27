package com.masiton.security.application.port.out;

import java.time.Duration;
import com.masiton.security.application.RefreshTokenRotation;

public interface RefreshTokenStore {

    RefreshTokenRotation issue(String adminId, Duration ttl);

    RefreshTokenRotation rotate(String refreshToken, Duration ttl);

    boolean matches(String adminId, String refreshToken);

    void revoke(String adminId);
}
