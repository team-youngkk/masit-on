package com.masiton.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ASG·Blue-Green 런타임 계약")
class RuntimeDeploymentContractTest {

    private static final Path APP_RUN = Path.of("deploy/scripts/app-run.sh");
    private static final Path APP_DEPLOY = Path.of("deploy/scripts/app-deploy.sh");
    private static final Path BOOTSTRAP = Path.of("deploy/scripts/instance-bootstrap.sh");
    private static final Path HEALTH = Path.of("deploy/scripts/runtime-health.sh");
    private static final Path CI = Path.of(".github/workflows/ci.yml");
    private static final Path NGINX = Path.of("deploy/nginx/masiton.click.conf");
    private static final Path NGINX_INSTALL = Path.of("deploy/scripts/nginx-install.sh");
    private static final Path ALB = Path.of("infra/production/terraform/alb.tf");
    private static final Path CODEDEPLOY = Path.of("infra/production/terraform/codedeploy.tf");
    private static final Path APPSPEC = Path.of("deploy/codedeploy/appspec.yml");
    private static final Path AFTER_INSTALL = Path.of("deploy/codedeploy/hooks/after-install.sh");
    private static final Path VALIDATE_SERVICE = Path.of("deploy/codedeploy/hooks/validate-service.sh");

    @Test
    @DisplayName("Redis는 환경 변수와 SSM을 선택적으로 사용하고 단일 EC2 기본값을 유지한다")
    void redisEndpoint_환경변수와SSM을선택적으로사용하고_loopback기본값을유지한다() throws IOException {
        String script = Files.readString(APP_RUN);

        assertThat(script)
                .contains("REQUIRE_SHARED_REDIS")
                .contains("REDIS_HOST=\"${REDIS_HOST:-$(optional_param /masiton/redis/host)}\"")
                .contains("REDIS_PORT=\"${REDIS_PORT:-$(optional_param /masiton/redis/port)}\"")
                .contains("REDIS_HOST=\"${REDIS_HOST:-127.0.0.1}\"")
                .contains("REDIS_PORT=\"${REDIS_PORT:-6379}\"")
                .contains("-e REDIS_HOST -e REDIS_PORT");
    }

    @Test
    @DisplayName("새 인스턴스 bootstrap은 공유 Redis를 기본으로 앱·Nginx를 같은 stage에서 멱등 실행한다")
    void bootstrap_동일stage에서설치스크립트를재실행한다() throws IOException {
        String script = Files.readString(BOOTSTRAP);

        assertThat(script)
                .contains("set -euo pipefail")
                .contains("redis-install.sh")
                .contains("app-deploy.sh")
                .contains("nginx-install.sh")
                .contains("runtime-health.sh")
                .contains("install -d -m 0750 /opt/masiton/bin /opt/masiton/etc")
                .contains("INSTALL_LOCAL_REDIS");
    }

    @Test
    @DisplayName("배포 후 health 실패는 이전 이미지 참조로 rollback하고 성공 시 경계를 해제한다")
    void deploy_배포후health실패시rollback하고_성공시trap을해제한다() throws IOException {
        String script = Files.readString(APP_DEPLOY);
        int trap = script.indexOf("trap rollback ERR");
        int health = script.lastIndexOf("\"$OPT_DIR/bin/runtime-health.sh\"");
        int release = script.lastIndexOf("trap - ERR");

        assertThat(script)
                .contains("rollback_enabled=yes", "previous", "systemctl restart masiton-backend.service")
                .contains("REDIS_HOST")
                .contains("redis_cli()")
                .contains("--network host");
        assertThat(trap).isGreaterThan(0);
        assertThat(health).isGreaterThan(trap);
        assertThat(release).isGreaterThan(health);
    }

    @Test
    @DisplayName("기존 단일 EC2 SSM 경로와 차세대 배포 입력 계약을 함께 보존한다")
    void ci_기존INSTANCE경로와_CodeDeploy_ASG입력을함께보존한다() throws IOException {
        String workflow = Files.readString(CI);

        assertThat(workflow)
                .contains("INSTANCE_ID:")
                .contains("--instance-ids \"$INSTANCE_ID\"")
                .contains("deployment_target")
                .contains("CODEDEPLOY_APPLICATION")
                .contains("CODEDEPLOY_DEPLOYMENT_GROUP")
                .contains("if: env.DEPLOYMENT_TARGET == 'instance'")
                .contains("if: env.DEPLOYMENT_TARGET == 'codedeploy'")
                .contains("environment: production")
                .contains("id-token: write");
    }

    @Test
    @DisplayName("CodeDeploy revision은 appspec hook과 안전한 image tag 파일을 사용한다")
    void codeDeploy_revision은appspec과안전한imageTag전달을사용한다() throws IOException {
        String appspec = Files.readString(APPSPEC);
        String hook = Files.readString(AFTER_INSTALL);
        String validate = Files.readString(VALIDATE_SERVICE);
        String workflow = Files.readString(CI);

        assertThat(appspec)
                .contains("version: 0.0")
                .contains("source: stage")
                .contains("destination: /opt/masiton/revision")
                .contains("stage/codedeploy/hooks/after-install.sh")
                .contains("stage/codedeploy/hooks/validate-service.sh");
        assertThat(hook)
                .contains("revision.env")
                .contains("${#image_tag}")
                .contains("instance-bootstrap.sh")
                .contains("[!0-9a-f]");
        assertThat(validate).contains("runtime-health.sh");
        assertThat(workflow)
                .contains("codedeploy-revision.tar.gz")
                .contains("revision.env")
                .contains("aws s3 cp codedeploy-revision.tar.gz")
                .contains("aws deploy create-deployment")
                .contains("aws deploy get-deployment")
                .contains("CODEDEPLOY_S3_BUCKET")
                .contains("codedeploy_s3_bucket || ''")
                .contains("--s3-location")
                .contains("for _ in $(seq 1 120)");
    }

    @Test
    @DisplayName("health 계약과 기존 Nginx 공개 경계를 함께 유지한다")
    void health와Nginx_내부경계를함께유지한다() throws IOException {
        String health = Files.readString(HEALTH);
        String nginx = Files.readString(NGINX);
        String nginxInstall = Files.readString(NGINX_INSTALL);
        String alb = Files.readString(ALB);
        String codeDeploy = Files.readString(CODEDEPLOY);

        assertThat(health)
                .contains("/internal/health/live")
                .contains("/internal/health/ready")
                .contains("/internal/health/dependencies")
                .contains("\"db\", \"redis\", \"mail\"")
                .contains("127.0.0.1:3000");
        assertThat(nginx)
                .contains("location ^~ /internal/")
                .contains("return 404")
                .contains("/_masiton/alb-health")
                .contains("proxy_pass http://masiton_backend/internal/health/ready");
        assertThat(nginxInstall)
                .contains("real_ip_header X-Forwarded-For")
                .contains("set_real_ip_from")
                .contains("NGINX_TRUSTED_PROXY_CIDRS");
        assertThat(alb)
                .contains("protocol    = var.app_protocol")
                .contains("protocol            = var.app_protocol");
        assertThat(codeDeploy)
                .contains("action = \"COPY_AUTO_SCALING_GROUP\"")
                .contains("DEPLOYMENT_STOP_ON_ALARM")
                .contains("local.deployment_alarm_names");
    }
}
