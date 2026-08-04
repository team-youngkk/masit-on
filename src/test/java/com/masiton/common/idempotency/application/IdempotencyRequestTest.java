package com.masiton.common.idempotency.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("멱등 요청")
class IdempotencyRequestTest {

    private static final byte[] REQUEST_HASH = new byte[32];

    @Test
    @DisplayName("원문 키는 보관하지 않고 UTF-8 SHA-256 해시만 만든다")
    void 키생성_유효한원문_SHA256해시만보관한다() throws Exception {
        // given
        String rawKey = "opaque-key-가나다";

        // when
        IdempotencyRequest request = request(rawKey);

        // then
        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest(rawKey.getBytes(StandardCharsets.UTF_8));
        assertThat(request.keyHash()).containsExactly(expected);
        assertThat(IdempotencyRequest.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("rawKey"));
    }

    @Test
    @DisplayName("키 길이는 유니코드 문자 기준 8자와 128자를 허용한다")
    void 키검증_경계길이_허용한다() {
        // given
        String eightCharacters = "😀😀😀😀😀😀😀😀";
        String maximumCharacters = "a".repeat(128);

        // when & then
        assertThat(request(eightCharacters).keyHash()).hasSize(32);
        assertThat(request(maximumCharacters).keyHash()).hasSize(32);
    }

    @Test
    @DisplayName("키가 없거나 8자 미만 또는 128자 초과면 거부한다")
    void 키검증_누락또는범위밖_거부한다() {
        // given & when & then
        assertThatThrownBy(() -> request(null))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> request("1234567"))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> request("a".repeat(129)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    @DisplayName("잘못된 키는 공통 오류 계약의 400 코드로 표현한다")
    void 키검증_형식오류_공통오류계약을사용한다() {
        // given & when & then
        assertThatThrownBy(() -> request("short"))
                .isInstanceOfSatisfying(InvalidIdempotencyKeyException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("INVALID_IDEMPOTENCY_KEY");
                });
    }

    @Test
    @DisplayName("요청 본문 해시는 정확히 32바이트여야 한다")
    void 요청해시검증_32바이트아님_거부한다() {
        // given & when & then
        assertThatThrownBy(() -> IdempotencyRequest.of(
                IdempotencyActorType.MEMBER,
                UUID.randomUUID(),
                IdempotencyApiScope.MEMBER_COLLECTIONS,
                "valid-key",
                new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private IdempotencyRequest request(String rawKey) {
        return IdempotencyRequest.of(
                IdempotencyActorType.MEMBER,
                UUID.randomUUID(),
                IdempotencyApiScope.MEMBER_COLLECTIONS,
                rawKey,
                REQUEST_HASH);
    }
}
