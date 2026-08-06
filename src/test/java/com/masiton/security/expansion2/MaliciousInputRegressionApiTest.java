package com.masiton.security.expansion2;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.test.FullContextIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TST-E2-SEC-001 (c): 악성 입력·URL 형식 회귀.
 *
 * 입력 정책은 {@code <}·{@code >} 자체와 제어 문자를 거부하는 허용목록 방식이어야 한다
 * (docs/08-planning/second-expansion-test-matrix.md 3절, NFR-SECURITY-006).
 *
 * 제보·신고의 description/evidenceUrl은 {@code ParticipationService.safeText}·
 * {@code validateHttps}가 이미 이 정책을 구현하므로 아래 테스트는 통과해야 한다.
 *
 * <p>개인 컬렉션 이름과 큐레이션 제목·설명은 {@link SafeTextPolicy}가 적용되어
 * {@code <}·{@code >}·ISO 제어 문자를 {@code INVALID_FIELD_VALUE}로 정상 거부한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("2차 확장 입력의 악성 입력·URL 형식 회귀")
class MaliciousInputRegressionApiTest extends FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final KeyPair KEY_PAIR = keyPair();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberTokenIssuer memberTokenIssuer;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("masiton.security.jwt.key-id", () -> "test-key-malicious-20260806");
        registry.add("masiton.security.jwt.private-key-pem", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
        registry.add("masiton.security.jwt.public-key-pem", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
    }

    @Test
    @DisplayName("정상 컬렉션 이름은 생성되고 스크립트 태그가 섞인 이름은 거부해야 한다")
    void 컬렉션이름_정상과스크립트태그_정상은통과하고스크립트는거부해야한다() throws Exception {
        UUID memberId = insertMember();
        String token = memberToken(memberId);

        mockMvc.perform(post("/api/me/collections")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "collection-normal-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"주말 맛집 모음\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/me/collections")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "collection-xss-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<img src=x onerror=alert(1)>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("제어 문자가 섞인 컬렉션 이름은 거부해야 한다")
    void 컬렉션이름_제어문자_거부해야한다() throws Exception {
        UUID memberId = insertMember();
        String token = memberToken(memberId);

        mockMvc.perform(post("/api/me/collections")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "collection-control-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"제어문자\\u0007포함\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("정상 큐레이션은 생성되고 스크립트 태그가 섞인 제목·설명은 거부해야 한다")
    void 큐레이션_정상과스크립트태그_정상은통과하고스크립트는거부해야한다() throws Exception {
        UUID adminId = insertAdmin();
        String token = adminToken(adminId);

        mockMvc.perform(post("/api/admin/curations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "curation-normal-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"여름 맛집 큐레이션\",\"description\":\"정상 설명입니다\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/curations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "curation-xss-title-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"<script>alert(1)</script>\",\"description\":\"정상 설명입니다\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));

        mockMvc.perform(post("/api/admin/curations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "curation-xss-desc-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"정상 제목\",\"description\":\"<svg onload=alert(1)>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("제보 설명에 실행성 태그가 있으면 거부한다")
    void 제보설명_이미지태그onerror_거부한다() throws Exception {
        UUID memberId = insertMember();
        String token = memberToken(memberId);

        mockMvc.perform(post("/api/me/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "submission-xss-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType":"RESTAURANT",
                                  "candidate":{"name":"새 맛집","roadAddress":"서울특별시 테스트로 1"},
                                  "description":"<img src=x onerror=alert(1)> 새 맛집을 제보합니다.",
                                  "evidenceUrl":"https://example.com/evidence"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    @DisplayName("제보 후보 필드에 실행성 태그가 있으면 거부한다")
    void 제보후보필드_스크립트태그_거부한다() throws Exception {
        UUID memberId = insertMember();
        String token = memberToken(memberId);

        mockMvc.perform(post("/api/me/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "submission-candidate-xss-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType":"RESTAURANT",
                                  "candidate":{"name":"<svg onload=alert(1)>","roadAddress":"서울특별시 테스트로 1"},
                                  "description":"악성 후보 필드 거부를 확인하는 제보입니다.",
                                  "evidenceUrl":"https://example.com/evidence"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"));
    }

    @Test
    @DisplayName("제보 근거 URL이 data URI이면 거부한다")
    void 제보근거URL_dataURI_거부한다() throws Exception {
        UUID memberId = insertMember();
        String token = memberToken(memberId);

        mockMvc.perform(post("/api/me/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "submission-data-uri-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType":"RESTAURANT",
                                  "candidate":{"name":"새 맛집","roadAddress":"서울특별시 테스트로 1"},
                                  "description":"data URI 근거 거부를 확인하는 제보입니다.",
                                  "evidenceUrl":"data:text/html,<script>alert(1)</script>"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("evidenceUrl"));
    }

    @Test
    @DisplayName("신고 근거 URL에 사용자 정보가 포함되면 거부한다")
    void 신고근거URL_사용자정보포함_거부한다() throws Exception {
        UUID memberId = insertMember();
        UUID restaurantId = insertRestaurant();
        String token = memberToken(memberId);

        mockMvc.perform(post("/api/me/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "report-userinfo-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType":"RESTAURANT",
                                  "targetId":"%s",
                                  "reportType":"ERROR",
                                  "description":"사용자 정보가 포함된 URL 거부를 확인하는 신고입니다.",
                                  "evidenceUrl":"https://user:pass@evil.example.com/evidence"
                                }
                                """.formatted(restaurantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("evidenceUrl"));
    }

    @Test
    @DisplayName("신고 설명이 svg onload 페이로드를 포함하면 거부한다")
    void 신고설명_svgOnload_거부한다() throws Exception {
        UUID memberId = insertMember();
        UUID restaurantId = insertRestaurant();
        String token = memberToken(memberId);

        mockMvc.perform(post("/api/me/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "report-svg-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType":"RESTAURANT",
                                  "targetId":"%s",
                                  "reportType":"ERROR",
                                  "description":"<svg onload=alert(1)> 폐업한 것으로 보입니다."
                                }
                                """.formatted(restaurantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    @DisplayName("신고 설명은 10자 미만을 거부하고 10자 이상은 허용한다")
    void 신고설명_최소길이경계_9자거부10자허용() throws Exception {
        UUID shortMember = insertMember();
        UUID longMember = insertMember();
        UUID shortRestaurant = insertRestaurant();
        UUID longRestaurant = insertRestaurant();

        mockMvc.perform(post("/api/me/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken(shortMember))
                        .header("Idempotency-Key", "report-min-under-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody(shortRestaurant, "a".repeat(9))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("description"));

        mockMvc.perform(post("/api/me/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken(longMember))
                        .header("Idempotency-Key", "report-min-at-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody(longRestaurant, "a".repeat(10))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("신고 설명은 2000자를 초과하면 거부하고 2000자는 허용한다")
    void 신고설명_최대길이경계_2000자허용2001자거부() throws Exception {
        UUID atLimitMember = insertMember();
        UUID overLimitMember = insertMember();
        UUID atLimitRestaurant = insertRestaurant();
        UUID overLimitRestaurant = insertRestaurant();

        mockMvc.perform(post("/api/me/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken(atLimitMember))
                        .header("Idempotency-Key", "report-max-at-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody(atLimitRestaurant, "a".repeat(2000))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/me/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken(overLimitMember))
                        .header("Idempotency-Key", "report-max-over-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody(overLimitRestaurant, "a".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    private String reportBody(UUID restaurantId, String description) {
        return """
                {
                  "targetType":"RESTAURANT",
                  "targetId":"%s",
                  "reportType":"ERROR",
                  "description":"%s"
                }
                """.formatted(restaurantId, description);
    }

    private String memberToken(UUID memberId) {
        return memberTokenIssuer.issueAccessToken(new MemberPrincipal(memberId.toString(), UUID.randomUUID().toString()));
    }

    private String adminToken(UUID adminId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("masit-on")
                .audience(List.of("masit-on-admin-api"))
                .subject(adminId.toString())
                .claim("roles", List.of("ADMIN"))
                .issueTime(java.util.Date.from(now))
                .expirationTime(java.util.Date.from(now.plusSeconds(60)))
                .build();
        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.RS256);
        header.keyID("test-key-malicious-20260806");
        SignedJWT jwt = new SignedJWT(header.build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
        return jwt.serialize();
    }

    private UUID insertMember() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO member_account (id, email, password_hash, email_verified_at, status)
                VALUES (?, ?, 'password-hash', CURRENT_TIMESTAMP, 'ACTIVE')
                """, id, id + "@example.com");
        return id;
    }

    private UUID insertAdmin() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO admin_account (id, login_id, password_hash) VALUES (?, ?, 'hash')",
                id, "admin-" + id);
        return id;
    }

    private UUID insertRestaurant() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO restaurant
                    (id, region_id, food_category_id, name, kakao_place_id, kakao_place_url,
                     road_address, phone_number, publication_status, lifecycle_status)
                VALUES (?, ?, ?, '악성입력 검증 맛집', ?, ?, '서울특별시 테스트로 1',
                        '02-1234-5678', 'PUBLIC', 'ACTIVE')
                """, id, REGION_ID, CATEGORY_ID, "KAKAO-" + id, "https://example.com/place/" + id);
        return id;
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
}
