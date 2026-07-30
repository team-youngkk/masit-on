package com.masiton.security.infrastructure.configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Base64;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.masiton.test.FullContextIntegrationTest;
import com.masiton.member.application.MemberAuthenticationStoreUnavailableException;
import com.masiton.member.application.MemberPrincipal;
import com.masiton.member.application.port.out.MemberTokenIssuer;
import com.masiton.member.infrastructure.persistence.JdbcMemberAuthenticationStateAdapter;
import com.masiton.orchestration.application.port.in.GetRestaurantDetailQuery;
import com.masiton.orchestration.application.query.ContentStatus;
import com.masiton.orchestration.application.query.RestaurantDetailResult;
import com.masiton.personalization.application.PersonalRestaurantService;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("관리자 API 보안 경계")
class SecurityConfigurationApiTest extends FullContextIntegrationTest {

    private static final KeyPair KEY_PAIR = keyPair();
    private static final UUID MEMBER_ID = UUID.fromString("7d865f1a-98f5-46f8-b6c8-658f67dcc07e");
    private static final UUID SESSION_ID = UUID.fromString("e320b522-e80f-4659-8974-bbd591b72573");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    @Qualifier("memberJwtDecoder")
    private JwtDecoder memberJwtDecoder;

    @Autowired
    private MemberTokenIssuer memberTokenIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PersonalRestaurantService personalRestaurantService;

    @MockitoBean
    private GetRestaurantDetailQuery getRestaurantDetailQuery;

    @MockitoSpyBean
    private JdbcMemberAuthenticationStateAdapter memberAuthenticationStateAdapter;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("masiton.security.jwt.key-id", () -> "test-key-20260727");
        registry.add("masiton.security.jwt.private-key-pem", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
        registry.add("masiton.security.jwt.public-key-pem", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE recent_restaurant_view, favorite, member_action_token, "
                + "member_session_revocation, member_account CASCADE");
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
    @DisplayName("회원 경계는 회원 JWT만 받고 관리자 경계는 관리자 JWT만 받는다")
    void memberAdminApi_교차Audience_401거부() throws Exception {
        String adminToken = signedToken("test-key-20260727", "masit-on", "masit-on-admin-api");
        String memberToken = memberToken(MEMBER_ID, UUID.randomUUID());
        insertActiveMember(MEMBER_ID);

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/restaurants").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/tokens"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("개인 맛집 API는 회원 JWT만 허용하고 관리자 JWT는 거부한다")
    void personalRestaurantApi_회원Jwt만허용() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        String adminToken = signedToken("test-key-20260727", "masit-on", "masit-on-admin-api");
        String memberToken = memberToken(MEMBER_ID, UUID.randomUUID());
        insertActiveMember(MEMBER_ID);
        when(personalRestaurantService.isFavorite(MEMBER_ID, restaurantId)).thenReturn(true);

        mockMvc.perform(get("/api/me/favorites/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));
        mockMvc.perform(get("/api/me/favorites/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
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
            mockMvc.perform(post(publicPath))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(post("/api/auth/password-reset-requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("선택 인증 대상이 아닌 공개 목록은 회원 JWT 오류를 익명으로 격하하지 않는다")
    void publicList_memberJwt_401거부() throws Exception {
        String memberToken = memberToken(MEMBER_ID, UUID.randomUUID());
        insertActiveMember(MEMBER_ID);

        mockMvc.perform(get("/api/restaurants").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/creators").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("공개 상세 조회의 유효한 회원 Bearer Token은 최근 기록을 남긴다")
    void publicDetail_memberJwt_최근기록저장() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String memberToken = memberToken(MEMBER_ID, sessionId);
        insertActiveMember(MEMBER_ID);
        when(getRestaurantDetailQuery.getRestaurantDetail(restaurantId)).thenReturn(detail(restaurantId));

        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk());

        verify(personalRestaurantService).record(
                org.mockito.ArgumentMatchers.eq(MEMBER_ID),
                org.mockito.ArgumentMatchers.eq(restaurantId), any());
    }

    @Test
    @DisplayName("sid 없는 회원 JWT는 보호 경계에서 401이고 공개 조회는 익명으로 격하한다")
    void memberJwt_sid없음_보호401_공개익명() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(getRestaurantDetailQuery.getRestaurantDetail(restaurantId)).thenReturn(detail(restaurantId));

        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberTokenWithoutSid(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(restaurantId.toString()));

        mockMvc.perform(get("/api/me/favorites/{restaurantId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberTokenWithoutSid(MEMBER_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));

        verify(personalRestaurantService, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("비활성 또는 폐기된 회원 세션은 401로 거부한다")
    void memberJwt_비활성또는폐기세션_401거부() throws Exception {
        UUID inactiveMemberId = UUID.randomUUID();
        UUID inactiveSessionId = UUID.randomUUID();
        insertMember(inactiveMemberId, "DISABLED");

        mockMvc.perform(get("/api/me/favorites/{restaurantId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken(inactiveMemberId, inactiveSessionId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        UUID revokedSessionId = UUID.randomUUID();
        insertActiveMember(MEMBER_ID);
        revokeSession(revokedSessionId, OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().plusMinutes(5));

        mockMvc.perform(get("/api/me/favorites/{restaurantId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken(MEMBER_ID, revokedSessionId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("공개 조회는 관리자 audience Bearer도 익명 요청으로만 처리한다")
    void publicRead_adminAudienceBearer_익명허용() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(getRestaurantDetailQuery.getRestaurantDetail(restaurantId)).thenReturn(detail(restaurantId));

        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + signedToken("test-key-20260727", "masit-on", "masit-on-admin-api")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(restaurantId.toString()));

        verify(personalRestaurantService, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("/api/me 저장소 확인 실패는 503과 private no-store 헤더를 반환한다")
    void memberBoundary_storeFailure_503과캐시헤더() throws Exception {
        insertActiveMember(MEMBER_ID);
        doThrow(new MemberAuthenticationStoreUnavailableException(
                new DataAccessResourceFailureException("db down")))
                .when(memberAuthenticationStateAdapter)
                .load(org.mockito.ArgumentMatchers.eq(MEMBER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID), any(Instant.class));

        mockMvc.perform(get("/api/me/favorites/{restaurantId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken(MEMBER_ID, SESSION_ID)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("공개 조회 저장소 확인 실패는 익명 요청으로 격하한다")
    void publicRead_storeFailure_익명허용() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        insertActiveMember(MEMBER_ID);
        when(getRestaurantDetailQuery.getRestaurantDetail(restaurantId)).thenReturn(detail(restaurantId));
        doThrow(new MemberAuthenticationStoreUnavailableException(
                new DataAccessResourceFailureException("db down")))
                .when(memberAuthenticationStateAdapter)
                .load(org.mockito.ArgumentMatchers.eq(MEMBER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID), any(Instant.class));

        mockMvc.perform(get("/api/restaurants/{restaurantId}", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken(MEMBER_ID, SESSION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(restaurantId.toString()));

        verify(personalRestaurantService, never()).record(any(), any(), any());
    }

    private RestaurantDetailResult detail(UUID restaurantId) {
        return new RestaurantDetailResult(
                restaurantId, "공개 맛집", "한식", "서울특별시 마포구 월드컵로 1",
                null, null, null, ContentStatus.AVAILABLE, List.of(), List.of());
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
        return signedToken(keyId, issuer, audience, MEMBER_ID.toString(), null, List.of("MEMBER"));
    }

    private static String memberToken(UUID memberId, UUID sessionId) throws Exception {
        return signedToken(
                "test-key-20260727",
                "masit-on",
                "masit-on-member-api",
                memberId.toString(),
                sessionId.toString(),
                List.of("MEMBER")
        );
    }

    private static String memberTokenWithoutSid(UUID memberId) throws Exception {
        return signedToken(
                "test-key-20260727",
                "masit-on",
                "masit-on-member-api",
                memberId.toString(),
                null,
                List.of("MEMBER")
        );
    }

    private static String signedToken(
            String keyId,
            String issuer,
            String audience,
            String subject,
            String sessionId,
            List<String> roles
    ) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(subject)
                .claim("roles", roles)
                .issueTime(java.util.Date.from(now))
                .expirationTime(java.util.Date.from(now.plusSeconds(60)));
        if (sessionId != null) {
            claims.claim("sid", sessionId);
        }
        com.nimbusds.jose.JWSHeader.Builder header = new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.RS256);
        if (keyId != null) {
            header.keyID(keyId);
        }
        SignedJWT jwt = new SignedJWT(header.build(), claims.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
        return jwt.serialize();
    }

    private void insertActiveMember(UUID memberId) {
        insertMember(memberId, "ACTIVE");
    }

    private void insertMember(UUID memberId, String status) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO member_account (
                    id, email, password_hash, email_verified_at, status, deletion_requested_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                memberId,
                memberId + "@example.com",
                "hashed-password",
                "ACTIVE".equals(status) || "DELETION_PENDING".equals(status) ? java.sql.Timestamp.from(now.toInstant()) : null,
                status,
                "DELETION_PENDING".equals(status) ? java.sql.Timestamp.from(now.toInstant()) : null,
                java.sql.Timestamp.from(now.toInstant()),
                java.sql.Timestamp.from(now.toInstant()));
    }

    private void revokeSession(UUID sessionId, OffsetDateTime revokedAt, OffsetDateTime expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO member_session_revocation (session_id, revoked_at, expires_at)
                VALUES (?, ?, ?)
                """,
                sessionId,
                java.sql.Timestamp.from(revokedAt.toInstant()),
                java.sql.Timestamp.from(expiresAt.toInstant()));
    }
}
