package com.masiton.security.expansion2;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.test.FullContextIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TST-E2-SEC-001 (a): 회원/관리자 audience 교차 거부를 실제 2차 확장 컨트롤러 경로에서 검증한다.
 * NFR-SECURITY-006, docs/06-architecture/security-boundary.md 3절(회원 decoder만
 * /api/auth/**·/api/me/**에, 관리자 decoder만 /api/admin/**에 적용해 교차 audience를 인증 단계에서
 * 거부한다)의 실제 컨트롤러 경로 커버리지다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("2차 확장 컨트롤러의 회원/관리자 Audience 교차 거부")
class AudienceCrossRejectionApiTest extends FullContextIntegrationTest {

    private static final KeyPair KEY_PAIR = keyPair();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberTokenIssuer memberTokenIssuer;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("masiton.security.jwt.key-id", () -> "test-key-20260806");
        registry.add("masiton.security.jwt.private-key-pem", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
        registry.add("masiton.security.jwt.public-key-pem", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
    }

    @Test
    @DisplayName("회원 JWT는 관리자 큐레이션 관리 API를 401로 거부당한다")
    void 회원토큰_관리자큐레이션API_401거부() throws Exception {
        String memberToken = memberToken();

        mockMvc.perform(get("/api/admin/curations").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("회원 JWT는 관리자 제보 관리 API를 401로 거부당한다")
    void 회원토큰_관리자제보API_401거부() throws Exception {
        String memberToken = memberToken();

        mockMvc.perform(get("/api/admin/submissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("회원 JWT는 관리자 신고 관리 API를 401로 거부당한다")
    void 회원토큰_관리자신고API_401거부() throws Exception {
        String memberToken = memberToken();

        mockMvc.perform(get("/api/admin/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("관리자 JWT는 회원 컬렉션 API를 401로 거부당한다")
    void 관리자토큰_회원컬렉션API_401거부() throws Exception {
        String adminToken = signedToken("test-key-20260806", "masit-on", "masit-on-admin-api");

        mockMvc.perform(get("/api/me/collections").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("관리자 JWT는 회원 컬렉션 옵션 API를 401로 거부당한다")
    void 관리자토큰_회원컬렉션옵션API_401거부() throws Exception {
        String adminToken = signedToken("test-key-20260806", "masit-on", "masit-on-admin-api");

        mockMvc.perform(get("/api/me/collection-options")
                        .queryParam("restaurantId", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("관리자 JWT는 회원 알림 API를 401로 거부당한다")
    void 관리자토큰_회원알림API_401거부() throws Exception {
        String adminToken = signedToken("test-key-20260806", "masit-on", "masit-on-admin-api");

        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("관리자 JWT는 회원 제보 목록 API를 401로 거부당한다")
    void 관리자토큰_회원제보목록API_401거부() throws Exception {
        String adminToken = signedToken("test-key-20260806", "masit-on", "masit-on-admin-api");

        mockMvc.perform(get("/api/me/submissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("관리자 JWT는 회원 신고 목록 API를 401로 거부당한다")
    void 관리자토큰_회원신고목록API_401거부() throws Exception {
        String adminToken = signedToken("test-key-20260806", "masit-on", "masit-on-admin-api");

        mockMvc.perform(get("/api/me/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("공개 맛집 경로(map-points, popular)는 회원 토큰이 포함되어도 세션 조회를 거치지 않고 200 OK로 처리된다")
    void 공개맛집경로_회원토큰동반시_정상200응답() throws Exception {
        String token = memberToken();

        mockMvc.perform(get("/api/restaurants/map-points").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/restaurants/popular").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String memberToken() {
        return memberTokenIssuer.issueAccessToken(
                new MemberPrincipal(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
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
                type, Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded), type);
    }

    private static String signedToken(String keyId, String issuer, String audience) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("admin-id")
                .claim("roles", List.of("ADMIN"))
                .issueTime(java.util.Date.from(now))
                .expirationTime(java.util.Date.from(now.plusSeconds(60)))
                .build();
        com.nimbusds.jose.JWSHeader.Builder header = new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.RS256);
        header.keyID(keyId);
        SignedJWT jwt = new SignedJWT(header.build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
        return jwt.serialize();
    }
}
