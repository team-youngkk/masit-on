package com.masiton.security.application.port.out;

import com.masiton.security.application.AdminPrincipal;

public interface TokenIssuer {

    String issueAccessToken(AdminPrincipal principal);
}
