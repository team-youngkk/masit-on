package com.masiton.member.application.port.out;

import com.masiton.member.application.MemberPrincipal;

public interface MemberTokenIssuer {

    String issueAccessToken(MemberPrincipal principal);
}
