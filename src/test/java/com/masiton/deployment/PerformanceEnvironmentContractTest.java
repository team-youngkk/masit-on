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
    @DisplayName("성능 환경에서도 검증 참여자 설정과 자격 증명을 주입하지 않는다")
    void 성능환경_검증참여자설정을주입하지않는다() throws IOException {
        String template = Files.readString(APP_USER_DATA, StandardCharsets.UTF_8);

        assertThat(template)
                .contains("write_secret 'masiton.member.action-mail.from-address' 'perf@example.invalid'")
                .doesNotContain("VERIFICATION_")
                .doesNotContain("write_secret 'masiton.security.verification.login-id'")
                .doesNotContain("write_secret 'masiton.security.verification.password-hash'")
                .doesNotContain("/masiton/access/verification-");
    }
}
