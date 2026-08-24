package com.masiton.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("운영 배포 산출물 계약")
class AppRunScriptContractTest {

    private static final Path APP_RUN = Path.of("deploy/scripts/app-run.sh");
    private static final Path SECRETS = Path.of("deploy/scripts/app-secrets-render.sh");
    private static final Path NGINX_SITE = Path.of("deploy/nginx/masiton.click.conf");
    private static final Path NGINX_MAIN = Path.of("deploy/nginx/nginx.conf");
    private static final Path NGINX_INSTALL = Path.of("deploy/scripts/nginx-install.sh");
    private static final Path NGINX_SMOKE = Path.of("deploy/scripts/nginx-smoke.sh");
    private static final String CALLBACK = "/api/webhooks/youtube/channel-updates";

    @Test
    @DisplayName("검증 참여자 환경변수와 SSM 주입을 운영 실행 경로에서 제거한다")
    void 운영실행경로에서검증참여자설정을제거한다() throws IOException {
        String appRun = read(APP_RUN);
        String secrets = read(SECRETS);

        assertThat(appRun).doesNotContain("VERIFICATION_");
        assertThat(secrets)
                .doesNotContain("verification-login-id")
                .doesNotContain("verification-password-hash")
                .doesNotContain("/masiton/access/verification-");
    }

    @Test
    @DisplayName("Nginx는 검증 gate 없이 API와 화면을 각 upstream으로 직접 전달한다")
    void nginx는검증게이트없이경로를직접프록시한다() throws IOException {
        String site = read(NGINX_SITE);

        assertThat(site)
                .doesNotContain("auth_request")
                .doesNotContain("@verification_api")
                .doesNotContain("@validation_login")
                .doesNotContain("/_verification/")
                .doesNotContain("/verification/login")
                .doesNotContain("/api/verification/sessions")
                .contains("location = /api {")
                .contains("location ^~ /api/ {")
                .contains("location / {")
                .contains("proxy_pass http://masiton_backend;")
                .contains("proxy_pass http://masiton_frontend;");
        assertThat(read(NGINX_INSTALL))
                .doesNotContain("verification_status")
                .doesNotContain("rollback_basic_auth")
                .contains("BASIC_AUTH_DROPIN")
                .contains("OLD_AUTH_MAP")
                .contains("trap on_install_failure")
                .contains("restore_or_remove");
    }

    @Test
    @DisplayName("Nginx의 Host·health·internal 경계를 유지한다")
    void nginx의네트워크경계를유지한다() throws IOException {
        String site = read(NGINX_SITE);
        assertThat(site)
                .contains("listen 80 default_server;")
                .contains("listen 443 ssl default_server;")
                .contains("location / { return 444; }")
                .contains("location = /_masiton/alb-health {")
                .contains("proxy_pass http://masiton_backend/internal/health/ready;")
                .contains("location ^~ /internal/ { access_log off; return 404; }")
                .contains("location = /internal { access_log off; return 404; }")
                .contains("server 127.0.0.1:8080;")
                .contains("server 127.0.0.1:3000;")
                .contains("proxy_set_header X-Forwarded-For $remote_addr;");
    }

    @Test
    @DisplayName("Webhook은 GET·POST와 제한된 본문·유량·헤더·timeout 방어를 유지한다")
    void webhook방어불변식을유지한다() throws IOException {
        String site = read(NGINX_SITE);
        assertThat(site)
                .contains("location = " + CALLBACK + " {")
                .contains("limit_req zone=masiton_ungated burst=20 nodelay;")
                .contains("if ($request_method !~ ^(GET|POST)$) { return 405; }")
                .contains("client_max_body_size 128k;")
                .contains("proxy_set_header Authorization \"\";")
                .contains("proxy_set_header Cookie \"\";")
                .contains("proxy_connect_timeout 5s;")
                .contains("proxy_send_timeout 30s;")
                .contains("proxy_read_timeout 30s;");
        assertThat(read(NGINX_MAIN)).contains("limit_req_zone $binary_remote_addr zone=masiton_ungated:");
    }

    @Test
    @DisplayName("Smoke는 공개 상태와 내부 경계만 검증하고 gate 전제를 갖지 않는다")
    void smoke는공개상태와방어불변식을검증한다() throws IOException {
        String smoke = read(NGINX_SMOKE);
        assertThat(smoke)
                .contains("assert_reaches_backend GET /api")
                .contains("/api/unknown-route")
                .contains("assert_reaches_backend GET /")
                .contains("assert_status 405 PATCH /api/webhooks/youtube/channel-updates")
                .contains("assert_status 404 GET /internal")
                .doesNotContain("VALIDATION_ACCESS_REQUIRED")
                .doesNotContain("assert_validation_gate")
                .doesNotContain("verification");
    }

    @Test
    @DisplayName("설치 스크립트는 TLS·설정 검사·재기동·smoke 순서를 유지한다")
    void 설치스크립트의정상경로를유지한다() throws IOException {
        String install = read(NGINX_INSTALL);
        int configTest = install.indexOf("nginx -t");
        int restart = install.lastIndexOf("systemctl restart nginx");
        int smoke = install.indexOf("bash \"$STAGE/nginx-smoke.sh\"");
        assertThat(install).contains("tls-deploy-cert.sh").contains("systemctl enable nginx");
        assertThat(configTest).isGreaterThanOrEqualTo(0);
        assertThat(restart).isGreaterThan(configTest);
        assertThat(smoke).isGreaterThan(restart);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
