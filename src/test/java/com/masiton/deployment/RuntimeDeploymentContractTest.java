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
    private static final Path REDIS_RENDER = Path.of("deploy/scripts/redis-render-conf.sh");

    @Test
    @DisplayName("직접 EC2 경로를 준비하면서 legacy ALB·ASG·CodeDeploy state를 보존한다")
    void terraform_직접경로와legacy경로를병행한다() throws IOException {
        assertThat(TERRAFORM.resolve("alb.tf")).exists();
        assertThat(TERRAFORM.resolve("asg.tf")).exists();
        assertThat(TERRAFORM.resolve("codedeploy.tf")).exists();

        String instance = read(TERRAFORM.resolve("instance.tf"));
        String route53 = read(TERRAFORM.resolve("route53.tf"));
        String security = read(TERRAFORM.resolve("security.tf"));
        String variables = read(TERRAFORM.resolve("variables.tf"));
        String outputs = read(TERRAFORM.resolve("outputs.tf"));
        String stateMigrations = read(TERRAFORM.resolve("state-migrations.tf"));

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
                .contains("direct_traffic_enabled")
                .contains("aws_lb.app.dns_name")
                .contains("aws_eip.app.public_ip");
        assertThat(security)
                .contains("resource \"aws_vpc_security_group_ingress_rule\" \"app_http\" {")
                .contains("resource \"aws_vpc_security_group_ingress_rule\" \"app_https\" {")
                .contains("aws_security_group.direct_app.id")
                .contains("var.app_ingress_cidr_blocks")
                .contains("resource \"aws_vpc_security_group_ingress_rule\" \"rds_from_app\" {")
                .contains("var.database_security_group_id")
                .contains("resource \"aws_vpc_security_group_ingress_rule\" \"database_from_direct_app\" {")
                .contains("referenced_security_group_id = aws_security_group.app.id")
                .contains("referenced_security_group_id = aws_security_group.direct_app.id");
        assertThat(variables)
                .contains("variable \"app_subnet_id\"")
                .contains("variable \"database_security_group_id\"")
                .contains("variable \"direct_traffic_enabled\"")
                .contains("variable \"alb_subnet_ids\"")
                .contains("variable \"rds_security_group_id\"");
        assertThat(stateMigrations)
                .contains("aws_route53_record.alb[\"enabled\"]")
                .contains("aws_route53_record.app[\"enabled\"]")
                .doesNotContain("rds_from_app");
        assertThat(outputs)
                .contains("output \"app_instance_id\"")
                .contains("output \"app_public_ip\"")
                .contains("output \"alb_dns_name\"")
                .contains("output \"autoscaling_group_names\"");
    }

    @Test
    @DisplayName("직접 배포 IAM과 legacy CodeDeploy IAM을 cutover 전까지 함께 유지한다")
    void terraform_배포role은직접SSM과legacy권한을함께갖는다() throws IOException {
        String iam = read(TERRAFORM.resolve("iam.tf"));

        assertThat(iam)
                .contains("github_actions_ssm_deploy")
                .contains("ssm:SendCommand")
                .contains("ssm:CancelCommand")
                .contains("AWS-RunShellScript")
                .contains("ssm:GetCommandInvocation")
                .contains("s3:PutObject")
                .contains("masiton/ssm/*")
                .contains("aws_instance.app.id")
                .contains("codedeploy:");
    }

    @Test
    @DisplayName("직접 앱 지표와 legacy ALB 지표를 cutover 전까지 함께 감시한다")
    void monitoring_직접앱과legacy경로를함께감시한다() throws IOException {
        String monitoring = read(TERRAFORM.resolve("monitoring.tf"));

        assertThat(monitoring)
                .contains("metric_name         = \"HealthLive\"")
                .contains("metric_name         = \"HealthReady\"")
                .contains("metric_name         = \"DependencyPostgres\"")
                .contains("metric_name         = \"DependencyRedis\"")
                .contains("InstanceId = aws_instance.app.id")
                .contains("treat_missing_data  = \"breaching\"")
                .contains("AWS/ApplicationELB")
                .contains("aws_lb.app");
    }

    @Test
    @DisplayName("CI는 CodeDeploy를 기본으로 유지하고 SSM 직접 배포를 명시적으로 선택한다")
    void ci_기본CodeDeploy와SSM옵트인배포를검증한다() throws IOException {
        String workflow = read(CI);

        assertThat(workflow)
                .contains("deployment_target:")
                .contains("default: codedeploy")
                .contains("aws deploy create-deployment")
                .contains("codedeploy-cancel-cleanup")
                .contains("needs.deploy.result == 'failure'")
                .contains("CodeDeploy pointer 실패 후 terminal 상태")
                .contains("CodeDeploy cleanup terminal 상태")
                .contains("ssm-deploy:")
                .contains("github.event.inputs.deployment_target == 'ssm'")
                .contains("instance_id:")
                .contains("INSTANCE_ID:")
                .contains("CONFIGURED_INSTANCE_ID:")
                .contains("PRODUCTION_INSTANCE_ID")
                .contains("ssm send-command")
                .contains("ssm get-command-invocation")
                .contains("SSM 명령 크기")
                .contains("65536")
                .contains("deploy/scripts/app-deploy.sh")
                .contains("METRIC_ENVIRONMENT=production")
                .contains("REQUIRE_SHARED_REDIS=true")
                .contains("NGINX_TRUSTED_PROXY_CIDRS=127.0.0.1")
                .contains("aws s3api put-object")
                .contains("aws s3api get-object")
                .contains("ssm cancel-command")
                .contains("SSM CommandId 보관에 실패했다")
                .contains("SSM 중지 후 terminal 상태")
                .contains("flock -n 9")
                .contains("environment: production")
                .contains("needs['ssm-deploy'].result == 'failure'")
                .contains("needs['ssm-deploy'].result == 'cancelled'");
        assertThat(workflow.indexOf("$STAGE/app-deploy.sh"))
                .isLessThan(workflow.indexOf("$STAGE/cloudwatch-install.sh"));
        assertThat(workflow.indexOf("  deploy:\n"))
                .isLessThan(workflow.indexOf("  ssm-deploy:\n"));
        int commandIdIndex = workflow.indexOf("echo \"CommandId: $command_id\"");
        int ssmTrapIndex = workflow.indexOf("trap on_exit EXIT", commandIdIndex);
        int ssmPointerIndex = workflow.indexOf("--key \"$SSM_POINTER_KEY\"", commandIdIndex);
        assertThat(commandIdIndex).isGreaterThanOrEqualTo(0);
        assertThat(ssmTrapIndex).isLessThan(ssmPointerIndex);
        assertThat(workflow).contains("ci-production-deploy");
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
    @DisplayName("Redis SSM endpoint는 대체 비밀 주입 경로가 마련될 때까지 유지한다")
    void redis_대체비밀주입경로전까지SSMEndpoint를유지한다() throws IOException {
        assertThat(REDIS_ENDPOINTS).exists();
        assertThat(read(REDIS_ENDPOINTS))
                .contains("resource \"aws_vpc_endpoint\" \"ssm\" {")
                .contains("private_dns_enabled = true");
        assertThat(read(REDIS_RENDER))
                .contains("aws ssm get-parameter")
                .contains("/masiton/redis/password");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
