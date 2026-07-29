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
import com.masiton.security.infrastructure.configuration.SecurityProperties;

@Component
public class JwtMemberTokenIssuer implements MemberTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public JwtMemberTokenIssuer(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    public String issueAccessToken(MemberPrincipal principal) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(principal.memberId())
                .audience(List.of(properties.getJwt().getMemberAudience()))
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.getJwt().getMemberAccessTokenTtl()))
                .claim("sid", principal.sessionId())
                .claim("roles", List.of("MEMBER"))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.getJwt().getKeyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
