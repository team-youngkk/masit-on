package com.masiton.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("운영 배포 산출물 계약")
class AppRunScriptContractTest {

    private static final Path APP_RUN_SCRIPT = Path.of("deploy/scripts/app-run.sh");
    private static final Path APP_DEPLOY_SCRIPT = Path.of("deploy/scripts/app-deploy.sh");
    private static final Path NGINX_SITE = Path.of("deploy/nginx/masiton.click.conf");
    private static final Path NGINX_MAIN = Path.of("deploy/nginx/nginx.conf");
    private static final Path NGINX_INSTALL_SCRIPT = Path.of("deploy/scripts/nginx-install.sh");
    private static final Path NGINX_SMOKE_SCRIPT = Path.of("deploy/scripts/nginx-smoke.sh");
    private static final Path CI_WORKFLOW = Path.of(".github/workflows/ci.yml");
    private static final Path SECURITY_CONFIGURATION = Path.of(
            "src/main/java/com/masiton/security/infrastructure/configuration/SecurityConfiguration.java");
    private static final Path COMMON_PROFILE = Path.of("src/main/resources/application.yml");
    private static final Path PROD_PROFILE = Path.of("src/main/resources/application-prod.yml");
    private static final Path HEALTH_METRICS_SCRIPT = Path.of("deploy/scripts/health-metrics.sh");

    private static final String LOOPBACK = "127.0.0.1";

    private static final String CALLBACK_PATH = "/api/webhooks/youtube/channel-updates";
    private static final String GATE_DIRECTIVE = "auth_request /_verification/session;";

    /**
     * 기본값이 없는 placeholder만 고른다. {@code ${VAR:기본값}}은 값이 없어도 기동한다.
     * Spring relaxed binding 때문에 {@code ${db.url}}도 환경 변수 {@code DB_URL}로 해석되므로
     * 소문자·점·하이픈 표기까지 받아 정규화한다. 중첩 기본값의 내부 이름을 필수로 오인하지
     * 않도록 이름에 {@code : \{ \}}를 허용하지 않는다.
     */
    private static final Pattern DEFAULTLESS_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.\\-]+)}");
    private static final Pattern PLACEHOLDER_WITH_DEFAULT = Pattern.compile("\\$\\{([A-Za-z0-9_.\\-]+):");

    /**
     * {@code case "$component" in}의 backend 분기를 분기 종료 토큰 {@code ;;}까지 자른다.
     * 다음 분기 라벨을 경계로 쓰면 분기 안에 중첩 {@code case}가 생기는 순간 조용히 잘린다.
     */
    private static final Pattern BACKEND_BRANCH = Pattern.compile("\\n\\s*backend\\)(.*?)\\n\\s*;;", Pattern.DOTALL);
    private static final Pattern FRONTEND_BRANCH = Pattern.compile("\\n\\s*frontend\\)(.*?)\\n\\s*;;", Pattern.DOTALL);

    /** 애플리케이션 포트 바인딩을 넓힐 수 있는 설정 이름. 운영 컨테이너에 전달하지 않는다. */
    private static final Set<String> BINDING_WIDENING_ENV = Set.of("SERVER_ADDRESS", "SERVER_PORT");

    /** 실제로 컨테이너를 띄우는 명령. 이 범위 밖의 {@code -e}는 전달로 세지 않는다. */
    private static final Pattern DOCKER_RUN = Pattern.compile("exec\\s+/usr/bin/docker\\s+run\\b(.*)", Pattern.DOTALL);

    /** 앞에 단어·하이픈이 붙은 {@code --volume-e} 같은 토큰을 {@code -e}로 오인하지 않는다. */
    private static final Pattern PASSED_ENV = Pattern.compile("(?<![\\w-])-e\\s+([A-Z0-9_]+)\\b");
    private static final Pattern EXPORTED_ENV = Pattern.compile("(?<![\\w-])export\\s+([A-Z0-9_]+)\\b");

    private static final Pattern CALLBACK_EXPORT = Pattern.compile(
            "(?<![\\w-])export\\s+YOUTUBE_WEBHOOK_CALLBACK_URL=\"?([^\"\\s]+)\"?");

    private static final Pattern METHOD_PERMIT_ALL = Pattern.compile(
            "requestMatchers\\(HttpMethod\\.(GET|POST|DELETE),([^)]*)\\)\\.permitAll\\(\\)",
            Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern LOCATION = Pattern.compile(
            "^ {4}location\\s+(.+?)\\s*\\{\\R(.*?)^ {4}}", Pattern.MULTILINE | Pattern.DOTALL);

    private static final List<ApiRoute> PUBLIC_API_ROUTES = List.of(
            new ApiRoute("GET", "/api/restaurants", "/api/restaurants"),
            new ApiRoute("GET", "/api/restaurants/*", "/api/restaurants/opaque-id"),
            new ApiRoute("GET", "/api/curations", "/api/curations"),
            new ApiRoute("GET", "/api/curations/*", "/api/curations/opaque-id"),
            new ApiRoute("GET", "/api/creators", "/api/creators"),
            new ApiRoute("GET", "/api/creators/*", "/api/creators/opaque-id"),
            new ApiRoute("GET", "/api/creators/*/restaurants", "/api/creators/opaque-id/restaurants"),
            new ApiRoute("GET", "/api/creators/*/videos", "/api/creators/opaque-id/videos"),
            new ApiRoute("POST", "/api/auth/registrations", "/api/auth/registrations"),
            new ApiRoute("POST", "/api/auth/email-verifications", "/api/auth/email-verifications"),
            new ApiRoute("POST", "/api/auth/email-verifications/resend", "/api/auth/email-verifications/resend"),
            new ApiRoute("POST", "/api/auth/password-resets/requests", "/api/auth/password-resets/requests"),
            new ApiRoute("POST", "/api/auth/password-resets/confirmations", "/api/auth/password-resets/confirmations"),
            new ApiRoute("POST", "/api/auth/tokens", "/api/auth/tokens"),
            new ApiRoute("POST", "/api/auth/tokens/refresh", "/api/auth/tokens/refresh"),
            new ApiRoute("POST", "/api/restaurants/course-routes", "/api/restaurants/course-routes"),
            new ApiRoute("POST", "/api/restaurants/natural-language-search",
                    "/api/restaurants/natural-language-search"));

    @Test
    @DisplayName("AI Worker와 Gemini Free Tier 게이트를 SSM에서 읽어 backend 컨테이너에 전달한다")
    void backend_AIWorker설정을컨테이너에전달한다() throws IOException {
        String script = Files.readString(APP_RUN_SCRIPT);
        String secretsRenderer = Files.readString(Path.of("deploy/scripts/app-secrets-render.sh"));

        assertThat(script)
                .contains("optional_param /masiton/ai/worker/enabled")
                .contains("optional_param /masiton/ai/worker/provider-quota-limit")
                .contains("optional_param /masiton/ai/worker/application-quota-limit")
                .contains("optional_param /masiton/ai/worker/quota-window")
                .contains("GEMINI_ENABLED=$(optional_bool_param /masiton/ai/gemini/enabled)")
                .contains("GEMINI_FREE_TIER_VERIFIED=$(optional_bool_param /masiton/ai/gemini/free-tier-verified)")
                .contains("GEMINI_PAID_BILLING_ENABLED=$(optional_bool_param /masiton/ai/gemini/paid-billing-enabled)")
                .contains("*) printf 'false' ;;")
                .contains("-e AI_WORKER_ENABLED -e AI_WORKER_PROVIDER_QUOTA_LIMIT")
                .contains("-e AI_WORKER_APPLICATION_QUOTA_LIMIT -e AI_WORKER_QUOTA_WINDOW")
                .contains("-e GEMINI_ENABLED -e GEMINI_FREE_TIER_VERIFIED -e GEMINI_PAID_BILLING_ENABLED");
        assertThat(secretsRenderer)
                .contains("get-parameters-by-path")
                .contains("/masiton/ai/temporary-input/keys")
                .contains("masiton.ai.temporary-input.keys.$key_id");
    }

    /**
     * 운영 프로파일이 기본값 없이 요구하는 환경 변수를 스크립트가 실제로 넘기는지 고정한다.
     *
     * 기본값 없는 {@code ${VAR}}는 값이 없으면 placeholder 해석 단계에서 기동이 실패한다.
     * 속성을 추가하면서 스크립트를 같이 고치지 않으면 빌드와 테스트는 통과하고 배포에서만
     * 드러난다. YOUTUBE_WEBHOOK_CALLBACK_URL이 그렇게 빠졌고, 그전에도 같은 유형으로
     * DB 비밀번호가 주입되지 않아 재기동 루프에 들어간 적이 있다(M2 기록 9.2절).
     * 개별 이름을 나열하지 않고 프로파일에서 뽑아 대조해 다음 누락도 잡는다.
     *
     * 운영 실행이 요구하는 값은 공통 계층과 prod 계층을 합친 결과다. 단 prod가 같은 키를
     * 기본값과 함께 재선언하면 그 값이 이기므로 공통 계층의 선언은 요구 대상에서 뺀다.
     * 대조 대상은 backend 분기의 {@code docker run} 명령으로 좁힌다. 스크립트 전체나 분기
     * 전체를 보면 주석이나 다른 명령의 {@code -e}가 "전달했다"로 잡혀, 정작 컨테이너가 값을
     * 못 받는 경우를 놓친다. {@code -e NAME} 형식은 셸에 그 변수가 있을 때만 값을 넘기므로
     * {@code export}까지 함께 확인한다.
     */
    @Test
    @DisplayName("운영 프로파일이 기본값 없이 요구하는 환경 변수를 모두 컨테이너에 전달한다")
    void backend_기본값없는운영환경변수를_docker_run과_export양쪽에서전달한다() throws IOException {
        String branch = stripComments(backendBranch(Files.readString(APP_RUN_SCRIPT)));
        Set<String> passed = names(PASSED_ENV, dockerRunCommand(branch));
        Set<String> exported = names(EXPORTED_ENV, branch);

        Set<String> required = requiredEnvironmentNames(
                Files.readString(COMMON_PROFILE), Files.readString(PROD_PROFILE));

        assertThat(required).contains("YOUTUBE_WEBHOOK_CALLBACK_URL");
        assertThat(required).allSatisfy(name -> {
            assertThat(passed).as("%s를 backend docker run에 -e로 전달해야 한다", name).contains(name);
            assertThat(exported).as("%s를 backend 분기에서 export해야 한다", name).contains(name);
        });
    }

    /**
     * 운영 프로파일이 선언한 Webhook 콜백 URL의 경로가 Nginx 제한 공개 gate에서 제외되고,
     * 제외에 따르는 유량·메서드·본문 제한이 함께 걸려 있는지 고정한다.
     *
     * 콜백 URL을 넘기는 것만으로는 허브가 도달하지 못한다. prefix {@code location /api/}가
     * 검증 세션 {@code auth_request}를 강제하므로, 쿠키를 보내지 않는 허브는 구독 확인
     * GET에서 401을 받고 구독 자체가 성립하지 않는다. 제외는 exact-match {@code location}으로만
     * 유효하다. 제외를 허용하는 조건은 ADR-DEPLOY-004 6.2절, 제외 경로 목록과 제한값은
     * 검증 참여자 제한 공개 API 계약의 "세션 gate 제외 경로" 절이 소유한다.
     */
    @Test
    @DisplayName("운영 콜백 URL 경로를 Nginx 세션 gate에서 제외하고 유량·메서드·본문을 제한한다")
    void nginx_콜백URL경로를세션gate에서제외하고제한을함께건다() throws IOException {
        String branch = stripComments(backendBranch(Files.readString(APP_RUN_SCRIPT)));
        String callbackUrl = callbackUrl(branch);
        String site = Files.readString(NGINX_SITE);

        assertThat(callbackUrl)
                .as("허브는 평문 HTTP 콜백을 받지 않는다")
                .startsWith("https://");
        assertThat(pathOf(callbackUrl))
                .as("프로파일의 콜백 경로와 Nginx 제외 경로가 같아야 한다")
                .isEqualTo(CALLBACK_PATH);

        assertThat(site)
                .as("%s를 exact-match location으로 분리해 gate에서 제외해야 한다", CALLBACK_PATH)
                .contains("location = " + CALLBACK_PATH + " {");

        // gate가 server 컨텍스트에 있으면 location 본문이 비어 있어도 상속돼 제외가 무효가 된다.
        assertThat(hasServerLevelAuthRequest(site))
                .as("auth_request를 server 컨텍스트에 두면 제외 location도 상속해 gate에 걸린다")
                .isFalse();

        String body = gateExcludedLocationBody(site);
        assertThat(body)
                .as("제외 location에 gate를 다시 걸면 제외가 무효가 된다")
                .doesNotContain(GATE_DIRECTIVE);
        assertThat(body)
                .as("외부 허브가 보낸 자격 증명 헤더를 백엔드로 흘려보내지 않아야 한다")
                .contains("proxy_set_header Authorization \"\";")
                .contains("proxy_set_header Cookie \"\";");
        assertThat(body)
                .as("무인증 요청이 백엔드에 도달하므로 유량·메서드·본문 제한이 함께 있어야 한다")
                .contains("limit_req zone=masiton_ungated")
                .contains("error_page 419 = @verification_webhook;")
                .contains("if ($request_method !~ ^(GET|POST)$)")
                .contains("return 419;")
                .contains("client_max_body_size 128k;");
        assertThat(body)
                .as("생략하면 Nginx 기본 60s가 적용돼 상위 location보다 오래 커넥션을 점유한다")
                .contains("proxy_connect_timeout 5s;")
                .contains("proxy_send_timeout 30s;")
                .contains("proxy_read_timeout 30s;");

        assertThat(Files.readString(NGINX_MAIN))
                .as("limit_req zone은 http 컨텍스트에 선언해야 한다")
                .contains("limit_req_zone $binary_remote_addr zone=masiton_ungated:");

        String webhookGate = locationBySelector(nginxLocations(site), "@verification_webhook").body();
        assertThat(webhookGate)
                .as("Webhook 비허용 메서드는 gate 뒤에도 외부 자격 증명을 backend로 전달하지 않아야 한다")
                .contains(GATE_DIRECTIVE)
                .contains("error_page 401 = /_verification/access-required;")
                .contains("error_page 500 503 = /_verification/unavailable;")
                .contains("proxy_pass http://masiton_backend;")
                .contains("proxy_set_header Authorization \"\";")
                .contains("proxy_set_header Cookie \"\";");
    }

    @Test
    @DisplayName("Spring Security 공개 API 매트릭스와 Nginx 경로·메서드 예외를 일치시킨다")
    void nginx_공개API매트릭스를_SpringSecurity와경로메서드단위로일치시킨다() throws IOException {
        String security = stripComments(Files.readString(SECURITY_CONFIGURATION));
        Set<String> actualPermits = methodPermitAllRoutes(security);
        Set<String> expectedPermits = new TreeSet<>();
        PUBLIC_API_ROUTES.forEach(route -> expectedPermits.add(route.method() + " " + route.securityPattern()));
        expectedPermits.add("GET " + CALLBACK_PATH);
        expectedPermits.add("POST " + CALLBACK_PATH);
        expectedPermits.add("POST /api/verification/sessions");
        expectedPermits.add("DELETE /api/verification/sessions");
        assertThat(actualPermits)
                .as("SecurityConfiguration의 메서드별 permitAll 목록이 공개 경계 계약과 같아야 한다")
                .containsExactlyElementsOf(expectedPermits);

        String site = Files.readString(NGINX_SITE);
        List<NginxLocation> locations = nginxLocations(site);
        assertThat(hasServerLevelAuthRequest(site))
                .as("server 수준 auth_request는 공개 location에도 상속돼 예외를 무효화한다")
                .isFalse();

        String publicProxy = locationBySelector(locations, "@public_api").body();
        String verificationProxy = locationBySelector(locations, "@verification_api").body();
        String httpsServer = httpsApplicationServer(site);
        assertThat(publicProxy)
                .as("일반 공개 API는 선택 회원 인증과 refresh cookie 계약을 위해 자격 증명을 보존해야 한다")
                .doesNotContain(GATE_DIRECTIVE)
                .doesNotContain("proxy_set_header Authorization \"\";")
                .doesNotContain("proxy_set_header Cookie \"\";")
                .contains("proxy_pass http://masiton_backend;");
        assertThat(serverLevelDirectiveCount(httpsServer, "recursive_error_pages on;"))
                .as("첫 418/419 전환 전에 적용되도록 HTTPS server 범위에서 재귀 오류 처리를 한 번만 켜야 한다")
                .isEqualTo(1);
        assertThat(verificationProxy)
                .as("418/419 내부 redirect 뒤의 gate 오류도 공통 JSON adapter로 다시 처리해야 한다")
                .contains(GATE_DIRECTIVE)
                .contains("error_page 401 = /_verification/access-required;")
                .contains("error_page 500 503 = /_verification/unavailable;")
                .contains("proxy_pass http://masiton_backend;");
        assertThat(locationBySelector(locations, "= /_verification/access-required").body())
                .as("401 종착 adapter가 error_page를 다시 적용하면 내부 redirect가 순환한다")
                .doesNotContain("error_page", "recursive_error_pages");
        assertThat(locationBySelector(locations, "= /_verification/unavailable").body())
                .as("503 종착 adapter가 error_page를 다시 적용하면 내부 redirect가 순환한다")
                .doesNotContain("error_page", "recursive_error_pages");

        for (ApiRoute route : PUBLIC_API_ROUTES) {
            NginxLocation location = resolveLocation(locations, route.examplePath());
            assertThat(location.body())
                    .as("%s %s만 공개 proxy로 보내야 한다", route.method(), route.examplePath())
                    .contains("error_page 418 = @public_api;")
                    .contains("error_page 419 = @verification_api;")
                    .contains("if ($request_method = " + route.method() + ") { return 418; }")
                    .contains("return 419;")
                    .doesNotContain(GATE_DIRECTIVE);
        }
    }

    @Test
    @DisplayName("공개 상세 정규식은 불투명 단일 segment에서 끝나고 다른 메서드와 하위 경로는 gate를 유지한다")
    void nginx_공개상세정규식을_anchor하고_비공개요청은gate로보낸다() throws IOException {
        List<NginxLocation> locations = nginxLocations(Files.readString(NGINX_SITE));

        for (String path : List.of(
                "/api/restaurants/opaque-id",
                "/api/curations/opaque-id",
                "/api/creators/opaque-id",
                "/api/creators/opaque-id/restaurants",
                "/api/creators/opaque-id/videos")) {
            NginxLocation location = resolveLocation(locations, path);
            assertThat(location.selector())
                    .as("%s 상세 location은 시작·끝 anchor와 단일 segment 규칙을 가져야 한다", path)
                    .startsWith("~ ^")
                    .contains("[^/]+")
                    .endsWith("$");
            assertThat(location.body())
                    .as("공개 path의 비공개 메서드는 @verification_api로 보내야 한다")
                    .contains("error_page 419 = @verification_api;")
                    .contains("return 419;");
        }

        for (String path : List.of(
                "/api/restaurants/opaque-id/extra",
                "/api/curations/opaque-id/extra",
                "/api/creators/opaque-id/extra",
                "/api/creators/opaque-id/restaurants/extra",
                "/api/creators/opaque-id/videos/extra")) {
            assertThat(resolveLocation(locations, path).body())
                    .as("anchor 밖 하위 경로 %s는 기본 API gate로 돌아가야 한다", path)
                    .contains(GATE_DIRECTIVE);
        }
    }

    @Test
    @DisplayName("검증 세션만 POST DELETE를 제외하고 관리자 미정의 API와 internal 경계는 닫는다")
    void nginx_검증세션_관리자_미정의API_internal경계를유지한다() throws IOException {
        List<NginxLocation> locations = nginxLocations(Files.readString(NGINX_SITE));
        NginxLocation sessions = resolveLocation(locations, "/api/verification/sessions");
        assertThat(sessions.selector()).isEqualTo("= /api/verification/sessions");
        assertThat(sessions.body())
                .contains("if ($request_method !~ ^(POST|DELETE)$)")
                .contains("error_page 419 = @verification_api;")
                .contains("proxy_pass http://masiton_backend;")
                .contains("proxy_set_header Authorization \"\";")
                .doesNotContain(GATE_DIRECTIVE);

        assertThat(resolveLocation(locations, "/api/admin/anything").body()).contains(GATE_DIRECTIVE);
        assertThat(resolveLocation(locations, "/api/not-defined").body()).contains(GATE_DIRECTIVE);

        NginxLocation apiRoot = resolveLocation(locations, "/api");
        assertThat(apiRoot.selector()).isEqualTo("= /api");
        assertThat(apiRoot.body())
                .contains(GATE_DIRECTIVE)
                .contains("proxy_set_header X-Forwarded-For $remote_addr;");

        NginxLocation internalRoot = resolveLocation(locations, "/internal");
        NginxLocation internalChild = resolveLocation(locations, "/internal/health/live");
        assertThat(internalRoot.selector()).isEqualTo("= /internal");
        assertThat(internalRoot.body()).contains("return 404;").doesNotContain("proxy_pass");
        assertThat(internalChild.selector()).isEqualTo("^~ /internal/");
        assertThat(internalChild.body()).contains("return 404;").doesNotContain("proxy_pass");

        assertThat(locationBySelector(locations, "= /verification/login").body())
                .as("로그인 화면은 GET 외 메서드를 프론트엔드로 전달하지 않아야 한다")
                .contains("limit_except GET", "deny all;");
        assertThat(locationBySelector(locations, "^~ /_next/static/").body())
                .as("정적 파일은 GET 외 메서드를 프론트엔드로 전달하지 않아야 한다")
                .contains("limit_except GET", "deny all;");
    }

    @Test
    @DisplayName("SSM 배포 번들에 Nginx 설치 산출물을 싣고 백엔드 배포 뒤 설치한다")
    void ci_SSM번들로nginx산출물을전달하고_백엔드배포뒤설치한다() throws IOException {
        String workflow = Files.readString(CI_WORKFLOW);

        assertThat(workflow).contains(
                "deploy/scripts/nginx-install.sh",
                "deploy/scripts/nginx-smoke.sh",
                "deploy/scripts/tls-deploy-cert.sh",
                "deploy/nginx/nginx.conf",
                "deploy/nginx/masiton.click.conf",
                "deploy/nginx/00-masiton-upgrade-map.conf",
                "deploy/nginx/masiton-tls-renew.service",
                "deploy/nginx/masiton-tls-renew.timer",
                "with tarfile.open(fileobj=archive, mode=\"w:gz\", compresslevel=9) as bundle:",
                "tar -xzf \"$STAGE/deploy-bundle.tar.gz\" -C \"$STAGE\"",
                "max_ssm_command_bytes = 64 * 1024",
                "payload_bytes = len(serialized.encode(\"utf-8\"))",
                "if payload_bytes > max_ssm_command_bytes:",
                "chmod +x \"$STAGE/app-deploy.sh\" \"$STAGE/app-run.sh\" \"$STAGE/app-secrets-render.sh\" "
                        + "\"$STAGE/nginx-install.sh\" \"$STAGE/nginx-smoke.sh\" \"$STAGE/tls-deploy-cert.sh\"");

        int appDeploy = workflow.indexOf("lines.append(f'\"$STAGE/app-deploy.sh\"");
        int nginxInstall = workflow.indexOf("lines.append('\"$STAGE/nginx-install.sh\" \"$STAGE\"')");
        assertThat(appDeploy).isNotNegative();
        assertThat(nginxInstall)
                .as("새 backend 검증 세션 경계가 준비된 뒤 Nginx 컷오버를 실행해야 한다")
                .isGreaterThan(appDeploy);
    }

    @Test
    @DisplayName("Nginx 재기동 smoke 실패를 컷오버 rollback 범위에서 처리한다")
    void nginx_smoke를재기동직후rollback범위에서실행한다() throws IOException {
        String install = Files.readString(NGINX_INSTALL_SCRIPT);
        String smoke = Files.readString(NGINX_SMOKE_SCRIPT);
        int smokeRun = install.indexOf("bash \"$STAGE/nginx-smoke.sh\"");
        int restart = install.lastIndexOf("systemctl restart nginx", smokeRun);
        int rollbackTrapRelease = install.indexOf("trap - ERR", smokeRun);

        assertThat(install)
                .contains("tls-deploy-cert.sh nginx-smoke.sh")
                .contains("bash \"$STAGE/nginx-smoke.sh\"")
                .doesNotContain("$OPT_DIR/bin/nginx-smoke.sh");
        assertThat(restart).isNotNegative();
        assertThat(smokeRun).as("재기동 뒤 smoke를 실행해야 한다").isGreaterThan(restart);
        assertThat(rollbackTrapRelease).as("smoke 성공 뒤에만 rollback ERR trap을 해제해야 한다").isGreaterThan(smokeRun);

        assertThat(smoke)
                .startsWith("#!/usr/bin/env bash")
                .contains("set -euo pipefail")
                .contains("VALIDATION_ACCESS_REQUIRED")
                .contains("if [ \"$method\" = \"HEAD\" ]; then")
                .contains("curl_args+=(--head)")
                .contains("curl_args+=(--request \"$method\")")
                .contains("[[ \"$status\" =~ ^(000|3[0-9][0-9]|502|503|504)$ ]]")
                .contains("[ \"$status\" = \"401\" ] || is_api_routing_failure \"$status\"")
                .contains("|| is_api_routing_failure \"$status\"; then")
                .contains("assert_validation_gate POST /api/auth/tokens '{}'")
                .contains("assert_validation_gate GET /api/nginx-smoke-unknown")
                .contains("assert_validation_gate PATCH /api/restaurants")
                .contains("assert_validation_gate GET /api/verification/sessions")
                .contains("assert_validation_gate_status HEAD /api/webhooks/youtube/channel-updates")
                .contains("assert_validation_gate PATCH /api/webhooks/youtube/channel-updates")
                .contains("assert_not_validation_access_error POST /api/verification/sessions '{}' \"$PUBLIC_ORIGIN\"")
                .contains("assert_status 204 DELETE /api/verification/sessions '' \"$PUBLIC_ORIGIN\"")
                .contains("assert_error_code 401 INVALID_REFRESH_TOKEN POST /api/auth/tokens/refresh '' \"$PUBLIC_ORIGIN\"")
                .contains("assert_not_validation_access_error POST \"$path\" '{'")
                .contains("assert_status 404 GET /internal")
                .contains("assert_status 404 GET /internal/health/live")
                .contains("assert_not_validation_access_error GET /api/webhooks/youtube/channel-updates")
                .contains("assert_not_validation_access_error POST /api/webhooks/youtube/channel-updates")
                .doesNotContain("--cookie", "Authorization:");
        PUBLIC_API_ROUTES.stream()
                .filter(route -> route.method().equals("GET"))
                .forEach(route -> assertThat(smoke)
                        .as("공개 GET 운영 smoke 누락: %s", route.examplePath())
                        .contains(route.examplePath().replace("opaque-id", "${opaque_segment}")));
        PUBLIC_API_ROUTES.stream()
                .filter(route -> route.method().equals("POST"))
                .forEach(route -> assertThat(smoke)
                        .as("공개 POST 운영 smoke 누락: %s", route.examplePath())
                        .contains(route.examplePath()));
    }

    @Test
    @DisplayName("Nginx 관리 설정이 배치된 경우에만 app-deploy가 설정 검사를 실행한다")
    void appDeploy_관리설정이있을때만Nginx설정검사() throws IOException {
        String script = Files.readString(APP_DEPLOY_SCRIPT);

        assertThat(script)
                .contains("nginx_site_conf=/etc/nginx/conf.d/masiton.click.conf")
                .contains("command -v nginx >/dev/null 2>&1 && [ -f \"$nginx_site_conf\" ]")
                .contains("[ -f \"$nginx_site_conf\" ] && systemctl is-active --quiet nginx")
                .contains("Nginx 설정 smoke 스킵: 설치된 masit-on site 설정이 없다");
    }

    /**
     * 운영 애플리케이션 포트가 loopback에만 바인딩되고, 그 loopback으로 실제 도달하는
     * 실행·프록시 조건이 함께 유지되는지 고정한다.
     *
     * 백엔드는 {@code --network host}로 실행되므로 {@code server.address}가 없으면 호스트의
     * 모든 인터페이스에 붙는다. 8080을 여는 보안 그룹·방화벽 규칙 하나로 Nginx를 건너뛴
     * 인터넷 직결이 성립하고, 인터넷에 공개하지 않기로 한 {@code /internal/**}까지 함께
     * 노출된다(ADR-WEB-005 2절, ADR-WEB-003 6.5절 상태 확인 경로).
     *
     * YAML에서 placeholder를 뺀 것만으로는 부족하다. OS 환경 변수 property source가 패키징된
     * 프로파일보다 우선하므로 {@code -e SERVER_ADDRESS=0.0.0.0} 하나로 값이 뒤집힌다. 바인딩을
     * 넓히는 설정 이름이 컨테이너에 전달되지 않는 것까지 확인해야 ADR-WEB-005 3절의
     * "{@code SERVER_ADDRESS}·{@code SERVER_PORT}를 전달하지 않는다"가 실제로 성립한다.
     * 포트도 같은 이유로 함께 본다.
     * 주소만 고정하고 {@code SERVER_PORT}로 포트가 바뀌면 Nginx upstream과 어긋난다.
     *
     * {@code PASSED_ENV}는 이 스크립트가 통일해 쓰는 {@code -e NAME} 형식만 뽑는다.
     * {@code --env NAME}이나 {@code -eNAME}으로 바꿔 전달하면 이 단정을 통과한다. 전달 형식을
     * 바꿀 때는 {@code PASSED_ENV}를 같이 넓힌다.
     *
     * 반대 방향의 회귀도 같이 막는다. 바인딩만 loopback으로 좁히고 실행 네트워크를 브리지로
     * 바꾸거나 Nginx upstream·상태 지표 수집 대상을 다른 주소로 옮기면, 인터넷 차단은
     * 유지되지만 정상 요청과 상태 확인이 전부 끊긴다. 각 지점을 한 테스트에서 대조한다.
     */
    @Test
    @DisplayName("운영 애플리케이션 포트를 loopback에만 바인딩하고 Nginx·상태 지표가 같은 주소로 도달한다")
    void 운영_애플리케이션포트를loopback에고정하고내부경로만도달시킨다() throws IOException {
        // given
        String script = Files.readString(APP_RUN_SCRIPT);
        String backend = stripComments(backendBranch(script));
        String frontend = stripComments(frontendBranch(script));
        String site = Files.readString(NGINX_SITE);

        // when
        PropertySource<?> prod = loadProdProfile();
        Set<String> backendEnv = names(PASSED_ENV, dockerRunCommand(backend));

        // then
        assertThat(prod.getProperty("server.address"))
                .as("운영 프로파일이 바인딩 주소를 선언하지 않으면 호스트의 모든 인터페이스에 붙는다")
                .isEqualTo(LOOPBACK);
        assertThat(backendEnv)
                .as("환경 변수는 프로파일보다 우선하므로 전달하면 YAML의 loopback 고정이 무효가 된다")
                .doesNotContainAnyElementsOf(BINDING_WIDENING_ENV);

        assertThat(dockerRunCommand(backend))
                .as("host 네트워크가 아니면 컨테이너 안의 %s가 호스트 loopback과 달라 Nginx가 도달하지 못한다", LOOPBACK)
                .contains("--network host");
        assertThat(frontend)
                .as("HOSTNAME이 없으면 Next.js standalone 서버는 모든 인터페이스에 붙는다")
                .contains("export HOSTNAME=" + LOOPBACK);
        assertThat(dockerRunCommand(frontend))
                .as("HOSTNAME을 export만 하고 전달하지 않으면 컨테이너가 받지 못한다")
                .contains("--network host")
                .contains("-e HOSTNAME");

        assertThat(site)
                .as("Nginx upstream이 바인딩 주소·포트와 같아야 정상 요청이 통과한다")
                .contains("server " + LOOPBACK + ":8080;")
                .contains("server " + LOOPBACK + ":3000;");

        assertThat(stripComments(Files.readString(HEALTH_METRICS_SCRIPT)))
                .as("상태 지표 수집도 같은 loopback으로 호출해야 알람이 유지된다")
                .contains("HEALTH_BASE:-http://" + LOOPBACK + ":8080");
    }

    private static PropertySource<?> loadProdProfile() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application-prod.yml", new FileSystemResource(PROD_PROFILE));
        assertThat(sources).as("운영 프로파일은 정확히 하나의 YAML 문서여야 한다").hasSize(1);
        return sources.get(0);
    }

    /**
     * prod가 기본값과 함께 재선언한 키는 공통 계층 선언이 이기지 못하므로 요구 대상에서 뺀다.
     */
    private static Set<String> requiredEnvironmentNames(String common, String prod) {
        Set<String> required = names(DEFAULTLESS_PLACEHOLDER, prod);
        Set<String> overriddenByProd = names(PLACEHOLDER_WITH_DEFAULT, prod);
        for (String name : names(DEFAULTLESS_PLACEHOLDER, common)) {
            if (!overriddenByProd.contains(name)) {
                required.add(name);
            }
        }
        return required;
    }

    private static Set<String> names(Pattern pattern, String text) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            names.add(normalize(matcher.group(1)));
        }
        return names;
    }

    private static Set<String> methodPermitAllRoutes(String securityConfiguration) {
        Set<String> routes = new TreeSet<>();
        Matcher matcher = METHOD_PERMIT_ALL.matcher(securityConfiguration);
        while (matcher.find()) {
            String method = matcher.group(1);
            Matcher pathMatcher = STRING_LITERAL.matcher(matcher.group(2));
            while (pathMatcher.find()) {
                routes.add(method + " " + pathMatcher.group(1));
            }
        }
        return routes;
    }

    private static List<NginxLocation> nginxLocations(String site) {
        List<NginxLocation> locations = new ArrayList<>();
        Matcher matcher = LOCATION.matcher(site);
        while (matcher.find()) {
            locations.add(new NginxLocation(matcher.group(1).trim(), matcher.group(2)));
        }
        assertThat(locations).as("TLS server의 location을 찾지 못했다").isNotEmpty();
        return locations;
    }

    /** 들여쓰기와 무관하게 최상위 블록(server)의 직계 auth_request만 찾는다. */
    private static boolean hasServerLevelAuthRequest(String site) {
        int depth = 0;
        for (String rawLine : site.split("\\R")) {
            String line = rawLine.replaceFirst("#.*$", "").trim();
            if (depth == 1 && line.matches("auth_request\\b.*")) {
                return true;
            }
            depth += count(line, '{') - count(line, '}');
        }
        return false;
    }

    private static String httpsApplicationServer(String site) {
        int searchFrom = 0;
        while (true) {
            int start = site.indexOf("server {", searchFrom);
            if (start < 0) {
                throw new AssertionError("masiton.click HTTPS server 블록을 찾지 못했다");
            }
            int depth = 0;
            for (int index = start; index < site.length(); index++) {
                char current = site.charAt(index);
                if (current == '{') {
                    depth++;
                } else if (current == '}' && --depth == 0) {
                    String server = site.substring(start, index + 1);
                    if (server.contains("listen 443 ssl;") && server.contains("server_name masiton.click;")) {
                        return server;
                    }
                    searchFrom = index + 1;
                    break;
                }
            }
        }
    }

    private static int serverLevelDirectiveCount(String server, String directive) {
        int depth = 0;
        int matches = 0;
        for (String rawLine : server.split("\\R")) {
            String line = rawLine.replaceFirst("#.*$", "").trim();
            if (depth == 1 && line.equals(directive)) {
                matches++;
            }
            depth += count(line, '{') - count(line, '}');
        }
        return matches;
    }

    private static int count(String value, char expected) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == expected) {
                count++;
            }
        }
        return count;
    }

    private static NginxLocation locationBySelector(List<NginxLocation> locations, String selector) {
        return locations.stream()
                .filter(location -> location.selector().equals(selector))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nginx location을 찾지 못했다: " + selector));
    }

    /** exact, {@code ^~} prefix, 선언 순서 regex, 일반 prefix 순으로 Nginx location 선택을 모사한다. */
    private static NginxLocation resolveLocation(List<NginxLocation> locations, String path) {
        for (NginxLocation location : locations) {
            if (location.selector().startsWith("= ") && path.equals(location.selector().substring(2))) {
                return location;
            }
        }

        NginxLocation longestPrefix = null;
        for (NginxLocation location : locations) {
            String selector = location.selector();
            String prefix = selector.startsWith("^~ ") ? selector.substring(3)
                    : isPlainPrefix(selector) ? selector : null;
            if (prefix != null && path.startsWith(prefix)
                    && (longestPrefix == null || prefix.length() > prefixOf(longestPrefix).length())) {
                longestPrefix = location;
            }
        }
        if (longestPrefix != null && longestPrefix.selector().startsWith("^~ ")) {
            return longestPrefix;
        }

        for (NginxLocation location : locations) {
            String selector = location.selector();
            if (selector.startsWith("~ ") && Pattern.compile(selector.substring(2)).matcher(path).find()) {
                return location;
            }
            if (selector.startsWith("~* ")
                    && Pattern.compile(selector.substring(3), Pattern.CASE_INSENSITIVE).matcher(path).find()) {
                return location;
            }
        }
        if (longestPrefix != null) {
            return longestPrefix;
        }
        throw new AssertionError("Nginx location과 일치하지 않는 경로: " + path);
    }

    private static boolean isPlainPrefix(String selector) {
        return selector.startsWith("/");
    }

    private static String prefixOf(NginxLocation location) {
        return location.selector().startsWith("^~ ") ? location.selector().substring(3) : location.selector();
    }

    /** Spring relaxed binding이 환경 변수로 해석하는 형태로 맞춘다. */
    private static String normalize(String propertyName) {
        return propertyName.replace('.', '_').replace('-', '_').toUpperCase();
    }

    private static String backendBranch(String script) {
        Matcher matcher = BACKEND_BRANCH.matcher(script);
        assertThat(matcher.find()).as("app-run.sh에서 backend 분기를 찾지 못했다").isTrue();
        return matcher.group(1);
    }

    private static String frontendBranch(String script) {
        Matcher matcher = FRONTEND_BRANCH.matcher(script);
        assertThat(matcher.find()).as("app-run.sh에서 frontend 분기를 찾지 못했다").isTrue();
        return matcher.group(1);
    }

    private static String dockerRunCommand(String branch) {
        Matcher matcher = DOCKER_RUN.matcher(branch);
        assertThat(matcher.find()).as("분기에서 docker run 명령을 찾지 못했다").isTrue();
        return matcher.group(1);
    }

    private static String stripComments(String shell) {
        return shell.replaceAll("(?m)^\\s*#.*$", "");
    }

    private static String callbackUrl(String branch) {
        Matcher matcher = CALLBACK_EXPORT.matcher(branch);
        assertThat(matcher.find()).as("backend 분기가 콜백 URL을 export하지 않는다").isTrue();
        return matcher.group(1);
    }

    private static String pathOf(String url) {
        int schemeEnd = url.indexOf("://");
        int pathStart = url.indexOf('/', schemeEnd + 3);
        return pathStart < 0 ? "" : url.substring(pathStart);
    }

    /**
     * location 본문을 location 자신의 들여쓰기 깊이에서 닫히는 {@code }}로 끊는다. 임의의
     * {@code \n\s*}}로 끊으면 {@code limit_except} 같은 중첩 블록의 닫는 괄호가 경계가 되어
     * 그 뒤에 추가된 지시어가 검사에서 사라진다.
     */
    private static String gateExcludedLocationBody(String site) {
        Matcher matcher = Pattern.compile(
                        Pattern.quote("location = " + CALLBACK_PATH + " {") + "(.*?)\\n {4}}",
                        Pattern.DOTALL)
                .matcher(site);
        assertThat(matcher.find()).as("gate 제외 location 본문을 찾지 못했다").isTrue();
        return matcher.group(1);
    }

    private record ApiRoute(String method, String securityPattern, String examplePath) {
    }

    private record NginxLocation(String selector, String body) {
    }
}
