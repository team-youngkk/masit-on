package com.masiton.security.expansion2;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

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

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TST-E2-SEC-001 (b): 회원 A의 실제 JWT로 회원 B의 컬렉션·제보·신고·알림 자원을 조회/변경할 때
 * 보안 Filter Chain 전체를 통과한 뒤 존재 은닉(404)됨을 검증한다. 기존 은닉 검증은 adapter/service
 * 계층이거나 standaloneSetup + mock UseCase로 Filter Chain을 지나지 않았다.
 *
 * docs/05-specs/api/common/identifier-contract.md 4절에 따라 식별자 형식 오류와 소유권/존재
 * 오류를 분리한다. 알림은 회원 본인 전용 자원 예외로 형식 오류도 404로 통일하지만, 컬렉션은 일반
 * 규칙(형식 오류 400 INVALID_IDENTIFIER)을 따른다. 이 차이는 실제 코드 동작이며 이 테스트는 그
 * 동작을 그대로 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("회원 본인 전용 자원의 Full-Stack 소유권 은닉")
class MemberOwnedResourceHidingFullStackTest extends FullContextIntegrationTest {

    private static final UUID REGION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final KeyPair KEY_PAIR = keyPair();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberTokenIssuer memberTokenIssuer;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("masiton.security.jwt.key-id", () -> "test-key-hiding-20260806");
        registry.add("masiton.security.jwt.private-key-pem", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
        registry.add("masiton.security.jwt.public-key-pem", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
    }

    @Test
    @DisplayName("다른 회원의 컬렉션 상세·이름 변경은 404로 숨기고 형식 오류는 400으로 분리한다")
    void 컬렉션_타회원조회와이름변경_404로숨긴다() throws Exception {
        UUID memberA = insertMember();
        UUID memberB = insertMember();
        String tokenA = memberToken(memberA);
        String tokenB = memberToken(memberB);

        String collectionId = createCollection(tokenA, "회원 A 컬렉션");

        mockMvc.perform(get("/api/me/collections/{id}", collectionId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COLLECTION_NOT_FOUND"));
        mockMvc.perform(patch("/api/me/collections/{id}", collectionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"탈취 시도\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COLLECTION_NOT_FOUND"));
        mockMvc.perform(get("/api/me/collections/{id}", "not-a-uuid").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"));

        mockMvc.perform(get("/api/me/collections/{id}", collectionId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("회원 A 컬렉션"));
    }

    @Test
    @DisplayName("다른 회원의 컬렉션 삭제·맛집 추가는 소유 자원에 부작용 없이 멱등하게 끝난다")
    void 컬렉션_타회원삭제와맛집추가_부작용없이끝난다() throws Exception {
        UUID memberA = insertMember();
        UUID memberB = insertMember();
        String tokenA = memberToken(memberA);
        String tokenB = memberToken(memberB);
        UUID restaurantId = insertRestaurant();
        String collectionId = createCollection(tokenA, "삭제 대상 컬렉션");

        mockMvc.perform(delete("/api/me/collections/{id}", collectionId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/me/collections/{id}/restaurants/{restaurantId}", collectionId, restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COLLECTION_NOT_FOUND"));

        // 회원 B의 DELETE는 실제로 소유하지 않은 컬렉션에 아무 영향을 주지 않아야 한다.
        mockMvc.perform(get("/api/me/collections/{id}", collectionId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("삭제 대상 컬렉션"));
    }

    @Test
    @DisplayName("다른 회원의 제보 상세는 404로 숨기고 형식 오류도 같은 코드로 통일한다")
    void 제보_타회원조회와형식오류_404로통일한다() throws Exception {
        UUID memberA = insertMember();
        UUID memberB = insertMember();
        String tokenA = memberToken(memberA);
        String tokenB = memberToken(memberB);

        String requestId = createSubmission(tokenA);

        mockMvc.perform(get("/api/me/submissions/{id}", requestId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));
        mockMvc.perform(get("/api/me/submissions/{id}", "not-a-uuid").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));

        mockMvc.perform(get("/api/me/submissions/{id}", requestId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    @DisplayName("다른 회원의 신고 상세는 404로 숨기고 형식 오류도 같은 코드로 통일한다")
    void 신고_타회원조회와형식오류_404로통일한다() throws Exception {
        UUID memberA = insertMember();
        UUID memberB = insertMember();
        String tokenA = memberToken(memberA);
        String tokenB = memberToken(memberB);
        UUID restaurantId = insertRestaurant();

        String requestId = createReport(tokenA, restaurantId);

        mockMvc.perform(get("/api/me/reports/{id}", requestId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        mockMvc.perform(get("/api/me/reports/{id}", "not-a-uuid").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));

        mockMvc.perform(get("/api/me/reports/{id}", requestId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    @DisplayName("다른 회원의 알림 읽음 처리는 404로 숨기고 형식 오류도 같은 코드로 통일한다")
    void 알림_타회원읽음처리와형식오류_404로통일한다() throws Exception {
        UUID memberA = insertMember();
        UUID memberB = insertMember();
        UUID adminId = insertAdmin();
        String tokenA = memberToken(memberA);
        String tokenB = memberToken(memberB);

        String requestId = createSubmission(tokenA);
        mockMvc.perform(put("/api/admin/submissions/{id}/status", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\",\"internalNote\":\"검토 시작\"}"))
                .andExpect(status().isOk());
        String notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notification WHERE submission_id = ? AND member_id = ?",
                String.class, UUID.fromString(requestId), memberA);

        mockMvc.perform(put("/api/me/notifications/{id}/read", notificationId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(put("/api/me/notifications/{id}/read", "not-a-uuid").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        mockMvc.perform(put("/api/me/notifications/{id}/read", notificationId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    private String adminToken(UUID adminId) throws Exception {
        java.time.Instant now = java.time.Instant.now();
        com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                .issuer("masit-on")
                .audience(java.util.List.of("masit-on-api"))
                .subject(adminId.toString())
                .claim("roles", java.util.List.of("ADMIN"))
                .issueTime(java.util.Date.from(now))
                .expirationTime(java.util.Date.from(now.plusSeconds(60)))
                .build();
        com.nimbusds.jose.JWSHeader.Builder header =
                new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256);
        header.keyID("test-key-hiding-20260806");
        com.nimbusds.jwt.SignedJWT jwt = new com.nimbusds.jwt.SignedJWT(header.build(), claims);
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner((java.security.interfaces.RSAPrivateKey) KEY_PAIR.getPrivate()));
        return jwt.serialize();
    }

    private String createCollection(String token, String name) throws Exception {
        String body = "{\"name\":\"" + name + "\"}";
        String response = mockMvc.perform(post("/api/me/collections")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "collection-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("collectionId").asText();
    }

    private String createSubmission(String token) throws Exception {
        String response = mockMvc.perform(post("/api/me/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "submission-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType":"RESTAURANT",
                                  "candidate":{"name":"새 맛집 %s","roadAddress":"서울특별시 테스트로 1"},
                                  "description":"새로운 맛집 등록을 제안하는 은닉 검증용 제보입니다.",
                                  "evidenceUrl":"https://example.com/evidence"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("requestId").asText();
    }

    private String createReport(String token, UUID restaurantId) throws Exception {
        String response = mockMvc.perform(post("/api/me/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Idempotency-Key", "report-key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType":"RESTAURANT",
                                  "targetId":"%s",
                                  "reportType":"ERROR",
                                  "description":"은닉 검증을 위한 신고 접수 본문입니다."
                                }
                                """.formatted(restaurantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("requestId").asText();
    }

    private String memberToken(UUID memberId) {
        return memberTokenIssuer.issueAccessToken(new MemberPrincipal(memberId.toString(), UUID.randomUUID().toString()));
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
                VALUES (?, ?, ?, '은닉 검증 맛집', ?, ?, '서울특별시 테스트로 1',
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
