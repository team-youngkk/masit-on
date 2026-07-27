package com.masiton.security.infrastructure.token;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.masiton.security.application.AdminPrincipal;
import com.masiton.security.application.port.out.TokenIssuer;
import com.masiton.security.infrastructure.configuration.SecurityProperties;

@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public JwtTokenIssuer(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    public String issueAccessToken(AdminPrincipal principal) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(principal.adminId())
                .audience(List.of(properties.getJwt().getAudience()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.getJwt().getAccessTokenTtl()))
                .claim("roles", principal.roles().stream().map(Enum::name).toList())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.getJwt().getKeyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
