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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.test.FullContextIntegrationTest;
import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.application.port.out.MemberTokenIssuer;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("관리자 API 보안 경계")
class SecurityConfigurationApiTest extends FullContextIntegrationTest {

    private static final KeyPair KEY_PAIR = keyPair();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    @Qualifier("memberJwtDecoder")
    private JwtDecoder memberJwtDecoder;

    @Autowired
    private MemberTokenIssuer memberTokenIssuer;

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

    @Test
    @DisplayName("관리자와 회원 JWT audience는 서로의 보안 경계에서 거부된다")
    void jwt_관리자회원Audience교차거부() throws Exception {
        String adminToken = signedToken("test-key-20260727", "masit-on", "masit-on-admin-api");
        String memberToken = signedToken("test-key-20260727", "masit-on", "masit-on-member-api");

        assertThatThrownBy(() -> jwtDecoder.decode(memberToken))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> memberJwtDecoder.decode(adminToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("회원 Access Token은 같은 sid와 매 발급 다른 jti를 가진다")
    void memberAccessToken_sid유지_jti재발급() {
        MemberPrincipal principal = new MemberPrincipal("member-id", "e320b522-e80f-4659-8974-bbd591b72573");

        org.springframework.security.oauth2.jwt.Jwt first = memberJwtDecoder.decode(memberTokenIssuer.issueAccessToken(principal));
        org.springframework.security.oauth2.jwt.Jwt second = memberJwtDecoder.decode(memberTokenIssuer.issueAccessToken(principal));

        assertThat(first.getClaimAsString("sid")).isEqualTo(principal.sessionId());
        assertThat(second.getClaimAsString("sid")).isEqualTo(principal.sessionId());
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    @DisplayName("회원 경계는 교차 audience와 sid 없는 회원 JWT를 거부한다")
    void memberAdminApi_교차Audience와sid누락_401거부() throws Exception {
        String adminToken = signedToken("test-key-20260727", "masit-on", "masit-on-admin-api");
        String memberToken = signedToken("test-key-20260727", "masit-on", "masit-on-member-api");

        mockMvc.perform(get("/api/me/boundary").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/boundary").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/restaurants").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/tokens"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원 공개 인증 경로는 계약된 POST 메서드만 허용한다")
    void memberAuthenticationPublicRoutes_계약경로만허용() throws Exception {
        String[] publicPaths = {
                "/api/auth/registrations",
                "/api/auth/email-verifications",
                "/api/auth/email-verifications/resend",
                "/api/auth/password-resets/requests",
                "/api/auth/password-resets/confirmations",
                "/api/auth/tokens",
                "/api/auth/tokens/refresh"
        };

        for (String publicPath : publicPaths) {
            var request = post(publicPath);
            if (publicPath.endsWith("/refresh")) {
                request.header(HttpHeaders.ORIGIN, "http://localhost:3000");
            }
            mockMvc.perform(request)
                    .andExpect(publicPath.endsWith("/refresh") ? status().isUnauthorized() : status().isBadRequest());
        }
        mockMvc.perform(post("/api/auth/password-reset-requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원 JWT를 포함한 공개 조회는 회원 decoder로 인증하고 허용한다")
    void publicRead_memberJwt_허용() throws Exception {
        String memberToken = signedToken("test-key-20260727", "masit-on", "masit-on-member-api");

        mockMvc.perform(get("/api/restaurants").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk());
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
                .claim("roles", List.of("MEMBER"))
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
