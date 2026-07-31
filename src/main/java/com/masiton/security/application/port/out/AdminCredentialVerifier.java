package com.masiton.security.application.port.out;

import java.util.Optional;

import com.masiton.security.application.AdminPrincipal;

public interface AdminCredentialVerifier {

    Optional<AdminPrincipal> authenticate(String loginId, String password);

    Optional<AdminPrincipal> findActivePrincipalById(String adminId);
}
