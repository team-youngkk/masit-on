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
    private static final Path TERRAFORM_VARIABLES = Path.of("infra/production/terraform/variables.tf");
    private static final Path TERRAFORM_DATA = Path.of("infra/production/terraform/data.tf");
    private static final Path TERRAFORM_ASG = Path.of("infra/production/terraform/asg.tf");
    private static final Path TERRAFORM_IAM = Path.of("infra/production/terraform/iam.tf");
    private static final Path MONITORING = Path.of("infra/production/terraform/monitoring.tf");
    private static final Path REDIS_INSTANCE = Path.of("infra/production/terraform-redis/instance.tf");
    private static final Path REDIS_USER_DATA = Path.of("infra/production/terraform-redis/templates/redis-user-data.sh.tftpl");
    private static final Path REDIS_README = Path.of("infra/production/terraform-redis/README.md");
    private static final Path PRODUCTION_README = Path.of("infra/production/README.md");
    private static final Path CI_ADR = Path.of("docs/07-adr/platform/ci-001-github-actions-quality-gate.md");
    private static final Path DEPLOYMENT_ADR = Path.of("docs/07-adr/platform/deploy-005-asg-blue-green-rollout.md");
    private static final Path ADR_BACKLOG = Path.of("docs/07-adr/adr-backlog.md");
    private static final Path CLEANUP_RUNBOOK = Path.of("docs/08-planning/blue-green-cleanup-runbook.md");
    private static final Path TROUBLESHOOTING = Path.of("docs/troubleshooting/pr-228-asg-replacement-deployment-review.md");
    private static final Path APPSPEC = Path.of("deploy/codedeploy/appspec.yml");
    private static final Path AFTER_INSTALL = Path.of("deploy/codedeploy/hooks/after-install.sh");
    private static final Path VALIDATE_SERVICE = Path.of("deploy/codedeploy/hooks/validate-service.sh");

    @Test
    @DisplayName("Redis는 환경 변수와 SSM을 사용해 배포 고도화 endpoint를 주입한다")
    void redisEndpoint_환경변수와SSM으로공유endpoint를주입한다() throws IOException {
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
    @DisplayName("새 인스턴스 bootstrap은 공유 Redis endpoint를 사용해 앱·Nginx를 같은 stage에서 멱등 실행한다")
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
        int firstActiveInstall = script.indexOf("install -d -m 0755 \"$OPT_DIR/bin\" \"$OPT_DIR/etc\"");
        int health = script.lastIndexOf("\"$OPT_DIR/bin/runtime-health.sh\"");
        int release = script.lastIndexOf("trap - ERR");

        assertThat(script)
                .contains("rollback_enabled=yes", "previous", "backup_asset", "restore_asset", ".missing")
                .contains("local original_exit_code=$?", "rollback_failed=yes", "return \"$original_exit_code\"")
                .contains("systemctl restart masiton-backend.service")
                .contains("REDIS_HOST")
                .contains("redis_cli()")
                .contains("--network host");
        assertThat(trap).isGreaterThan(0);
        assertThat(firstActiveInstall).isGreaterThan(trap);
        assertThat(health).isGreaterThan(trap);
        assertThat(release).isGreaterThan(health);
    }

    @Test
    @DisplayName("CodeDeploy timeout과 취소는 배포를 중지하고 terminal 상태까지 확인한다")
    void codeDeploy_대기timeout과취소시중지하고_terminal상태까지확인한다() throws IOException {
        String workflow = Files.readString(CI);
        String iam = Files.readString(TERRAFORM_IAM);

        assertThat(workflow)
                .contains("stop-deployment")
                .contains("--auto-rollback-enabled")
                .contains("trap on_exit EXIT")
                .contains("trap on_signal INT TERM")
                .contains("for _ in $(seq 1 270)")
                .contains("for _ in $(seq 1 60)")
                .contains("Succeeded|Failed|Stopped")
                .contains("stop_failed=false", "return 1", "::error::CodeDeploy")
                .contains("cancel-in-progress: ${{ github.event_name == 'pull_request' }}")
                .contains("codedeploy-cancel-cleanup")
                .contains("deployment_id_key")
                .contains("aws s3api put-object")
                .contains("aws s3 cp \"s3://${CODEDEPLOY_S3_BUCKET}/${deployment_id_key}\"")
                .contains("for _ in $(seq 1 24)");
        assertThat(workflow).doesNotContain("actions/download-artifact@v4");
        assertThat(workflow.indexOf("aws s3api put-object"))
                .isGreaterThan(workflow.indexOf("aws deploy create-deployment"))
                .isLessThan(workflow.indexOf("for _ in $(seq 1 270)"));
        assertThat(iam).contains("codedeploy:StopDeployment");
    }

    @Test
    @DisplayName("Terraform은 ALB IGW 경로와 선언된 app 배치 의도, 단일 target group을 검증한다")
    void terraform_서브넷route와_단일targetGroup을검증한다() throws IOException {
        String data = Files.readString(TERRAFORM_DATA);
        String variables = Files.readString(TERRAFORM_VARIABLES);
        String asg = Files.readString(TERRAFORM_ASG);
        String alb = Files.readString(ALB);
        String monitoring = Files.readString(MONITORING);

        // ALB는 IGW 기본 경로를 요구하고, app은 선언한 배치 의도와 실제 route가
        // 어긋날 때 plan에서 실패한다. 배포 고도화 영향 검토 6.6절이 앱을 public
        // subnet에 두는 구성을 전제로 비용을 산정했으므로 방향을 코드에 굳히지 않는다.
        assertThat(data)
                .contains("data \"aws_route_table\" \"alb\"")
                .contains("data \"aws_route_table\" \"app\"")
                .contains("0.0.0.0/0")
                .contains("^igw-")
                .contains("alb_subnet_ids의 route table에는")
                .contains("var.app_subnet_is_private")
                .contains("nat_gateway_id")
                .contains("0.0.0.0/0 -> NAT gateway");
        assertThat(variables)
                .contains("variable \"app_subnet_is_private\"")
                .contains("variable \"acm_certificate_arn\"")
                .contains("nullable    = false")
                .contains("acm_certificate_arn은 유효한 ACM certificate ARN이어야 한다.");
        assertThat(asg)
                .contains("network_interfaces")
                .contains("associate_public_ip_address = !var.app_subnet_is_private");
        assertThat(alb).doesNotContain("resource \"aws_lb_target_group\" \"green\"");
        assertThat(monitoring).doesNotContain("green_unhealthy");
    }

    @Test
    @DisplayName("Redis AOF 데이터 volume은 인스턴스 교체와 분리된 수명주기로 mount한다")
    void redis_데이터volume을_인스턴스교체와분리한다() throws IOException {
        String instance = Files.readString(REDIS_INSTANCE);
        String userData = Files.readString(REDIS_USER_DATA);

        assertThat(instance)
                .contains("aws_ebs_volume\" \"redis_data")
                .contains("prevent_destroy = true")
                .contains("aws_volume_attachment\" \"redis_data")
                .contains("data.aws_subnet.redis.availability_zone");
        assertThat(userData)
                .contains("/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_")
                .contains("DATA_VOLUME_SERIAL=\"$${DATA_VOLUME_ID//-/}\"")
                .contains("nvme-Amazon_Elastic_Block_Store_$${DATA_VOLUME_SERIAL}")
                .doesNotContain("DATA_VOLUME_SERIAL=\"${DATA_VOLUME_ID//-/}\"")
                .contains("/opt/masiton/redis/data")
                .contains("mkfs.ext4")
                .contains("/etc/fstab");
        assertThat(Files.readString(REDIS_README))
                .contains("## 기존 Redis 상태가 있을 때 최초 전환")
                .contains("rsync -aHAX --numeric-ids")
                .contains("appendonly.aof.manifest")
                .contains("redis-check-aof")
                .contains("known-fixture-key")
                .contains("terraform import aws_ebs_volume.redis_data");
        assertThat(Files.readString(CI))
                .contains("Terraform 렌더링 계약")
                .contains("infra/production/terraform-redis/tests/template-render")
                .contains("hashicorp/terraform:1.6.6 test");
    }

    @Test
    @DisplayName("배포 구현·ADR·runbook·troubleshooting 문서는 같은 취소와 Redis 승인 경계를 설명한다")
    void 문서계약은_취소정리와Redis승인경계를_같이설명한다() throws IOException {
        assertThat(Files.readString(PRODUCTION_README))
                .contains("deployment ID pointer")
                .contains("StopDeployment")
                .contains("ADR-DEPLOY-005")
                .contains("Accepted")
                .contains("전용 Redis")
                .contains("운영 apply");
        assertThat(Files.readString(CI_ADR))
                .contains("terraform-contract")
                .contains("S3 pointer")
                .contains("CodeDeploy 취소 cleanup");
        assertThat(Files.readString(DEPLOYMENT_ADR))
                .contains("replacement 환경")
                .contains("사설 subnet 전용 Redis")
                .contains("owner 재합의")
                .contains("Accepted 운영 계약");
        assertThat(Files.readString(ADR_BACKLOG))
                .contains("Accepted (2026-08-18")
                .contains("Redis 배치 owner 재합의")
                .contains("운영 전환 전에 남김");
        assertThat(Files.readString(CLEANUP_RUNBOOK))
                .contains("codedeploy-cancel-cleanup")
                .contains("S3 deployment ID pointer");
        assertThat(Files.readString(TROUBLESHOOTING))
                .contains("redis-user-data.tftest.hcl")
                .contains("terraform-contract");
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
