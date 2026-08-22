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

    @Test
    @DisplayName("실제 app-deploy 호출 문맥의 실패는 원래 상태로 롤백하고 응답 본문을 노출하지 않는다")
    void dependencyHealth_실제appDeploy호출문맥에서_실패하면롤백하고본문을누출하지않는다() throws Exception {
        String script = Files.readString(APP_DEPLOY);
        String fragment = embeddedDependencyHealthFragment(script);
        String trapContext = embeddedTopLevelRollbackTrap(script);
        List<DependencyHealthCase> cases = List.of(
                new DependencyHealthCase("transport", 7, "HTTP 요청 실패: HTTP 000", null),
                new DependencyHealthCase("http", 1, "HTTP 실패: HTTP 503", null),
                new DependencyHealthCase("malformed", 1, "JSON 파싱 실패: HTTP 200", null),
                new DependencyHealthCase("db", 1, "구성요소 실패: db", "db"),
                new DependencyHealthCase("mail", 1, "구성요소 실패: mail", "mail"),
                new DependencyHealthCase("redis", 1, "구성요소 실패: redis", "redis"),
                new DependencyHealthCase("db-mail", 1, "구성요소 실패: db mail", "db mail"),
                new DependencyHealthCase("db-redis", 1, "구성요소 실패: db redis", "db redis"),
                new DependencyHealthCase("mail-redis", 1, "구성요소 실패: mail redis", "mail redis"),
                new DependencyHealthCase("all", 1, "구성요소 실패: db mail redis", "db mail redis"));

        for (DependencyHealthCase healthCase : cases) {
            ShellResult result = runDependencyHealthHarness(fragment, trapContext, healthCase.mode());

            assertThat(result.exitCode())
                    .as("실패 모드: %s", healthCase.mode())
                    .isEqualTo(healthCase.exitCode());
            assertThat(result.output())
                    .as("진단 메시지: %s", healthCase.mode())
                    .contains(healthCase.diagnostic());
            assertThat(result.output())
                    .as("응답 본문 누출: %s", healthCase.mode())
                    .doesNotContain("SENSITIVE_HEALTH_BODY");
            assertThat(result.rollbackInvoked())
                    .as("ERR trap rollback 호출: %s", healthCase.mode())
                    .isTrue();
            assertThat(result.rollbackStatus())
                    .as("rollback 원래 종료 상태: %s", healthCase.mode())
                    .isEqualTo(healthCase.exitCode());
            if (healthCase.expectedFailures() != null) {
                assertThat(result.output()).contains(healthCase.expectedFailures());
            }
        }
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

    private static String embeddedDependencyHealthFragment(String script) {
        String marker = "dependencies_body=\"$staged/dependencies.json\"\n";
        int fragmentStart = script.indexOf(marker);
        assertThat(fragmentStart).as("dependency health top-level fragment").isGreaterThanOrEqualTo(0);
        int fragmentEnd = script.indexOf("\n\nfront=\"\"", fragmentStart);
        assertThat(fragmentEnd).as("dependency health top-level fragment end").isGreaterThan(fragmentStart);
        String fragment = script.substring(fragmentStart, fragmentEnd);
        assertThat(fragment)
                .contains("check_dependency_health \"$dependencies_body\" \"$dependencies_failures\"");
        return fragment;
    }

    private static String embeddedTopLevelRollbackTrap(String script) {
        String marker = "rollback_enabled=yes\ntrap rollback ERR\n";
        int trapStart = script.indexOf(marker);
        assertThat(trapStart).as("production top-level rollback trap").isGreaterThanOrEqualTo(0);
        return script.substring(trapStart, trapStart + marker.length());
    }

    private static ShellResult runDependencyHealthHarness(String fragment, String trapContext, String mode)
            throws Exception {
        Path directory = Files.createTempDirectory("masiton-dependency-health-shell-");
        Path harness = directory.resolve("harness.sh");
        Path stagedDirectory = directory.resolve("staged");
        Path rollbackFile = directory.resolve("rollback-status.txt");
        Path rollbackInvokedFile = directory.resolve("rollback-invoked.txt");
        Files.createDirectories(stagedDirectory);
        Files.writeString(harness,
                shellHarness(fragment, trapContext, stagedDirectory, rollbackFile, rollbackInvokedFile),
                StandardCharsets.UTF_8);
        makeExecutable(harness);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(bashCommand(), harness.toString())
                    .redirectErrorStream(true);
            processBuilder.environment().put("DEPENDENCY_HEALTH_CASE", mode);
            Process process = processBuilder.start();
            assertThat(process.waitFor(10, TimeUnit.SECONDS))
                    .as("dependency health shell harness timeout: %s", mode)
                    .isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int rollbackStatus = Integer.parseInt(Files.readString(rollbackFile, StandardCharsets.UTF_8).trim());
            boolean rollbackInvoked = Files.readString(rollbackInvokedFile, StandardCharsets.UTF_8)
                    .trim()
                    .equals("yes");
            return new ShellResult(process.exitValue(), output, rollbackStatus, rollbackInvoked);
        } finally {
            Files.deleteIfExists(rollbackFile);
            Files.deleteIfExists(rollbackInvokedFile);
            Files.deleteIfExists(stagedDirectory.resolve("dependencies.json"));
            Files.deleteIfExists(stagedDirectory.resolve("dependency-failures.txt"));
            Files.deleteIfExists(stagedDirectory);
            Files.deleteIfExists(harness);
            Files.deleteIfExists(directory);
        }
    }

    private static String shellHarness(
            String fragment,
            String trapContext,
            Path stagedDirectory,
            Path rollbackFile,
            Path rollbackInvokedFile) {
        return "#!/usr/bin/env bash\n"
                + "set -Eeuo pipefail\n"
                + "rollback_marker='" + shellPath(rollbackFile) + "'\n"
                + "rollback_invoked_marker='" + shellPath(rollbackInvokedFile) + "'\n"
                + "rollback() {\n"
                + "  local original_exit_code=$?\n"
                + "  printf 'yes\\n' > \"$rollback_invoked_marker\"\n"
                + "  printf '%s\\n' \"$original_exit_code\" > \"$rollback_marker\"\n"
                + "  return \"$original_exit_code\"\n"
                + "}\n"
                + "staged='" + shellPath(stagedDirectory) + "'\n"
                + trapContext
                + "python3() { python \"$@\"; }\n"
                + "curl() {\n"
                + "  local output='' body code\n"
                + "  while (($# > 0)); do\n"
                + "    case \"$1\" in\n"
                + "      -o) output=\"$2\"; shift 2 ;;\n"
                + "      -w) shift 2 ;;\n"
                + "      *) shift ;;\n"
                + "    esac\n"
                + "  done\n"
                + "  case \"${DEPENDENCY_HEALTH_CASE:?}\" in\n"
                + "    transport) return 7 ;;\n"
                + "    http) body='{\"secret\":\"SENSITIVE_HEALTH_BODY\",\"components\":{\"db\":{\"status\":\"DOWN\"}}}'; code=503 ;;\n"
                + "    malformed) body='{malformed-SENSITIVE_HEALTH_BODY'; code=200 ;;\n"
                + "    db) body='{\"secret\":\"SENSITIVE_HEALTH_BODY\",\"components\":{\"db\":{\"status\":\"DOWN\"},\"mail\":{\"status\":\"UP\"},\"redis\":{\"status\":\"UP\"}}}'; code=200 ;;\n"
                + "    mail) body='{\"secret\":\"SENSITIVE_HEALTH_BODY\",\"components\":{\"db\":{\"status\":\"UP\"},\"mail\":{\"status\":\"DOWN\"},\"redis\":{\"status\":\"UP\"}}}'; code=200 ;;\n"
                + "    redis) body='{\"secret\":\"SENSITIVE_HEALTH_BODY\",\"components\":{\"db\":{\"status\":\"UP\"},\"mail\":{\"status\":\"UP\"},\"redis\":{\"status\":\"DOWN\"}}}'; code=200 ;;\n"
                + "    db-mail) body='{\"secret\":\"SENSITIVE_HEALTH_BODY\",\"components\":{\"db\":{\"status\":\"DOWN\"},\"mail\":{\"status\":\"DOWN\"},\"redis\":{\"status\":\"UP\"}}}'; code=200 ;;\n"
                + "    db-redis) body='{\"secret\":\"SENSITIVE_HEALTH_BODY\",\"components\":{\"db\":{\"status\":\"DOWN\"},\"mail\":{\"status\":\"UP\"},\"redis\":{\"status\":\"DOWN\"}}}'; code=200 ;;\n"
                + "    mail-redis) body='{\"secret\":\"SENSITIVE_HEALTH_BODY\",\"components\":{\"db\":{\"status\":\"UP\"},\"mail\":{\"status\":\"DOWN\"},\"redis\":{\"status\":\"DOWN\"}}}'; code=200 ;;\n"
                + "    all) body='{\"secret\":\"SENSITIVE_HEALTH_BODY\",\"components\":{\"db\":{\"status\":\"DOWN\"},\"mail\":{\"status\":\"DOWN\"},\"redis\":{\"status\":\"DOWN\"}}}'; code=200 ;;\n"
                + "    *) return 64 ;;\n"
                + "  esac\n"
                + "  printf '%s' \"$body\" > \"$output\"\n"
                + "  printf '%s' \"$code\"\n"
                + "}\n"
                + fragment + "\n";
    }

    private static String shellPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static String bashCommand() {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            return "bash";
        }
        Path gitBash = Path.of("C:", "Program Files", "Git", "bin", "bash.exe");
        return Files.isExecutable(gitBash) ? gitBash.toString() : "bash";
    }

    private static void makeExecutable(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, java.util.Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Windows test environments use the bash interpreter directly.
        }
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

    private record DependencyHealthCase(String mode, int exitCode, String diagnostic, String expectedFailures) {
    }

    private record ShellResult(int exitCode, String output, int rollbackStatus, boolean rollbackInvoked) {
    }
}
