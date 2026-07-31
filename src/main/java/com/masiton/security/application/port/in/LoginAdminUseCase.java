package com.masiton.security.application.port.in;

import com.masiton.security.application.AuthenticationResult;

public interface LoginAdminUseCase {

    AuthenticationResult login(LoginCommand command);

    record LoginCommand(String loginId, String password, String source) {

        public LoginCommand {
            source = source == null || source.isBlank() ? "unknown" : source;
        }
    }
}
