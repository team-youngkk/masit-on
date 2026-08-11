package com.masiton.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("운영 애플리케이션 실행 스크립트")
class AppRunScriptContractTest {

    @Test
    @DisplayName("AI Worker 활성화와 quota 설정을 SSM에서 읽어 backend 컨테이너에 전달한다")
    void backend_AIWorker설정을컨테이너에전달한다() throws IOException {
        String script = Files.readString(Path.of("deploy/scripts/app-run.sh"));
        String secretsRenderer = Files.readString(Path.of("deploy/scripts/app-secrets-render.sh"));

        assertThat(script)
                .contains("optional_param /masiton/ai/worker/enabled")
                .contains("optional_param /masiton/ai/worker/provider-quota-limit")
                .contains("optional_param /masiton/ai/worker/application-quota-limit")
                .contains("optional_param /masiton/ai/worker/quota-window")
                .contains("-e AI_WORKER_ENABLED -e AI_WORKER_PROVIDER_QUOTA_LIMIT")
                .contains("-e AI_WORKER_APPLICATION_QUOTA_LIMIT -e AI_WORKER_QUOTA_WINDOW");
        assertThat(secretsRenderer)
                .contains("get-parameters-by-path")
                .contains("/masiton/ai/temporary-input/keys")
                .contains("masiton.ai.temporary-input.keys.$key_id");
    }
}
