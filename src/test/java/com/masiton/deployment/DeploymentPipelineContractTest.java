package com.masiton.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 파이프라인 회귀 계약")
class DeploymentPipelineContractTest {

    private static final Path CI = Path.of(".github/workflows/ci.yml");

    @Test
    @DisplayName("배포 입력은 이벤트별로 이미지 참조를 결정한다")
    void 배포입력은이벤트별로이미지참조를결정한다() throws IOException {
        String workflow = read(CI);
        String resolveStep = section(workflow, "      - name: 배포 입력 및 이미지 digest 검증", "      - name: 배포 bundle 생성");

        assertThat(resolveStep)
                .contains("DEPLOYMENT_EVENT: ${{ github.event_name }}")
                .contains("if [ \"$DEPLOYMENT_EVENT\" = \"workflow_dispatch\" ]; then")
                .contains("elif [ \"$DEPLOYMENT_EVENT\" = \"push\" ]; then")
                .contains("else")
                .contains("지원하지 않는 배포 이벤트다")
                .contains("docker pull \"$image\"")
                .contains("repo_digest=")
                .contains("ref=\"docker.io/${DOCKERHUB_NAMESPACE}/masiton-${name}@${digest}\"");
    }

    @Test
    @DisplayName("push와 dispatch의 이미지 참조 및 플랫폼 검증을 유지한다")
    void push와dispatch의이미지참조및플랫폼검증을유지한다() throws IOException {
        String workflow = read(CI);
        String imagesJob = section(workflow, "  images:\n", "  ssh-deploy:\n");
        String resolveStep = section(workflow, "      - name: 배포 입력 및 이미지 digest 검증", "      - name: 배포 bundle 생성");

        assertThat(imagesJob)
                .contains("backend_ref: ${{ steps.publish.outputs.backend_ref }}")
                .contains("frontend_ref: ${{ steps.publish.outputs.frontend_ref }}")
                .contains("docker image inspect --format '{{.Os}}/{{.Architecture}}'")
                .contains("[ \"$platform\" = 'linux/amd64' ]")
                .contains("docker push");
        assertThat(resolveStep)
                .contains("images job의 backend_ref output이 비어 있다")
                .contains("images job의 frontend_ref output이 비어 있다")
                .contains("docker image inspect --format '{{.Os}}/{{.Architecture}}'")
                .contains("[ \"$platform\" != 'linux/amd64' ]");

        assertThat(indexOfOrFail(resolveStep, "docker pull \"$image\""))
                .isLessThan(indexOfOrFail(resolveStep, "docker image inspect --format '{{.Os}}/{{.Architecture}}'"));
    }

    @Test
    @DisplayName("배포 이미지 ref는 Docker Hub namespace의 64자리 digest만 허용한다")
    void 배포이미지ref는DockerHubNamespace의64자리digest만허용한다() throws IOException {
        String workflow = read(CI);
        String resolveStep = section(workflow, "      - name: 배포 입력 및 이미지 digest 검증", "      - name: 배포 bundle 생성");
        String validator = resolveStep.substring(indexOfOrFail(resolveStep, "validate_ref()"));

        assertThat(validator)
                .contains("local prefix=\"docker.io/${DOCKERHUB_NAMESPACE}/masiton-${component}@sha256:\"")
                .contains("local digest=\"${reference#\"$prefix\"}\"")
                .contains("[[ \"$digest\" =~ ^[0-9a-f]{64}$ ]]")
                .contains("validate_ref backend \"$backend_ref\"")
                .contains("validate_ref frontend \"$frontend_ref\"");
    }

    @Test
    @DisplayName("workflow dispatch 대상은 main의 조상 커밋으로 제한한다")
    void workflowDispatch대상은main의조상커밋으로제한한다() throws IOException {
        String workflow = read(CI);
        String dispatchStep = section(workflow, "      - name: dispatch 대상이 main 조상인지 검증", "      - name: 배포 입력 및 이미지 digest 검증");

        assertThat(dispatchStep)
                .contains("if: github.event_name == 'workflow_dispatch'")
                .contains("git fetch --no-tags origin main:refs/remotes/origin/main")
                .contains("git merge-base --is-ancestor \"$IMAGE_TAG\" refs/remotes/origin/main")
                .contains("fetch-depth: 0");
    }

    @Test
    @DisplayName("배포 bundle은 소스 디렉터리를 평탄화하고 manifest를 검증한다")
    void 배포bundle은소스디렉터리를평탄화하고manifest를검증한다() throws IOException {
        String workflow = read(CI);
        String bundleStep = section(workflow, "      - name: 배포 bundle 생성", "      - name: SSH key와 known_hosts 준비");

        assertThat(bundleStep)
                .contains("-C \"$GITHUB_WORKSPACE/deploy/scripts\"")
                .contains("-C \"$GITHUB_WORKSPACE/deploy/cloudwatch\"")
                .contains("-C \"$GITHUB_WORKSPACE/deploy/app\"")
                .contains("-C \"$GITHUB_WORKSPACE/deploy/nginx\"")
                .contains("bundle_entries=$(tar -tzf \"$bundle\")")
                .contains("grep -Fqx \"$entry\" <<< \"$bundle_entries\"")
                .contains("배포 bundle에 필요한 파일이 없거나 경로가 평탄화되지 않았다")
                .doesNotContain("deploy/scripts/app-deploy.sh")
                .doesNotContain("deploy/cloudwatch/amazon-cloudwatch-agent.json");
    }

    @Test
    @DisplayName("원격 배포 호출은 username·namespace·이미지 ref·stage 순서를 보존한다")
    void 원격배포호출은인자순서를보존한다() throws IOException {
        String workflow = read(CI);
        String remoteDeployStep = section(workflow, "      - name: 원격 Docker Hub 로그인과 애플리케이션 배포", "      - name: 원격 임시 파일 정리");

        assertThat(remoteDeployStep)
                .contains("REMOTE_STAGE: ${{ steps.remote-stage.outputs.path }}")
                .contains("^/run/masiton/deploy/masiton-deploy\\.[A-Za-z0-9]{6}$")
                .contains("'$remote_stage/dockerhub-app-deploy.sh' '$DOCKERHUB_USERNAME' '$DOCKERHUB_NAMESPACE' '$BACKEND_IMAGE_REF' '$FRONTEND_IMAGE_REF' '$remote_stage'");
    }

    @Test
    @DisplayName("운영 publish는 보호 환경·immutable tag·고정 Action을 사용한다")
    void 운영publish는보호환경과immutabletag를사용한다() throws IOException {
        String workflow = read(CI);
        String imagesJob = section(workflow, "  images:\n", "  ssh-deploy:\n");

        assertThat(imagesJob)
                .contains("environment: production")
                .contains("dockerhub_tag_state()")
                .contains("registry-1.docker.io")
                .contains("404) echo missing")
                .contains("state='기존 digest 재사용'")
                .doesNotContain("actions/checkout@v4");
        assertThat(workflow)
                .doesNotContain("actions/checkout@v4")
                .doesNotContain("actions/setup-java@v4")
                .doesNotContain("actions/upload-artifact@v4");
    }

    @Test
    @DisplayName("두 이미지 태그를 모두 사전 확인한 뒤 push를 시작한다")
    void 두이미지태그를모두사전확인한뒤push를시작한다() throws IOException {
        String workflow = read(CI);
        String publishStep = section(workflow, "      - name: 이미지 push와 digest 기록", "      - name: 게시 output 검증");

        assertThat(publishStep)
                .contains("tag_states[\"$name\"]=$(dockerhub_tag_state \"$repo\")")
                .contains("existing) echo \"Docker Hub에 기존 tag 확인: $remote_tag\"")
                .contains("missing) echo \"Docker Hub에 tag 없음 확인: $remote_tag\"")
                .contains("Docker Hub에 tag 없음 확인: $remote_tag")
                .contains("위 preflight loop가 backend·frontend 모두의 부재를 확인한 뒤에 push한다.");
        assertThat(indexOfOrFail(publishStep, "tag_states[\"$name\"]=$(dockerhub_tag_state \"$repo\")"))
                .isLessThan(indexOfOrFail(publishStep, "docker push \"$remote_tag\""));
    }

    @Test
    @DisplayName("원격 bundle은 root stage에 직접 쓰고 checksum 뒤 압축을 해제한다")
    void 원격Bundle은사용자소유임시파일을거치지않는다() throws IOException {
        String workflow = read(CI);
        String uploadStep = section(workflow, "      - name: bundle 업로드 및 원격 압축 해제", "      - name: 원격 Docker Hub 로그인과 애플리케이션 배포");
        String keyStep = section(workflow, "      - name: SSH key와 known_hosts 준비", "      - name: bundle 업로드 및 원격 압축 해제");

        assertThat(uploadStep)
                .contains("remote_stage=\"$(ssh")
                .contains("mktemp -d -m 0700 -p /run/masiton/deploy masiton-deploy.XXXXXX")
                .contains("remote_archive=\"$remote_stage/payload.tgz\"")
                .contains("sudo -n tee '$remote_archive'")
                .contains("sudo -n sha256sum '$remote_archive'")
                .contains("--no-same-owner")
                .contains("--no-same-permissions")
                .doesNotContain("scp \"${ssh_opts[@]}\"");
        assertThat(keyStep)
                .contains("trap cleanup EXIT")
                .contains("rm -f -- \"$key_file\" \"$known_hosts_file\"");
        assertThat(workflow)
                .contains("steps.ssh-files.outputs.key_file || format('{0}/masiton-production-key', runner.temp)")
                .contains("steps.ssh-files.outputs.known_hosts_file || format('{0}/masiton-production-known-hosts', runner.temp)");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String section(String text, String startMarker, String endMarker) {
        int start = indexOfOrFail(text, startMarker);
        int end = indexOfOrFail(text, endMarker);
        assertThat(end).as("section end must follow its start").isGreaterThan(start);
        return text.substring(start, end);
    }

    private static int indexOfOrFail(String text, String marker) {
        int index = text.indexOf(marker);
        assertThat(index).as("필수 CI 계약 표식: %s", marker).isGreaterThanOrEqualTo(0);
        return index;
    }
}
