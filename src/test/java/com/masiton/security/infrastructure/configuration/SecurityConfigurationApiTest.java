package com.masiton.security.infrastructure.configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Base64;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("관리자 API 보안 경계")
class SecurityConfigurationApiTest {

    private static final KeyPair KEY_PAIR = keyPair();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("masiton.security.jwt.key-id", () -> "test-key-20260727");
        registry.add("masiton.security.jwt.private-key-pem", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
        registry.add("masiton.security.jwt.public-key-pem", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
    }

    @Test
    @DisplayName("보호된 관리자 API의 미인증 요청은 traceId를 가진 401 계약을 반환한다")
    void 관리자API_미인증_401계약() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("ADMIN 권한 없는 인증 요청은 403 계약을 반환한다")
    void 관리자API_ADMIN권한없음_403계약() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("CREATOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").value(not(emptyString())));
    }

    @Test
    @DisplayName("로그인과 재발급 matcher는 포괄 관리자 matcher보다 먼저 허용한다")
    void 로그인재발급_matcher_401없이입력검증() throws Exception {
        mockMvc.perform(post("/api/admin/auth/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/auth/tokens/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("로그아웃 matcher는 JWT와 Refresh Cookie를 모두 요구한다")
    void 로그아웃_JWT없음_401() throws Exception {
        mockMvc.perform(delete("/api/admin/auth/tokens"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("공개 목록 matcher는 인증 없이 통과시킨다")
    void 공개목록_무인증_401이아님() throws Exception {
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("검증 키 목록에 없는 kid JWT는 거부한다")
    void jwt_알수없는Kid_거부한다() throws Exception {
        assertThatThrownBy(() -> jwtDecoder.decode(signedToken("retired-key")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("kid 없는 JWT는 거부한다")
    void jwt_Kid없음_거부한다() throws Exception {
        assertThatThrownBy(() -> jwtDecoder.decode(signedToken(null)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("issuer 또는 audience가 다른 JWT는 거부한다")
    void jwt_IssuerAudience불일치_거부한다() throws Exception {
        assertThatThrownBy(() -> jwtDecoder.decode(signedToken("test-key-20260727", "other-issuer", "masit-on-admin-api")))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtDecoder.decode(signedToken("test-key-20260727", "masit-on", "other-audience")))
                .isInstanceOf(JwtException.class);
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN %s-----\n%s\n-----END %s-----".formatted(
                type,
                Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded),
                type
        );
    }

    private static String signedToken(String keyId) throws Exception {
        return signedToken(keyId, "masit-on", "masit-on-admin-api");
    }

    private static String signedToken(String keyId, String issuer, String audience) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("admin-id")
                .issueTime(java.util.Date.from(now))
                .expirationTime(java.util.Date.from(now.plusSeconds(60)))
                .build();
        com.nimbusds.jose.JWSHeader.Builder header = new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.RS256);
        if (keyId != null) {
            header.keyID(keyId);
        }
        SignedJWT jwt = new SignedJWT(header.build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
        return jwt.serialize();
    }
}
