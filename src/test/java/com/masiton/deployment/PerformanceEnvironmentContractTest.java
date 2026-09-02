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
    private static final Path LOADGEN_USER_DATA = Path.of(
            "infra/performance/terraform/templates/loadgen-user-data.sh.tftpl");
    private static final Path VARIABLES = Path.of("infra/performance/terraform/variables.tf");
    private static final Path EC2 = Path.of("infra/performance/terraform/ec2.tf");

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

    @Test
    @DisplayName("성능 환경의 실행 아키텍처와 런타임 제한이 운영과 일치한다")
    void 성능환경_실행아키텍처와런타임제한이운영과일치한다() throws IOException {
        String variables = Files.readString(VARIABLES, StandardCharsets.UTF_8);
        String ec2 = Files.readString(EC2, StandardCharsets.UTF_8);
        String appUserData = Files.readString(APP_USER_DATA, StandardCharsets.UTF_8);
        String loadgenUserData = Files.readString(LOADGEN_USER_DATA, StandardCharsets.UTF_8);

        assertThat(variables)
                .contains("description = \"Amazon Linux 2023 x86_64 AMI ID. latest 자동 선택 금지\"")
                .contains("default     = \"t2.micro\"")
                .contains("default     = \"t2.small\"")
                .contains("variable \"k6_amd64_sha256\"")
                .contains("default     = \"295d961ebfca306f295f1133068dcd403a8171c87f387928f5f30b0fbcff858a\"")
                .doesNotContain("arm64")
                .doesNotContain("t4g.");
        assertThat(ec2)
                .contains("instance_type               = var.loadgen_instance_type")
                .contains("k6_amd64_sha256 = var.k6_amd64_sha256")
                .doesNotContain("t4g.");
        assertThat(appUserData)
                .contains("--memory 512m")
                .doesNotContain("--memory 1024m");
        assertThat(loadgenUserData)
                .contains("linux-amd64")
                .contains("${k6_amd64_sha256}")
                .doesNotContain("linux-arm64")
                .doesNotContain("k6_arm64_sha256");
    }
}
