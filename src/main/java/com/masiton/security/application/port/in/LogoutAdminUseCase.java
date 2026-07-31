package com.masiton.security.application.port.in;

public interface LogoutAdminUseCase {

    void logout(String adminId, String refreshToken);
}
