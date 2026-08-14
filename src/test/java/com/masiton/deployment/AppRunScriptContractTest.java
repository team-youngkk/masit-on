package com.masiton.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("운영 배포 산출물 계약")
class AppRunScriptContractTest {

    private static final Path APP_RUN_SCRIPT = Path.of("deploy/scripts/app-run.sh");
    private static final Path APP_DEPLOY_SCRIPT = Path.of("deploy/scripts/app-deploy.sh");
    private static final Path NGINX_SITE = Path.of("deploy/nginx/masiton.click.conf");
    private static final Path NGINX_MAIN = Path.of("deploy/nginx/nginx.conf");
    private static final Path COMMON_PROFILE = Path.of("src/main/resources/application.yml");
    private static final Path PROD_PROFILE = Path.of("src/main/resources/application-prod.yml");

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

    /** 실제로 컨테이너를 띄우는 명령. 이 범위 밖의 {@code -e}는 전달로 세지 않는다. */
    private static final Pattern DOCKER_RUN = Pattern.compile("exec\\s+/usr/bin/docker\\s+run\\b(.*)", Pattern.DOTALL);

    /** 앞에 단어·하이픈이 붙은 {@code --volume-e} 같은 토큰을 {@code -e}로 오인하지 않는다. */
    private static final Pattern PASSED_ENV = Pattern.compile("(?<![\\w-])-e\\s+([A-Z0-9_]+)\\b");
    private static final Pattern EXPORTED_ENV = Pattern.compile("(?<![\\w-])export\\s+([A-Z0-9_]+)\\b");

    private static final Pattern CALLBACK_EXPORT = Pattern.compile(
            "(?<![\\w-])export\\s+YOUTUBE_WEBHOOK_CALLBACK_URL=\"?([^\"\\s]+)\"?");

    /** 이 저장소의 Nginx 파일은 server 지시어를 4칸, location 안 지시어를 8칸 들여쓴다. */
    private static final Pattern SERVER_LEVEL_AUTH_REQUEST = Pattern.compile("^ {4}auth_request\\b", Pattern.MULTILINE);

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
     * 유효하다. 제외를 허용하는 조건은 ADR-DEPLOY-003 4.3절, 제외 경로 목록과 제한값은
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
        assertThat(SERVER_LEVEL_AUTH_REQUEST.matcher(site).find())
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
                .contains("limit_except GET POST")
                .contains("client_max_body_size 128k;");
        assertThat(body)
                .as("생략하면 Nginx 기본 60s가 적용돼 상위 location보다 오래 커넥션을 점유한다")
                .contains("proxy_connect_timeout 5s;")
                .contains("proxy_send_timeout 30s;")
                .contains("proxy_read_timeout 30s;");

        assertThat(Files.readString(NGINX_MAIN))
                .as("limit_req zone은 http 컨텍스트에 선언해야 한다")
                .contains("limit_req_zone $binary_remote_addr zone=masiton_ungated:");
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

    /** Spring relaxed binding이 환경 변수로 해석하는 형태로 맞춘다. */
    private static String normalize(String propertyName) {
        return propertyName.replace('.', '_').replace('-', '_').toUpperCase();
    }

    private static String backendBranch(String script) {
        Matcher matcher = BACKEND_BRANCH.matcher(script);
        assertThat(matcher.find()).as("app-run.sh에서 backend 분기를 찾지 못했다").isTrue();
        return matcher.group(1);
    }

    private static String dockerRunCommand(String branch) {
        Matcher matcher = DOCKER_RUN.matcher(branch);
        assertThat(matcher.find()).as("backend 분기에서 docker run 명령을 찾지 못했다").isTrue();
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
}
