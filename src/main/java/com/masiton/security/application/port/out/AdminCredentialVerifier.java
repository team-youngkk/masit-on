package com.masiton.security.application.port.out;

import java.util.Optional;

import com.masiton.security.application.AdminPrincipal;

public interface AdminCredentialVerifier {

    boolean matches(String loginId, String password);

    Optional<AdminPrincipal> findActivePrincipal(String loginId);

    Optional<AdminPrincipal> findActivePrincipalById(String adminId);
}
