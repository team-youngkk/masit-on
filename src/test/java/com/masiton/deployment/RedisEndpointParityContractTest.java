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

    @Test
    @DisplayName("앱 실행·배포 smoke·health 지표가 IPv4 전용 numeric pinning 계약을 공유한다")
    void redisEndpoint_모든생산자가동일한IPv4전용계약을사용한다() throws IOException {
        for (Path producer : PRODUCERS) {
            String script = Files.readString(producer);

            assertThat(script)
                    .as(producer.toString())
                    .contains("is_canonical_ipv4")
                    .contains("is_safe_shared_ipv4")
                    .contains("getent ahostsv4")
                    .contains("validate_shared_redis_port")
                    .contains("validate_shared_redis_endpoint")
                    .contains("REDIS_VALIDATED_HOST")
                    .contains("REDIS_VALIDATED_PORT")
                    .doesNotContain("fc00")
                    .doesNotContain("fd00")
                    .doesNotContain("REDISCLI_AUTH");
        }

        String appRun = Files.readString(PRODUCERS.get(0));
        String appDeploy = Files.readString(PRODUCERS.get(1));
        String healthMetrics = Files.readString(PRODUCERS.get(2));

        assertThat(appRun).contains(
                "validate_shared_redis_endpoint \"$REDIS_HOST\" \"$REDIS_PORT\"",
                "REDIS_HOST=\"$REDIS_VALIDATED_HOST\"",
                "REDIS_PORT=\"$REDIS_VALIDATED_PORT\"");
        assertThat(appDeploy).contains(
                "validate_shared_redis_endpoint \"$REDIS_HOST\" \"$REDIS_PORT\"",
                "REDIS_HOST=\"$REDIS_VALIDATED_HOST\"",
                "REDIS_PORT=\"$REDIS_VALIDATED_PORT\"",
                "redis-cli --askpass",
                "--mount \"type=bind,source=$REDIS_PASSWORD_FILE,target=/run/secrets/redis-password,readonly\"");
        assertThat(healthMetrics).contains(
                "validate_shared_redis_endpoint \"$REDIS_HOST\" \"$REDIS_PORT\"",
                "REDIS_ENDPOINT_HOST=\"$REDIS_VALIDATED_HOST\"",
                "REDIS_ENDPOINT_PORT=\"$REDIS_VALIDATED_PORT\"",
                "redis-cli --askpass",
                "< /run/masiton-redis-password");
    }
}
