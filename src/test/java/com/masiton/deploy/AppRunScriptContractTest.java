package com.masiton.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppRunScriptContractTest {

    @Test
    @DisplayName("Mobility gate 파라미터가 없어도 false로 백엔드 기동을 계속한다")
    void appRun_MobilityGate누락_false로계속기동한다() throws IOException {
        String script = Files.readString(Path.of("deploy/scripts/app-run.sh"), StandardCharsets.UTF_8);

        assertThat(script).contains("optional_bool_param()");
        assertThat(script).contains(
                "KAKAO_MOBILITY_ENABLED=$(optional_bool_param /masiton/integration/kakao-mobility/enabled)");
        assertThat(script).contains(
                "KAKAO_MOBILITY_FREE_TIER_VERIFIED=$(optional_bool_param /masiton/integration/kakao-mobility/free-tier-verified)");
        assertThat(script).contains("*) printf 'false' ;;");
        assertThat(script).contains("exec /usr/bin/docker run --name masiton-backend");
        assertThat(script).contains("-e SPRING_FLYWAY_TARGET");
    }
}
