package com.masiton.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("health-metrics 셸 계약")
class HealthMetricsShellContractTest {

    private static final Path CONTRACT = Path.of("deploy/scripts/tests/health-metrics-memory-test.sh");
    private static final Path DOCKER_FALLBACK_CONTRACT =
            Path.of("deploy/scripts/tests/health-metrics-docker-fallback-test.sh");

    @Test
    @DisplayName("Redis 메모리 지표는 유효한 INFO에서만 올리고 결측에서도 의존성 지표를 유지한다")
    void redisMemory_유효한Info에서만용량지표를올리고_결측에서도의존성지표를유지한다()
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(bashCommand(), CONTRACT.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished).as("hermetic shell contract must be bounded").isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("health-metrics memory contract: PASS");
    }

    @Test
    @DisplayName("Docker fallback은 시크릿 파일 소유자로 컨테이너를 실행하고 평문을 넘기지 않는다")
    void redisDockerFallback_시크릿파일소유자로실행하고_평문을전달하지않는다()
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(bashCommand(), DOCKER_FALLBACK_CONTRACT.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished).as("hermetic Docker fallback contract must be bounded").isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("health-metrics Docker fallback contract: PASS");
    }

    private static String bashCommand() {
        Path gitBash = Path.of("C:/Program Files/Git/bin/bash.exe");
        return Files.isExecutable(gitBash) ? gitBash.toString() : "bash";
    }
}
