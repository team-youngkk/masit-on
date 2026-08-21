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
    private static final Path HEALTH_METRICS = Path.of("deploy/scripts/health-metrics.sh");
    private static final Path CLOUDWATCH_AGENT = Path.of("deploy/cloudwatch/amazon-cloudwatch-agent.json");
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
    private static final Path TERRAFORM_TFVARS_EXAMPLE = Path.of("infra/production/terraform/terraform.tfvars.example");
    private static final Path APPSPEC = Path.of("deploy/codedeploy/appspec.yml");
    private static final Path AFTER_INSTALL = Path.of("deploy/codedeploy/hooks/after-install.sh");
    private static final Path VALIDATE_SERVICE = Path.of("deploy/codedeploy/hooks/validate-service.sh");
    private static final Path MIGRATION_PLAN = Path.of("docs/05-specs/data/migration-plan.md");
    private static final Path DEPLOYMENT_IMPACT_REVIEW = Path.of("docs/08-planning/deployment-hardening-impact-review.md");
    private static final String TERRAFORM_IMAGE =
            "hashicorp/terraform@sha256:9a42ea97ea25b363f4c65be25b9ca52b1e511ea5bf7d56050a506ad2daa7af9d";
    private static final Path PLANNING_README = Path.of("docs/08-planning/README.md");
    private static final Path POST_CUTOVER_BASELINE = Path.of("docs/08-planning/post-cutover-runtime-baseline.md");
    private static final Path REDIS_RECOVERY_RUNBOOK = Path.of("docs/08-planning/redis-recovery-runbook.md");
    private static final Path M2_PROVISIONING_RECORD = Path.of("docs/08-planning/m2-provisioning-record.md");

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
        String variables = Files.readString(TERRAFORM_VARIABLES);
        String deploy = section(workflow, "  deploy:", "  # GitHub 취소");
        String cleanup = workflow.substring(workflow.indexOf("  codedeploy-cancel-cleanup:"));

        assertThat(deploy)
                .contains("ref: ${{ env.IMAGE_TAG }}")
                .contains("actions/checkout@v4")
                .contains("trap on_exit EXIT")
                .contains("trap on_signal INT TERM")
                .contains("for _ in $(seq 1 270)")
                .contains("for _ in $(seq 1 60)")
                .contains("stop_failed=false", "return 1", "::error::CodeDeploy")
                .contains("CODEDEPLOY_SEED_ASG")
                .contains("CODEDEPLOY_SEED_ASG: masiton-prod-blue-asg")
                .contains("get-deployment-group")
                .contains("terminateBlueInstancesOnDeploymentSuccess.action")
                .contains("TERMINATE 배포를 차단했다")
                .contains("TERMINATE 배포는 Terraform seed 이름이 고정된 production deployment group에서만 허용한다")
                .contains("current_asg_count")
                .contains("[ \"$current_asg\" = \"$CODEDEPLOY_SEED_ASG\" ]")
                .contains("aws s3api put-object");
        assertThat(variables)
                .contains("variable \"codedeploy_deployment_wait_minutes\"")
                .contains("default     = 15")
                .contains(">= 1")
                .contains("<= 15")
                .contains("floor(var.codedeploy_deployment_wait_minutes)")
                .contains("variable \"codedeploy_termination_enabled\"")
                .contains("default     = false");
        assertThat(cleanup)
                .contains("stop-deployment")
                .contains("--auto-rollback-enabled")
                .contains("Succeeded|Failed|Stopped")
                .contains("codedeploy-cancel-cleanup")
                .contains("deployment_id_key")
                .contains("aws s3 cp \"s3://${CODEDEPLOY_S3_BUCKET}/${deployment_id_key}\"")
                .contains("aws deploy list-deployments")
                .contains("aws deploy batch-get-deployments")
                .contains("lookup_completed")
                .contains("steps.lookup.outputs.resolved")
                .contains("for _ in $(seq 1 24)");
        int unresolvedStart = cleanup.indexOf("if [ -z \"$matched\" ]; then");
        int unresolvedEnd = cleanup.indexOf("case \"$matched\"", unresolvedStart);
        assertThat(unresolvedStart).isGreaterThan(0);
        assertThat(unresolvedEnd).isGreaterThan(unresolvedStart);
        assertThat(cleanup.substring(unresolvedStart, unresolvedEnd))
                .contains("원격 CodeDeploy 상태가 미확정")
                .contains("exit 1")
                .doesNotContain("exit 0")
                .doesNotContain("배포가 생성되기 전에 취소됐을 수 있다");
        assertThat(workflow).contains("cancel-in-progress: ${{ github.event_name == 'pull_request' }}");
        assertThat(workflow).doesNotContain("actions/download-artifact@v4");
        assertThat(workflow.indexOf("aws s3api put-object"))
                .isGreaterThan(workflow.indexOf("aws deploy create-deployment"))
                .isLessThan(workflow.indexOf("for _ in $(seq 1 270)"));
        assertThat(iam)
                .contains("codedeploy:StopDeployment")
                .contains("actions   = [\"codedeploy:ListDeployments\"]\n    resources = [aws_codedeploy_deployment_group.app.arn]")
                .contains("actions   = [\"codedeploy:BatchGetDeployments\"]\n    resources = [aws_codedeploy_deployment_group.app.arn]");
    }

    @Test
    @DisplayName("Terraform은 ALB IGW 경로와 선언된 app 배치 의도, 단일 target group을 검증한다")
    void terraform_서브넷route와_단일targetGroup을검증한다() throws IOException {
        String data = Files.readString(TERRAFORM_DATA);
        String variables = Files.readString(TERRAFORM_VARIABLES);
        String asg = Files.readString(TERRAFORM_ASG);
        String alb = Files.readString(ALB);
        String monitoring = Files.readString(MONITORING);
        String tfvarsExample = Files.readString(TERRAFORM_TFVARS_EXAMPLE);

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
        assertThat(tfvarsExample)
                .contains("subnet-REPLACE_ME_PUBLIC_A")
                .contains("subnet-REPLACE_ME_PUBLIC_C")
                .contains("app_subnet_is_private = false")
                .contains("현재 승인된 운영 예시는 public app subnet이다");
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
                .contains(TERRAFORM_IMAGE + " test")
                .contains("--entrypoint sh \\\n            " + TERRAFORM_IMAGE + " \\\n            -c 'terraform init -backend=false && terraform validate'")
                .doesNotContain("hashicorp/terraform:1.6.6");
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
                .contains("운영 apply")
                .contains("0.0.0.0/0 -> NAT gateway")
                .contains("endpoint-only private 토폴로지는 현재 postcondition에서 지원하지 않는다");
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
                .contains("S3 deployment ID pointer")
                .contains("update-auto-scaling-group")
                .contains("--min-size 0")
                .contains("--desired-capacity 0")
                .contains("ignore_changes = [desired_capacity]")
                .contains("healthy target instance가 seed ASG에 남아 있지 않고 replacement ASG에만 속하는지")
                .contains("seed ASG 자체는 Terraform state와 target group 연결을 유지하므로 삭제하지 않는다");
        assertThat(Files.readString(PRODUCTION_README))
                .contains("desired capacity 0으로 축소한다")
                .contains("ignore_changes = [desired_capacity]")
                .contains("seed ASG의 healthy target이 남아 있지 않은 것을 확인한 뒤");
        assertThat(Files.readString(TERRAFORM_TFVARS_EXAMPLE))
                .contains("blue_desired_capacity=1은 최초")
                .contains("desired_capacity는 ignore_changes 대상이므로 tfvars만 바꾸지 않는다");
        assertThat(Files.readString(TROUBLESHOOTING))
                .contains("redis-user-data.tftest.hcl")
                .contains("terraform-contract");
    }

    @Test
    @DisplayName("Blue-Green 마이그레이션 하위 호환 규칙을 Accepted 데이터 계약으로 고정한다")
    void 문서계약은_BlueGreenMigration하위호환규칙을_Accepted계약으로고정한다() throws IOException {
        String migrationPlan = Files.readString(MIGRATION_PLAN);
        String impactReview = Files.readString(DEPLOYMENT_IMPACT_REVIEW);
        String planningReadme = Files.readString(PLANNING_README);

        assertThat(migrationPlan)
                .contains("### 5.1. Blue-Green 하위 호환 계약")
                .contains("Blue와 green이 같은 RDS를 함께 사용하는 동안")
                .contains("별도 migration으로 수행한다")
                .contains("배포 후보에서 제외하고 데이터 소유자 검토를 다시 받는다");
        assertThat(impactReview)
                .contains("status: ACCEPTED")
                .contains("decision_pending:\n  - 앱 인스턴스 t4g.medium → t4g.small 하향")
                .doesNotContain("decision_pending:\n  - Blue-Green 도입에 따른 마이그레이션 하위 호환 규칙")
                .contains("Flyway 마이그레이션 계획 5.1절")
                .contains("Accepted 데이터 계약");
        assertThat(planningReadme)
                .contains("deployment-hardening-impact-review.md")
                .contains("`ACCEPTED`");
    }

    @Test
    @DisplayName("CodeDeploy 단일 경로와 운영 기본값을 사용한다")
    void ci_CodeDeploy단일경로와운영기본값을사용한다() throws IOException {
        String workflow = Files.readString(CI);
        String deploy = section(workflow, "  deploy:", "  # GitHub 취소");
        String cleanup = workflow.substring(workflow.indexOf("  codedeploy-cancel-cleanup:"));

        assertThat(workflow)
                .doesNotContain("INSTANCE_ID:")
                .doesNotContain("--instance-ids \"$INSTANCE_ID\"")
                .doesNotContain("deployment_target")
                .doesNotContain("DEPLOYMENT_TARGET")
                .doesNotContain("SSM 명령 준비")
                .doesNotContain("SSM으로 배포 실행")
                .contains("CODEDEPLOY_APPLICATION")
                .contains("CODEDEPLOY_DEPLOYMENT_GROUP")
                .contains("codedeploy_application || 'masiton-prod-codedeploy'")
                .contains("codedeploy_deployment_group || 'masiton-prod-deployment-group'")
                .contains("codedeploy_s3_bucket || 'masiton-prod-codedeploy-711457211155'")
                .contains("environment: production")
                .contains("id-token: write");
        assertThat(deploy)
                .contains("name: CodeDeploy revision 패키징")
                .contains("name: CodeDeploy revision 업로드 및 배포")
                .doesNotContain("if: env.DEPLOYMENT_TARGET");
        assertThat(cleanup)
                .contains("needs.deploy.result == 'cancelled'")
                .contains("codedeploy_s3_bucket || 'masiton-prod-codedeploy-711457211155'")
                .doesNotContain("github.event_name == 'workflow_dispatch'")
                .doesNotContain("github.event.inputs.deployment_target");
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
                .contains("codedeploy_s3_bucket || 'masiton-prod-codedeploy-711457211155'")
                .contains("--s3-location")
                .contains("for _ in $(seq 1 270)");
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

    @Test
    @DisplayName("Redis 장애는 트래픽이 아닌 배포 게이트로 감지한다")
    void redis장애를_배포게이트alarm으로감지한다() throws IOException {
        String metrics = Files.readString(HEALTH_METRICS);
        String appDeploy = Files.readString(APP_DEPLOY);
        String bootstrap = Files.readString(BOOTSTRAP);
        String afterInstall = Files.readString(AFTER_INSTALL);
        String workflow = Files.readString(CI);
        String iam = Files.readString(TERRAFORM_IAM);
        String monitoring = Files.readString(MONITORING);
        String variables = Files.readString(TERRAFORM_VARIABLES);
        String agent = Files.readString(CLOUDWATCH_AGENT);
        String nginx = Files.readString(NGINX);
        String deploymentAlarms = section(monitoring, "locals {", "resource \"aws_cloudwatch_metric_alarm\" \"target_5xx\"");
        String deploymentRedis = section(
                monitoring,
                "resource \"aws_cloudwatch_metric_alarm\" \"fleet_dependency_redis\"",
                "resource \"aws_cloudwatch_metric_alarm\" \"blue_unhealthy\"");

        assertThat(metrics)
                .contains("MetricName=FleetDependencyRedis,Value=$redis,Unit=None")
                .contains("MetricName=DependencyRedis,Value=$redis,Unit=None,Dimensions=")
                .contains("redis_cli INFO memory")
                .contains("resolve_shared_redis_host")
                .contains("private IPv4/port")
                .contains("A colon is never valid in the hostname form")
                .contains("REDIS_ENDPOINT_HOST")
                .contains("[ \"$REDIS_ENDPOINT_VALID\" = true ] || return 1")
                .contains("REDIS_CLI_IMAGE='redis@sha256:8096655e437712b07503796fb64d81359256cfcff0ab29d95a7da72863786efb'")
                .contains("redis-cli --askpass -h \"$REDIS_ENDPOINT_HOST\" -p \"$REDIS_ENDPOINT_PORT\" --raw \"$@\" < \"$REDIS_PASSWORD_FILE\"")
                .doesNotContain("REDISCLI_AUTH=\"$redis_password\" redis-cli")
                .doesNotContain("REDIS_CLI_IMAGE=\"${REDIS_CLI_IMAGE:-")
                .contains("redis_password_owner=$(stat -c '%u:%g' \"$REDIS_PASSWORD_FILE\"")
                .contains("--user \"$redis_password_owner\"")
                .contains("--mount \"type=bind,src=$REDIS_PASSWORD_FILE,dst=/run/masiton-redis-password,readonly\"")
                .contains("/run/masiton-redis-password")
                .doesNotContain("docker run --rm --network host -e REDISCLI_AUTH")
                .contains("MetricName=RedisUsedMemoryBytes")
                .contains("MetricName=RedisMaxMemoryBytes")
                .contains("MetricName=RedisMemoryUtilizationPercent")
                .contains("maxmemory")
                .contains("used_memory");
        assertThat(appDeploy)
                .contains("is_canonical_ipv4")
                .contains("is_safe_shared_ipv4")
                .contains("resolve_shared_redis_host")
                .contains("getent ahostsv4")
                .contains("validate_shared_redis_endpoint")
                .contains("REDIS_HOST=\"$REDIS_VALIDATED_HOST\"")
                .contains("REDIS_PORT=\"$REDIS_VALIDATED_PORT\"")
                .doesNotContain("fc00")
                .doesNotContain("fd00")
                .contains("readonly REDIS_CLI_IMAGE='redis@sha256:8096655e437712b07503796fb64d81359256cfcff0ab29d95a7da72863786efb'")
                .contains("--mount \"type=bind,source=$REDIS_PASSWORD_FILE,target=/run/secrets/redis-password,readonly\"")
                .contains("--user \"$REDIS_PASSWORD_UID:$REDIS_PASSWORD_GID\"")
                .contains("redis-cli --askpass")
                .doesNotContain("REDISCLI_AUTH")
                .doesNotContain("redis_password")
                .doesNotContain("${REDIS_CLI_IMAGE")
                .doesNotContain("redis:8.8-alpine");
        assertThat(Files.readString(REDIS_README))
                .doesNotContain("REDISCLI_AUTH")
                .doesNotContain("docker exec -e")
                .contains("redis-cli --askpass")
                .contains("< /run/redis-password");
        assertThat(Files.readString(M2_PROVISIONING_RECORD))
                .doesNotContain("REDISCLI_AUTH")
                .contains("redis-cli --askpass")
                .contains("install -m 0600 /dev/null /run/masiton/redis-cli-password")
                .contains("docker exec -i masiton-redis")
                .contains("< /run/masiton/redis-cli-password");
        assertThat(appDeploy.indexOf("validate_shared_redis_endpoint \"$REDIS_HOST\" \"$REDIS_PORT\""))
                .as("공유 Redis endpoint를 검증한 뒤에만 비밀번호 파일을 열어야 한다")
                .isLessThan(appDeploy.indexOf("REDIS_PASSWORD_FILE="));
        assertThat(workflow)
                .contains("deploy/scripts/cloudwatch-install.sh")
                .contains("deploy/scripts/health-metrics.sh")
                .contains("deploy/cloudwatch/amazon-cloudwatch-agent.json")
                .contains("deploy/cloudwatch/masiton-health-metrics.service")
                .contains("deploy/cloudwatch/masiton-health-metrics.timer")
                .contains("infra/production/terraform")
                .contains("terraform init -backend=false")
                .contains("terraform validate")
                .contains("infra/production/terraform-redis/tests/template-render")
                .contains(TERRAFORM_IMAGE + " test")
                .contains("--entrypoint sh \\\n            " + TERRAFORM_IMAGE + " \\\n            -c 'terraform init -backend=false && terraform validate'")
                .doesNotContain("hashicorp/terraform:1.6.6");
        assertThat(bootstrap)
                .contains("\"$STAGE/cloudwatch-install.sh\" \"$STAGE\"")
                .contains("after-install.sh의 chmod");
        assertThat(afterInstall).contains("cloudwatch-install.sh");
        assertThat(metrics)
                .contains("put_status=$?")
                .contains("exit \"$put_status\"");
        assertThat(iam)
                .contains("cloudwatch:PutMetricData")
                .contains("cloudwatch:namespace")
                .contains("masiton/health")
                .contains("masiton/host")
                .contains("logs:PutLogEvents")
                .contains("log-group:/masiton/*");
        assertThat(agent).contains("\"namespace\": \"masiton/host\"");
        assertThat(deploymentRedis)
                .contains("evaluation_periods  = 3")
                .contains("datapoints_to_alarm = 3")
                .contains("treat_missing_data = \"breaching\"")
                .contains("comparison_operator = \"LessThanThreshold\"")
                .contains("Environment = \"asg\"");
        String deploymentRedisMemory = section(
                monitoring,
                "resource \"aws_cloudwatch_metric_alarm\" \"redis_memory_utilization\"",
                "resource \"aws_cloudwatch_metric_alarm\" \"blue_unhealthy\"");
        assertThat(deploymentRedisMemory)
                .contains("metric_name         = \"RedisMemoryUtilizationPercent\"")
                .contains("threshold           = 80")
                .contains("evaluation_periods  = 3")
                .contains("datapoints_to_alarm = 3")
                .contains("treat_missing_data = \"breaching\"")
                .contains("Environment = \"asg\"");
        assertThat(deploymentAlarms)
                .contains("var.redis_recovery_mode ? [] : [")
                .contains("aws_cloudwatch_metric_alarm.target_5xx.alarm_name")
                .contains("aws_cloudwatch_metric_alarm.target_latency.alarm_name")
                .contains("aws_cloudwatch_metric_alarm.blue_unhealthy.alarm_name")
                .contains("aws_cloudwatch_metric_alarm.fleet_dependency_redis.alarm_name")
                .contains("aws_cloudwatch_metric_alarm.redis_memory_utilization.alarm_name")
                .doesNotContain("fleet_dependency_redis_freshness");
        assertThat(variables)
                .contains("variable \"deployment_alarms_enabled\"")
                .contains("variable \"redis_recovery_mode\"")
                .contains("default     = true");
        assertThat(metrics).contains("Dimensions=[{Name=Environment,Value=$ENVIRONMENT}]");

        assertThat(nginx)
                .contains("location = /_masiton/alb-health {")
                .contains("proxy_pass http://masiton_backend/internal/health/ready");
        for (String block : nginx.split("location = /_masiton/alb-health")) {
            if (block.startsWith(" {")) {
                assertThat(block.substring(0, block.indexOf('}')))
                        .doesNotContain("/internal/health/dependencies");
            }
        }
    }

    @Test
    @DisplayName("Redis 장애 break-glass는 정상 fail-closed 게이트를 복원해야 한다")
    void redisRecovery_장애복구breakGlass와정상게이트복원을문서화한다() throws IOException {
        String baseline = Files.readString(POST_CUTOVER_BASELINE);
        String variables = Files.readString(TERRAFORM_VARIABLES);
        String monitoring = Files.readString(MONITORING);
        String codeDeploy = Files.readString(CODEDEPLOY);

        assertThat(variables)
                .contains("variable \"deployment_alarms_enabled\"")
                .contains("default     = true");
        assertThat(monitoring)
                .contains("treat_missing_data = \"breaching\"");
        assertThat(codeDeploy)
                .contains("alarms = local.deployment_alarm_names")
                .contains("enabled                   = var.deployment_alarms_enabled")
                .contains("ignore_poll_alarm_failure = false");
        assertThat(baseline)
                .contains("redis_recovery_mode=true")
                .contains("deployment_alarms_enabled=true")
                .doesNotContain("deployment_alarms_enabled=false")
                .contains("terraform.tfvars")
                .contains("정책은 바꾸지 않는다")
                .contains("공개 맛집 탐색 GET")
                .contains("회원 로그인·토큰 재발급·세션·rate-limit")
                .contains("known-good revision으로 rollback")
                .contains("15개월")
                .contains("새 metric series 3개와 alarm 1개");
        assertThat(Files.readString(PRODUCTION_README))
                .contains("redis-recovery-runbook.md")
                .contains("30분 유효기간")
                .contains("단 한 번 배포");
        assertThat(Files.readString(REDIS_RECOVERY_RUNBOOK))
                .contains("운영 담당자 2명")
                .contains("30분을 break-glass 유효기간")
                .contains("최대 한 번")
                .contains("treat_missing_data = \"breaching\"")
                .contains("ignore_poll_alarm_failure = false")
                .contains("redis_recovery_mode=true")
                .contains("deployment_alarms_enabled=true")
                .doesNotContain("deployment_alarms_enabled=false")
                .contains("ALB target 5xx")
                .contains("target latency")
                .contains("blue unhealthy-host")
                .contains("Enabled=true")
                .contains("IgnorePollFailure=false");
    }

    @Test
    @DisplayName("Redis 복구 모드에서 배포 알람 게이트 비활성화 조합을 Terraform이 거부한다")
    void redisRecoveryMode_배포알람게이트비활성화조합을계획에서거부한다() throws IOException {
        String codeDeploy = Files.readString(CODEDEPLOY);

        assertThat(codeDeploy)
                .contains("resource \"aws_codedeploy_deployment_group\" \"app\" {")
                .contains("lifecycle {")
                .contains("precondition {")
                .contains("condition     = !var.redis_recovery_mode || var.deployment_alarms_enabled")
                .contains("redis_recovery_mode=true requires deployment_alarms_enabled=true")
                .contains("ALB 5xx, latency, and unhealthy-host protections must remain enabled.");
    }

    @Test
    @DisplayName("최초 seeding에서 CodeDeploy 원본 종료 활성화 조합을 Terraform이 거부한다")
    void initialAlarmSeeding_CodeDeploy원본종료활성화조합을계획에서거부한다() throws IOException {
        String codeDeploy = Files.readString(CODEDEPLOY);

        assertThat(codeDeploy)
                .contains("condition     = !var.initial_alarm_seeding || !var.codedeploy_termination_enabled")
                .contains("initial_alarm_seeding=true cannot be combined with codedeploy_termination_enabled=true")
                .contains("keep seed ASG termination disabled until the replacement ASG is verified.");
    }

    @Test
    @DisplayName("최초 seeding에서 자동 rollback 활성화 조합을 Terraform이 거부한다")
    void initialAlarmSeeding_자동Rollback활성화조합을계획에서거부한다() throws IOException {
        String codeDeploy = Files.readString(CODEDEPLOY);

        assertThat(codeDeploy)
                .contains("condition     = !var.initial_alarm_seeding || !var.deployment_auto_rollback_enabled")
                .contains("initial_alarm_seeding=true requires deployment_auto_rollback_enabled=false")
                .contains("condition     = var.initial_alarm_seeding || var.deployment_auto_rollback_enabled")
                .contains("initial_alarm_seeding=false requires deployment_auto_rollback_enabled=true")
                .contains("condition     = var.initial_alarm_seeding ? (!var.deployment_alarms_enabled && !var.deployment_auto_rollback_enabled && !var.redis_recovery_mode) : (var.deployment_alarms_enabled && var.deployment_auto_rollback_enabled)")
                .contains("Only the explicit initial seed combination may disable deployment alarms and automatic rollback");
    }

    @Test
    @DisplayName("배포 알람 비활성화는 명시적인 최초 seeding 명령에서만 허용한다")
    void deploymentAlarms_최초Seeding명령에서만비활성화한다() throws IOException {
        String variables = Files.readString(TERRAFORM_VARIABLES);
        String codeDeploy = Files.readString(CODEDEPLOY);
        String tfvarsExample = Files.readString(TERRAFORM_TFVARS_EXAMPLE);
        String adr = Files.readString(DEPLOYMENT_ADR);
        String productionReadme = Files.readString(PRODUCTION_README);
        String cutoverRecord = Files.readString(Path.of("docs/08-planning/deployment-hardening-cutover-record.md"));

        assertThat(variables)
                .contains("variable \"initial_alarm_seeding\"")
                .contains("default     = false");
        assertThat(codeDeploy)
                .contains("condition     = var.deployment_alarms_enabled || var.initial_alarm_seeding")
                .contains("deployment_alarms_enabled=false requires initial_alarm_seeding=true")
                .contains("condition     = !var.initial_alarm_seeding || !var.redis_recovery_mode")
                .contains("initial_alarm_seeding=true cannot be combined with redis_recovery_mode=true")
                .contains("condition     = !var.initial_alarm_seeding || !var.deployment_auto_rollback_enabled")
                .contains("condition     = var.initial_alarm_seeding || var.deployment_auto_rollback_enabled")
                .contains("condition     = var.initial_alarm_seeding ? (!var.deployment_alarms_enabled && !var.deployment_auto_rollback_enabled && !var.redis_recovery_mode) : (var.deployment_alarms_enabled && var.deployment_auto_rollback_enabled)")
                .contains("condition     = !var.redis_recovery_mode || var.deployment_alarms_enabled")
                .contains("condition     = !var.initial_alarm_seeding || !var.codedeploy_termination_enabled")
                .contains("initial_alarm_seeding=true cannot be combined with codedeploy_termination_enabled=true");
        assertThat(tfvarsExample)
                .contains("terraform plan -var=\"initial_alarm_seeding=true\" -var=\"deployment_alarms_enabled=false\" -var=\"deployment_auto_rollback_enabled=false\"")
                .contains("initial_alarm_seeding             = true")
                .contains("deployment_alarms_enabled         = false");
        assertThat(adr)
                .contains("initial_alarm_seeding=true")
                .contains("deployment_alarms_enabled=false")
                .contains("deployment_auto_rollback_enabled=false")
                .contains("redis_recovery_mode=false")
                .contains("Redis 복구");
        assertThat(productionReadme)
                .contains("initial_alarm_seeding=true")
                .contains("deployment_alarms_enabled=false")
                .contains("deployment_auto_rollback_enabled=false")
                .contains("initial_alarm_seeding=false");
        assertThat(cutoverRecord)
                .contains("initial_alarm_seeding=true")
                .contains("deployment_alarms_enabled=false")
                .contains("deployment_auto_rollback_enabled=false")
                .contains("Redis 복구 모드에서는 이 완화를 사용하지 않는다");
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        if (start < 0 || end < 0) {
            throw new AssertionError("계약 섹션을 찾지 못했다: " + startMarker);
        }
        return source.substring(start, end);
    }
}


