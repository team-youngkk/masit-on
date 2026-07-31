package com.masiton.security.application.port.in;

import com.masiton.security.application.AuthenticationResult;

public interface RefreshAdminTokenUseCase {

    AuthenticationResult refresh(String refreshToken);
}
