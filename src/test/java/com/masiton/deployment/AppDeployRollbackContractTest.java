package com.masiton.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("앱 배포 롤백 계약")
class AppDeployRollbackContractTest {

    private static final Path APP_DEPLOY = Path.of("deploy/scripts/app-deploy.sh");

    @Test
    @DisplayName("롤백은 현재 서비스를 먼저 정리한 뒤 이전 산출물과 상태를 복원한다")
    void 롤백은현재서비스를먼저정리한뒤이전산출물과상태를복원한다() throws IOException {
        String rollback = rollbackSection();

        int stopCurrentServices = rollback.indexOf("systemctl stop \"$service\"");
        int disableCurrentServices = rollback.indexOf("systemctl disable \"$service\"");
        int restoreBackendUnit = rollback.indexOf(
                "restore_asset \"/etc/systemd/system/masiton-backend.service\"");
        int daemonReload = rollback.indexOf("systemctl daemon-reload");

        assertThat(stopCurrentServices).isGreaterThanOrEqualTo(0);
        assertThat(disableCurrentServices).isGreaterThan(stopCurrentServices);
        assertThat(restoreBackendUnit).isGreaterThan(disableCurrentServices);
        assertThat(daemonReload).isGreaterThan(restoreBackendUnit);
    }

    @Test
    @DisplayName("이전 unit이 없던 초기 설치 롤백은 health를 건너뛰고 known wants 링크를 정리한다")
    void 이전unit이없던초기설치롤백은health를건너뛰고knownWants링크를정리한다() throws IOException {
        String appDeploy = read(APP_DEPLOY);
        String rollback = rollbackSection();
        String restoreServices = restoreServicesSection(rollback);

        assertThat(appDeploy)
                .contains("previous_backend_unit_present=no")
                .contains("previous_frontend_unit_present=no")
                .contains("previous_backend_active=no")
                .contains("previous_frontend_active=no")
                .contains("previous_nginx_active=no");
        assertThat(rollback)
                .contains("rm -f \"/etc/systemd/system/multi-user.target.wants/$service\"")
                .containsSubsequence(
                        "if [ \"$previous_nginx_active\" = yes ] &&",
                        "nginx -t");
        assertThat(restoreServices)
                .containsSubsequence(
                        "if [ \"$previous_unit_present\" = yes ]; then",
                        "else",
                        "rm -f \"/etc/systemd/system/multi-user.target.wants/$service\"");
        assertThat(rollback)
                .containsSubsequence(
                        "if [ \"$previous_backend_active\" = yes ]; then",
                        "if [ \"$previous_backend_active\" = yes ] && [ \"$previous_frontend_active\" = yes ] &&")
                .doesNotContain("if [ \"$previous_backend_unit_present\" = yes ] && curl")
                .doesNotContain("if [ \"$previous_frontend_unit_present\" = yes ] && curl");
    }

    @Test
    @DisplayName("이전 비활성 unit은 재기동하지 않고 health 검증도 요구하지 않는다")
    void 이전비활성unit은재기동하지않고health검증도요구하지않는다() throws IOException {
        String rollback = rollbackSection();
        String restoreServices = restoreServicesSection(rollback);

        assertThat(restoreServices)
                .containsSubsequence(
                        "if [ \"$previous_unit_present\" = yes ]; then",
                        "if [ \"$previous_active\" = yes ]; then",
                        "systemctl restart \"$service\"",
                        "else",
                        "systemctl stop \"$service\"");
        assertThat(rollback)
                .containsSubsequence(
                        "rollback_backend_health=$([ \"$previous_backend_active\" = yes ] && printf no || printf yes)",
                        "if [ \"$previous_backend_active\" = yes ]; then",
                        "curl -fsS -m 3 http://127.0.0.1:8080/internal/health/ready")
                .containsSubsequence(
                        "elif [ \"$previous_frontend_active\" = yes ]; then",
                        "curl -fsS -m 3 http://127.0.0.1:3000/");
    }

    @Test
    @DisplayName("롤백 runtime health는 backend와 frontend가 모두 이전에 활성일 때만 실행한다")
    void 롤백RuntimeHealth는backend와frontend가모두이전에활성일때만실행한다() throws IOException {
        String rollback = rollbackSection();
        String runtimeHealthCondition = "if [ \"$previous_backend_active\" = yes ] && [ \"$previous_frontend_active\" = yes ] &&\n"
                + "     [ -x \"$OPT_DIR/bin/runtime-health.sh\" ] && ! \"$OPT_DIR/bin/runtime-health.sh\"; then";

        assertThat(rollback).contains(runtimeHealthCondition);
        assertThat(rollback.indexOf(runtimeHealthCondition))
                .isGreaterThan(rollback.indexOf("rollback_dependencies_body="));
    }

    @Test
    @DisplayName("dependency health 함수 호출은 조건문 밖의 최상위 호출로 유지한다")
    void dependencyHealth함수호출은조건문밖의최상위호출로유지한다() throws IOException {
        String appDeploy = read(APP_DEPLOY);
        String call = "check_dependency_health \"$dependencies_body\" \"$dependencies_failures\"";
        int functionStart = appDeploy.indexOf("check_dependency_health() {");
        int callStart = appDeploy.indexOf(call);

        assertThat(functionStart).isGreaterThanOrEqualTo(0);
        assertThat(callStart).isGreaterThan(functionStart);
        assertThat(appDeploy.split("check_dependency_health", -1).length - 1).isEqualTo(2);
        assertThat(appDeploy.substring(callStart, callStart + call.length()))
                .isEqualTo(call);
        assertThat(appDeploy.substring(Math.max(0, callStart - 40), callStart))
                .doesNotContain("if ")
                .doesNotContain("! ");
    }

    private static String rollbackSection() throws IOException {
        String appDeploy = read(APP_DEPLOY);
        int start = appDeploy.indexOf("rollback() {");
        int end = appDeploy.indexOf("\nbackup_asset() {", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return appDeploy.substring(start, end);
    }

    private static String restoreServicesSection(String rollback) {
        int start = rollback.indexOf("  for service in masiton-backend.service masiton-frontend.service; do",
                rollback.indexOf("systemctl daemon-reload"));
        int end = rollback.indexOf("  done\n  rollback_backend_health", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return rollback.substring(start, end);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
