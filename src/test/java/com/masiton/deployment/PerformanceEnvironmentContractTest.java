package com.masiton.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("격리 성능 환경 계약")
class PerformanceEnvironmentContractTest {

    private static final Path APP_USER_DATA = Path.of(
            "infra/performance/terraform/templates/app-user-data.sh.tftpl");

    @Test
    @DisplayName("검증 세션 비활성화 설정을 컨테이너까지 전달하고 자격 증명을 렌더링하지 않는다")
    void 성능환경_검증세션을명시적으로비활성화한다() throws IOException {
        String template = Files.readString(APP_USER_DATA, StandardCharsets.UTF_8);

        assertThat(template)
                .contains("export VERIFICATION_ENABLED=false")
                .contains("-e VERIFICATION_ENABLED")
                .contains("write_secret 'masiton.member.action-mail.from-address' 'perf@example.invalid'")
                .doesNotContain("write_secret 'masiton.security.verification.login-id'")
                .doesNotContain("write_secret 'masiton.security.verification.password-hash'");
    }
}
