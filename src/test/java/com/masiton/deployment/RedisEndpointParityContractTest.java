package com.masiton.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Redis endpoint producer 계약")
class RedisEndpointParityContractTest {

    private static final List<Path> PRODUCERS = List.of(
            Path.of("deploy/scripts/app-run.sh"),
            Path.of("deploy/scripts/app-deploy.sh"),
            Path.of("deploy/scripts/health-metrics.sh"));
    private static final String CONTRACT_START = "# BEGIN SHARED REDIS ENDPOINT CONTRACT";
    private static final String CONTRACT_END = "# END SHARED REDIS ENDPOINT CONTRACT";

    @Test
    @DisplayName("앱 실행·배포 smoke·health 지표가 동일한 endpoint 검증 계약과 fallback 경계를 사용한다")
    void redisEndpoint_세producer가동일한검증계약과fallback경계를사용한다() throws IOException {
        List<String> scripts = PRODUCERS.stream()
                .map(this::read)
                .toList();

        String sharedContract = contract(scripts.get(0));
        for (int i = 1; i < scripts.size(); i++) {
            assertThat(contract(scripts.get(i)))
                    .as("Redis endpoint 계약이 app-run과 다르다: %s", PRODUCERS.get(i))
                    .isEqualTo(sharedContract);
        }

        for (int i = 0; i < scripts.size(); i++) {
            String script = scripts.get(i);
            assertThat(script)
                    .as(PRODUCERS.get(i).toString())
                    .contains("redis_ipv4_to_words")
                    .contains("redis_ipv6_to_words")
                    .contains("redis_ip_is_approved")
                    .contains("getent ahosts --no-addrconfig")
                    .contains("validate_shared_redis_endpoint \"$REDIS_HOST\" \"$REDIS_PORT\"")
                    .doesNotContain("is_canonical_ipv4")
                    .doesNotContain("is_safe_shared_ipv4")
                    .doesNotContain("getent ahostsv4")
                    .doesNotContain("REDISCLI_AUTH");
            assertSharedLookupIsScopedToSharedMode(script, PRODUCERS.get(i));
        }

        assertThat(scripts.get(0))
                .contains("REDIS_HOST=\"${REDIS_HOST:-127.0.0.1}\"")
                .contains("REDIS_PORT=\"${REDIS_PORT:-6379}\"")
                .contains("REDIS_HOST=\"$REDIS_VALIDATED_HOST\"");
        assertThat(scripts.get(1))
                .contains("REDIS_HOST=\"${REDIS_HOST:-127.0.0.1}\"")
                .contains("REDIS_PORT=\"${REDIS_PORT:-6379}\"")
                .contains("REDIS_HOST=\"$REDIS_VALIDATED_HOST\"")
                .contains("redis-cli --askpass")
                .contains("--mount \"type=bind,source=$REDIS_PASSWORD_FILE,target=/run/secrets/redis-password,readonly\"");
        assertThat(scripts.get(2))
                .contains("REDIS_HOST=127.0.0.1")
                .contains("REDIS_PORT=\"${REDIS_PORT:-6379}\"")
                .contains("validate_local_redis_endpoint \"$REDIS_PORT\"")
                .contains("REDIS_ENDPOINT_HOST=\"$REDIS_VALIDATED_HOST\"")
                .contains("REDIS_ENDPOINT_PORT=\"$REDIS_VALIDATED_PORT\"")
                .contains("redis-cli --askpass");
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Redis endpoint producer를 읽지 못했다: " + path, exception);
        }
    }

    private static String contract(String script) {
        int start = script.indexOf(CONTRACT_START);
        int end = script.indexOf(CONTRACT_END, start);
        assertThat(start).as("Redis endpoint 계약 시작 표식").isGreaterThanOrEqualTo(0);
        assertThat(end).as("Redis endpoint 계약 종료 표식").isGreaterThan(start);
        return script.substring(start, end + CONTRACT_END.length()).replace("\r\n", "\n");
    }

    private static void assertSharedLookupIsScopedToSharedMode(String script, Path producer) {
        int sharedMode = script.indexOf("if [ \"${REQUIRE_SHARED_REDIS:-false}\" = true ]; then");
        int hostLookup = script.indexOf("/masiton/redis/host");
        int portLookup = script.indexOf("/masiton/redis/port");
        int localFallback = script.indexOf("REDIS_HOST=127.0.0.1");
        if (localFallback < 0) {
            localFallback = script.indexOf("REDIS_HOST=\"${REDIS_HOST:-127.0.0.1}\"");
        }
        int localPortDefault = script.indexOf("REDIS_PORT=\"${REDIS_PORT:-6379}\"");

        assertThat(sharedMode).as("shared Redis 분기를 찾지 못했다: %s", producer).isGreaterThanOrEqualTo(0);
        assertThat(hostLookup).as("Redis host SSM 조회를 찾지 못했다: %s", producer).isGreaterThan(sharedMode);
        assertThat(portLookup).as("Redis port SSM 조회를 찾지 못했다: %s", producer).isGreaterThan(sharedMode);
        assertThat(localFallback).as("로컬 Redis fallback을 찾지 못했다: %s", producer).isGreaterThan(portLookup);
        assertThat(localPortDefault).as("로컬 Redis port 기본값을 찾지 못했다: %s", producer)
                .isGreaterThan(localFallback);
    }
}
