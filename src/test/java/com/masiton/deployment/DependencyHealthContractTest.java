package com.masiton.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 dependency health 진단 계약")
class DependencyHealthContractTest {

    private static final Path APP_DEPLOY = Path.of("deploy/scripts/app-deploy.sh");
    private static final List<String> EXPECTED_COMPONENTS = List.of("db", "mail", "redis");

    @Test
    @DisplayName("HTTP·JSON·구성요소 실패를 서로 다른 운영 메시지로 구분한다")
    void appDeploy_dependencyHealth실패유형을_구분한다() throws IOException {
        String script = Files.readString(APP_DEPLOY);

        assertThat(script)
                .contains("백엔드 dependency health HTTP 요청 실패: HTTP")
                .contains("백엔드 dependency health HTTP 실패: HTTP")
                .contains("백엔드 dependency health JSON 파싱 실패: HTTP")
                .contains("백엔드 dependency health 구성요소 실패:")
                .doesNotContain("백엔드 mail dependency 확인 실패")
                .doesNotContain("echo \"$dependencies_body\"")
                .doesNotContain("cat \"$dependencies_body\"");
    }

    @Test
    @DisplayName("mail·db·redis 단일 실패와 복수 실패의 실제 이름을 진단한다")
    void dependencyHealth_단일및복수실패의_실제컴포넌트를수집한다() throws Exception {
        String python = embeddedDependencyHealthPython();

        for (String failedComponent : EXPECTED_COMPONENTS) {
            assertThat(runValidator(python, healthJson(failedComponent), failedComponent))
                    .as("단일 실패 구성요소: %s", failedComponent)
                    .isEqualTo(1);
        }

        assertThat(runValidator(python, healthJson("db", "redis"), "db redis"))
                .as("복수 실패 구성요소")
                .isEqualTo(1);
        assertThat(runValidator(python, healthJsonWithout("mail"), "mail"))
                .as("누락된 구성요소")
                .isEqualTo(1);
        assertThat(runValidator(python, healthJson(), ""))
                .as("모든 dependency 정상")
                .isZero();
    }

    @Test
    @DisplayName("JSON 파싱 실패는 구성요소 실패와 다른 종료 상태를 사용한다")
    void dependencyHealth_잘못된JSON을_별도진단한다() throws Exception {
        assertThat(runValidator(embeddedDependencyHealthPython(), "{malformed", ""))
                .isEqualTo(2);
        assertThat(runValidator(embeddedDependencyHealthPython(), "{\"status\":\"DOWN\"}", ""))
                .isEqualTo(2);
    }

    private static String embeddedDependencyHealthPython() throws IOException {
        String script = Files.readString(APP_DEPLOY);
        String marker = "if python3 - \"$dependencies_body\" \"$dependencies_failures\" <<'PY'\n";
        int pythonStart = script.indexOf(marker);
        assertThat(pythonStart).as("dependency health Python validator").isGreaterThanOrEqualTo(0);
        pythonStart += marker.length();
        int pythonEnd = script.indexOf("\nPY\n", pythonStart);
        assertThat(pythonEnd).as("dependency health Python heredoc").isGreaterThan(pythonStart);
        return script.substring(pythonStart, pythonEnd);
    }

    private static int runValidator(String python, String body, String expectedFailures) throws Exception {
        Path directory = Files.createTempDirectory("masiton-dependency-health-");
        Path validatorFile = directory.resolve("validator.py");
        Path bodyFile = directory.resolve("dependencies.json");
        Path failuresFile = directory.resolve("dependency-failures.txt");
        Files.writeString(validatorFile, python, StandardCharsets.UTF_8);
        Files.writeString(bodyFile, body, StandardCharsets.UTF_8);

        try {
            Process process = new ProcessBuilder(
                    pythonCommand(), validatorFile.toString(), bodyFile.toString(), failuresFile.toString())
                    .redirectErrorStream(true)
                    .start();
            assertThat(process.waitFor(10, TimeUnit.SECONDS))
                    .as("dependency health validator timeout")
                    .isTrue();
            if (process.exitValue() == 1) {
                assertThat(Files.exists(failuresFile))
                        .as("dependency health validator output: %s",
                                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                        .isTrue();
                assertThat(Files.readString(failuresFile, StandardCharsets.UTF_8))
                        .isEqualTo(expectedFailures);
            }
            return process.exitValue();
        } finally {
            Files.deleteIfExists(failuresFile);
            Files.deleteIfExists(bodyFile);
            Files.deleteIfExists(validatorFile);
            Files.deleteIfExists(directory);
        }
    }

    private static String pythonCommand() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
    }

    private static String healthJson(String... failedComponents) {
        return "{\"components\":{" + componentJson("db", status("db", failedComponents)) + ","
                + componentJson("mail", status("mail", failedComponents)) + ","
                + componentJson("redis", status("redis", failedComponents)) + "}}";
    }

    private static String healthJsonWithout(String missingComponent) {
        StringBuilder components = new StringBuilder();
        for (String component : EXPECTED_COMPONENTS) {
            if (component.equals(missingComponent)) {
                continue;
            }
            if (components.length() > 0) {
                components.append(',');
            }
            components.append(componentJson(component, "UP"));
        }
        return "{\"components\":{" + components + "}}";
    }

    private static String componentJson(String name, String status) {
        return "\"" + name + "\":{\"status\":\"" + status + "\"}";
    }

    private static String status(String component, String... failedComponents) {
        for (String failedComponent : failedComponents) {
            if (component.equals(failedComponent)) {
                return "DOWN";
            }
        }
        return "UP";
    }
}
