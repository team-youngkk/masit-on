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

@DisplayName("운영 애플리케이션 실행 스크립트")
class AppRunScriptContractTest {

    @Test
    @DisplayName("AI Worker와 Gemini Free Tier 게이트를 SSM에서 읽어 backend 컨테이너에 전달한다")
    void backend_AIWorker설정을컨테이너에전달한다() throws IOException {
        String script = Files.readString(Path.of("deploy/scripts/app-run.sh"));
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
     */
    @Test
    @DisplayName("운영 프로파일이 기본값 없이 요구하는 환경 변수를 모두 컨테이너에 전달한다")
    void backend_기본값없는운영환경변수를모두전달한다() throws IOException {
        String profile = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        String script = Files.readString(Path.of("deploy/scripts/app-run.sh"));

        Set<String> required = new TreeSet<>();
        Matcher matcher = Pattern.compile("\\$\\{([A-Z0-9_]+)}").matcher(profile);
        while (matcher.find()) {
            required.add(matcher.group(1));
        }

        assertThat(required).contains("YOUTUBE_WEBHOOK_CALLBACK_URL");
        assertThat(required).allSatisfy(name -> assertThat(script)
                .as("%s를 backend 컨테이너에 전달해야 한다", name)
                .contains("-e " + name));
    }
}
