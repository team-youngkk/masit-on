package com.masiton.member.infrastructure.token;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.common.security.MemberJwtSettings;

@Component
public class JwtMemberTokenIssuer implements MemberTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final MemberJwtSettings settings;

    public JwtMemberTokenIssuer(JwtEncoder jwtEncoder, MemberJwtSettings settings) {
        this.jwtEncoder = jwtEncoder;
        this.settings = settings;
    }

    @Override
    public String issueAccessToken(MemberPrincipal principal) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(principal.memberId())
                .audience(List.of(settings.audience()))
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(settings.accessTokenTtl()))
                .claim("sid", principal.sessionId())
                .claim("roles", List.of(principal.role().name()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(settings.keyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
