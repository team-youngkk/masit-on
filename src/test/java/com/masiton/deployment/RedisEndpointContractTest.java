package com.masiton.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("공유 Redis endpoint 배포 계약")
class RedisEndpointContractTest {

    private static final Path APP_RUN_SCRIPT = Path.of("deploy/scripts/app-run.sh");
    private static final Path APP_DEPLOY_SCRIPT = Path.of("deploy/scripts/app-deploy.sh");
    private static final String CONTRACT_START = "# BEGIN SHARED REDIS ENDPOINT CONTRACT";
    private static final String CONTRACT_END = "# END SHARED REDIS ENDPOINT CONTRACT";

    @Test
    @DisplayName("app-run과 deploy smoke가 동일한 endpoint 검증 계약과 fallback 경계를 사용한다")
    void appRun과DeploySmoke가_동일한RedisEndpoint계약을사용한다() throws IOException {
        String appRun = Files.readString(APP_RUN_SCRIPT);
        String appDeploy = Files.readString(APP_DEPLOY_SCRIPT);

        assertThat(contract(appRun)).isEqualTo(contract(appDeploy));
        assertThat(appRun)
                .contains("validate_shared_redis_endpoint \"$REDIS_HOST\" \"$REDIS_PORT\"")
                .contains("REDIS_HOST=\"$REDIS_VALIDATED_HOST\"")
                .contains("REDIS_HOST=\"${REDIS_HOST:-127.0.0.1}\"")
                .contains("REDIS_PORT=\"${REDIS_PORT:-6379}\"");
        assertThat(appDeploy)
                .contains("validate_shared_redis_endpoint \"$REDIS_HOST\" \"$REDIS_PORT\"")
                .contains("REDIS_HOST=\"$REDIS_VALIDATED_HOST\"")
                .contains("REDIS_HOST=\"${REDIS_HOST:-127.0.0.1}\"")
                .contains("REDIS_PORT=\"${REDIS_PORT:-6379}\"");
        assertThat(appDeploy)
                .contains("--mount \"type=bind,source=$REDIS_PASSWORD_FILE,target=/run/secrets/redis-password,readonly\"")
                .contains("--user \"$REDIS_PASSWORD_UID:$REDIS_PASSWORD_GID\"")
                .contains("redis-cli --askpass")
                .doesNotContain("REDISCLI_AUTH")
                .doesNotContain("redis_password");
        assertThat(appDeploy.lastIndexOf("validate_shared_redis_endpoint \"$REDIS_HOST\" \"$REDIS_PORT\""))
                .isLessThan(appDeploy.indexOf("REDIS_PASSWORD_FILE="))
                .isLessThan(appDeploy.indexOf("redis_cli() {"));
    }

    private static String contract(String script) {
        int start = script.indexOf(CONTRACT_START);
        int end = script.indexOf(CONTRACT_END, start);
        assertThat(start).as("Redis endpoint contract 시작 표식").isGreaterThanOrEqualTo(0);
        assertThat(end).as("Redis endpoint contract 종료 표식").isGreaterThan(start);
        return script.substring(start, end + CONTRACT_END.length());
    }
}
