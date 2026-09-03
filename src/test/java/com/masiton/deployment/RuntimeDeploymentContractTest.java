package com.masiton.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("단일 EC2 운영 배포 계약")
class RuntimeDeploymentContractTest {

    private static final Path TERRAFORM = Path.of("infra/production/terraform");
    private static final Path CI = Path.of(".github/workflows/ci.yml");
    private static final Path DIRECT_USER_DATA = TERRAFORM.resolve("templates/direct-app-user-data.sh.tftpl");
    private static final Path LEGACY_USER_DATA = TERRAFORM.resolve("templates/app-user-data.sh.tftpl");
    private static final Path REDIS_ENDPOINTS = Path.of("infra/production/terraform-redis/endpoints.tf");
    private static final Path REDIS_USER_DATA = Path.of("infra/production/terraform-redis/templates/redis-user-data.sh.tftpl");
    private static final Path REDIS_RENDER = Path.of("deploy/scripts/redis-render-conf.sh");

    @Test
    @DisplayName("직접 EC2 경로와 보존 대상 seed 리소스를 선언한다")
    void terraform_직접경로와보존대상seed를선언한다() throws IOException {
        assertThat(TERRAFORM.resolve("alb.tf")).exists();
        assertThat(TERRAFORM.resolve("asg.tf")).exists();
        assertThat(TERRAFORM.resolve("codedeploy.tf")).doesNotExist();

        String instance = read(TERRAFORM.resolve("instance.tf"));
        String route53 = read(TERRAFORM.resolve("route53.tf"));
        String security = read(TERRAFORM.resolve("security.tf"));
        String variables = read(TERRAFORM.resolve("variables.tf"));
        String outputs = read(TERRAFORM.resolve("outputs.tf"));
        String stateMigrations = read(TERRAFORM.resolve("state-migrations.tf"));
        String directDatabaseIngressResource = "resource \"aws_vpc_security_group_ingress_rule\" \"database_from_direct_app\" {";

        assertThat(instance)
                .contains("resource \"aws_instance\" \"app\" {")
                .contains("resource \"aws_eip\" \"app\" {")
                .contains("resource \"aws_eip_association\" \"app\" {")
                .contains("subnet_id                   = data.aws_subnet.direct_app.id")
                .contains("aws_security_group.direct_app.id")
                .contains("associate_public_ip_address = true")
                .contains("user_data_replace_on_change = false");
        assertThat(route53)
                .contains("resource \"aws_route53_record\" \"app\" {")
                .contains("ttl     = 60")
                .doesNotContain("aws_lb.app.dns_name")
                .contains("aws_eip.app.public_ip");
        assertThat(security)
                .contains("resource \"aws_vpc_security_group_ingress_rule\" \"app_http\" {")
                .contains("resource \"aws_vpc_security_group_ingress_rule\" \"app_https\" {")
                .contains("aws_security_group.direct_app.id")
                .contains("var.app_ingress_cidr_blocks")
                .contains("resource \"aws_vpc_security_group_ingress_rule\" \"rds_from_app\" {")
                .contains("var.database_security_group_id")
                .contains("referenced_security_group_id = aws_security_group.app.id")
                .contains("referenced_security_group_id = aws_security_group.direct_app.id")
                .doesNotContain("resource \"aws_security_group\" \"alb\" {")
                .doesNotContain("resource \"aws_vpc_security_group_ingress_rule\" \"app_from_alb\" {");
        int directDatabaseIngressStart = security.indexOf(directDatabaseIngressResource);
        int directDatabaseIngressEnd = security.indexOf("\n}", directDatabaseIngressStart);
        assertThat(directDatabaseIngressStart).isGreaterThanOrEqualTo(0);
        assertThat(directDatabaseIngressEnd).isGreaterThan(directDatabaseIngressStart);
        assertThat(security.substring(directDatabaseIngressStart, directDatabaseIngressEnd))
                .contains("count = 1");
        assertThat(variables)
                .contains("variable \"app_subnet_id\"")
                .contains("variable \"database_security_group_id\"")
                .contains("variable \"direct_traffic_enabled\"")
                .doesNotContain("variable \"alb_subnet_ids\"")
                .contains("variable \"rds_security_group_id\"");
        assertThat(stateMigrations)
                .contains("aws_route53_record.alb[\"enabled\"]")
                .contains("aws_route53_record.app[\"enabled\"]")
                .doesNotContain("rds_from_app");
        assertThat(outputs)
                .contains("output \"app_instance_id\"")
                .contains("output \"app_public_ip\"")
                .contains("output \"autoscaling_group_names\"")
                .doesNotContain("output \"alb_dns_name\"")
                .doesNotContain("output \"codedeploy_application_name\"");
    }

    @Test
    @DisplayName("저사용량 EC2 인스턴스 타입 프로파일을 선언한다")
    void terraform_저사용량인스턴스타입프로파일을선언한다() throws IOException {
        String appVariables = read(TERRAFORM.resolve("variables.tf"));
        String appExample = read(TERRAFORM.resolve("terraform.tfvars.example"));
        String redisVariables = read(Path.of("infra/production/terraform-redis/variables.tf"));

        assertThat(appVariables)
                .contains("variable \"instance_type\" {")
                .contains("description = \"직접 서비스할 앱 EC2의 인스턴스 유형\"")
                .contains("default     = \"t2.micro\"");
        assertThat(appVariables)
                .contains("variable \"seed_instance_type\" {")
                .contains("default     = \"t2.small\"");
        assertThat(appExample).contains("instance_type = \"t2.micro\"");
        assertThat(appExample).contains("seed_instance_type = \"t2.small\"");
        assertThat(read(TERRAFORM.resolve("instance.tf")))
                .contains("instance_type               = var.instance_type");
        assertThat(read(TERRAFORM.resolve("asg.tf")))
                .contains("instance_type = var.seed_instance_type");
        assertThat(redisVariables)
                .contains("variable \"redis_instance_type\" {")
                .contains("default     = \"t2.nano\"");
    }

    @Test
    @DisplayName("앱 runtime IAM은 비밀값 조회 권한만 유지한다")
    void terraform_앱runtime은비밀값조회만유지한다() throws IOException {
        String iam = read(TERRAFORM.resolve("iam.tf"));
        String redisIam = read(Path.of("infra/production/terraform-redis/iam.tf"));

        assertThat(iam)
                .contains("ssm:GetParameter")
                .contains("ssm:GetParameters")
                .contains("ssm:GetParametersByPath")
                .contains("kms:ViaService")
                .contains("ssm.${var.aws_region}.amazonaws.com")
                .contains("kms:EncryptionContext:PARAMETER_ARN")
                .contains("parameter/masiton/*")
                .contains("ecr:GetAuthorizationToken")
                .doesNotContain("codedeploy:")
                .doesNotContain("github_actions_ssm_deploy")
                .doesNotContain("ssm:SendCommand")
                .doesNotContain("ssm:CancelCommand")
                .doesNotContain("ssm:GetCommandInvocation")
                .doesNotContain("ssm:ListCommandInvocations")
                .doesNotContain("ssm:DescribeInstanceInformation");
        assertThat(redisIam)
                .contains("ReadRedisPasswordObject")
                .contains("s3:GetObject")
                .contains("var.redis_password_object_key")
                .contains("!startswith(var.redis_password_object_key, \"${var.redis_assets_prefix}/\")")
                .contains("data.aws_kms_key.s3.arn")
                .contains("kms:ViaService")
                .contains("s3.${var.aws_region}.amazonaws.com")
                .doesNotContain("ssm:GetParameter")
                .doesNotContain("redis_password_parameter_arn")
                .doesNotContain("kms:EncryptionContext:PARAMETER_ARN");
    }

    @Test
    @DisplayName("직접 앱 지표와 의존성 지표를 감시한다")
    void monitoring_직접앱과의존성지표를감시한다() throws IOException {
        String monitoring = read(TERRAFORM.resolve("monitoring.tf"));

        assertThat(monitoring)
                .contains("metric_name         = \"HealthLive\"")
                .contains("metric_name         = \"HealthReady\"")
                .contains("metric_name         = \"DependencyPostgres\"")
                .contains("metric_name         = \"DependencyRedis\"")
                .contains("InstanceId = aws_instance.app.id")
                .contains("treat_missing_data  = \"breaching\"")
                .doesNotContain("AWS/ApplicationELB")
                .doesNotContain("aws_lb.app");
    }

    @Test
    @DisplayName("CI는 Docker Hub digest를 SSH로 단일 EC2에 배포한다")
    void ci_DockerHubDigest를SSH로단일EC2에배포한다() throws IOException {
        String workflow = read(CI);

        assertThat(workflow)
                .contains("workflow_dispatch:")
                .contains("image_tag:")
                .contains("ssh-deploy:")
                .contains("DOCKERHUB_PUSH_TOKEN")
                .contains("DOCKERHUB_PULL_TOKEN")
                .contains("PRODUCTION_HOST")
                .contains("PRODUCTION_SSH_USER")
                .contains("PRODUCTION_SSH_PRIVATE_KEY")
                .contains("PRODUCTION_SSH_KNOWN_HOSTS")
                .contains("StrictHostKeyChecking=yes")
                .contains("UserKnownHostsFile=")
                .contains("--password-stdin")
                .contains("docker.io/")
                .contains("@sha256:")
                .contains("deploy/scripts/app-deploy.sh")
                .contains("deploy/scripts/dockerhub-app-deploy.sh")
                .contains("environment: production")
                .doesNotContain("deployment_target:")
                .doesNotContain("instance_id:")
                .doesNotContain("INSTANCE_ID:")
                .doesNotContain("PRODUCTION_INSTANCE_ID")
                .doesNotContain("ssm-deploy:")
                .doesNotContain("ssm-cancel-cleanup:")
                .doesNotContain("aws-actions/configure-aws-credentials")
                .doesNotContain("aws ecr")
                .doesNotContain("ssm send-command")
                .doesNotContain("ssm get-command-invocation")
                .doesNotContain("ssm cancel-command")
                .doesNotContain("SSM_POINTER");
        assertThat(workflow.indexOf("deploy/scripts/app-deploy.sh"))
                .isLessThan(workflow.indexOf("deploy/scripts/cloudwatch-install.sh"));
        assertThat(workflow).contains("ci-production-deploy");
    }

    @Test
    @DisplayName("운영 이미지와 SSH 배포는 main 브랜치에서만 실행된다")
    void ci_운영배포는main브랜치에서만실행된다() throws IOException {
        String workflow = read(CI);
        String imagesJob = section(workflow, "  images:\n", "  ssh-deploy:\n");
        String sshDeployJob = workflow.substring(workflow.indexOf("  ssh-deploy:\n"));

        assertThat(workflow)
                .doesNotContain("deploy/m2")
                .doesNotContain("deploy/**")
                .doesNotContain("refs/heads/deploy/")
                .contains("      - main");
        assertThat(imagesJob)
                .contains("github.event_name == 'push' &&\n      github.ref == 'refs/heads/main'")
                .contains("runs-on: ubuntu-24.04\n")
                .doesNotContain("ubuntu-24.04-arm");
        assertThat(sshDeployJob)
                .contains("github.ref == 'refs/heads/main'")
                .contains("needs.images.result == 'success'")
                .contains("needs['frontend-dispatch-audit'].result == 'skipped'")
                .contains("needs.images.result == 'skipped'")
                .contains("needs['frontend-dispatch-audit'].result == 'success'")
                .contains("runs-on: ubuntu-24.04")
                .contains("timeout-minutes: 30")
                .contains("StrictHostKeyChecking=yes")
                .doesNotContain("ssm send-command")
                .doesNotContain("ssm cancel-command");
    }

    @Test
    @DisplayName("직접 bootstrap과 legacy bootstrap이 서로의 배포 경계를 침범하지 않는다")
    void runtime_직접경로와legacy경로의bootstrap을분리한다() throws IOException {
        String directUserData = read(DIRECT_USER_DATA);
        String legacyUserData = read(LEGACY_USER_DATA);
        String codedeployHook = read(Path.of("deploy/codedeploy/hooks/after-install.sh"));
        String unit = read(Path.of("deploy/app/masiton-backend.service"));
        String nginx = read(Path.of("deploy/nginx/masiton.click.conf"));
        String metrics = read(Path.of("deploy/scripts/health-metrics.sh"));

        assertThat(directUserData)
                .contains("amazon-ssm-agent")
                .contains("REQUIRE_SHARED_REDIS=true")
                .contains("NGINX_TRUSTED_PROXY_CIDRS=127.0.0.1")
                .contains("METRIC_ENVIRONMENT=production")
                .doesNotContain("codedeploy-agent");
        assertThat(legacyUserData)
                .contains("codedeploy-agent")
                .contains("REQUIRE_SHARED_REDIS=true")
                .doesNotContain("METRIC_ENVIRONMENT=production");
        assertThat(unit)
                .contains("After=docker.service network-online.target")
                .doesNotContain("masiton-redis.service");
        assertThat(nginx)
                .contains("location = /_masiton/alb-health")
                .contains("location ^~ /internal/ { access_log off; return 404; }");
        assertThat(metrics)
                .contains("ENVIRONMENT=\"${METRIC_ENVIRONMENT:-asg}\"")
                .contains("DependencyRedis");
        assertThat(codedeployHook)
                .contains("/run/masiton/deploy.lock")
                .contains("flock -n 9");
    }

    @Test
    @DisplayName("Redis 비밀 객체 주입은 SSM endpoint와 분리한다")
    void redis_비밀객체주입은SSMEndpoint와분리한다() throws IOException {
        assertThat(REDIS_ENDPOINTS).exists();
        assertThat(read(REDIS_ENDPOINTS))
                .contains("resource \"aws_vpc_endpoint\" \"s3\" {")
                .doesNotContain("resource \"aws_vpc_endpoint\" \"ssm\" {");
        assertThat(read(REDIS_RENDER))
                .contains("REDIS_PASSWORD_BUCKET")
                .contains("REDIS_PASSWORD_OBJECT_KEY")
                .contains("aws s3api get-object")
                .doesNotContain("aws ssm get-parameter")
                .doesNotContain("/masiton/redis/password");
        assertThat(read(REDIS_USER_DATA))
                .contains("Environment=REDIS_PASSWORD_BUCKET=$BUCKET")
                .contains("Environment=REDIS_PASSWORD_OBJECT_KEY=$PASSWORD_OBJECT_KEY")
                .doesNotContain("REDIS_PASSWORD=");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String section(String content, String startMarker, String endMarker) {
        int start = content.indexOf(startMarker);
        int end = content.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end < 0) {
            throw new IllegalStateException("워크플로 job 경계를 찾지 못했다");
        }
        return content.substring(start, end);
    }
}
