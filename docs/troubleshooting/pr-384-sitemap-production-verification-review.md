---
related_documents:
  - ../../.github/workflows/ci.yml
  - ../../deploy/scripts/dockerhub-app-deploy.sh
  - ../../deploy/scripts/sitemap-smoke.sh
  - ../../deploy/scripts/tests/sitemap-smoke-test.sh
  - ../07-adr/platform/ci-001-github-actions-quality-gate.md
  - ../07-adr/platform/runtime-001-docker.md
  - pr-369-file-based-ssh-deployment-review.md
  - pr-206-nginx-public-api-gate-review.md
---

# PR #384 리뷰 트러블슈팅: 운영 sitemap 검증 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#384](https://github.com/team-youngkk/masit-on/pull/384) |
| 작성자 | w00lam |
| 처리 일자 | 2026-09-14 |
| 범위 | 운영 배포 경로, Googlebot robots 허용 여부, sitemap URL의 noindex 판정에 대한 누적 리뷰 9건 |
| 주 문제 유형 | 배포 / 애플리케이션 |
| 기존 기록 | [PR #369 파일 기반 SSH 운영 배포와 Nginx 전환 경계](pr-369-file-based-ssh-deployment-review.md), [PR #206 Nginx 공개 API smoke·Accepted ADR 정합화](pr-206-nginx-public-api-gate-review.md)를 조사 전에 확인했다. bundle 생산자와 소비자를 함께 대조하고 운영 smoke의 false green을 회귀 테스트로 고정하는 해결 방식을 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [r3986972970](https://github.com/team-youngkk/masit-on/pull/384#discussion_r3986972970) | SSH bundle에 sitemap smoke를 포함하고 실제 운영 경로에 보존 | 배포 | 수정 필요 | workflow bundle에 추가하고 `/opt/masiton/bin/sitemap-smoke.sh`로 설치하도록 변경 | `DeploymentPipelineContractTest`, `RuntimeDeploymentContractTest`, Docker Hub 배포 셸 계약 통과 |
| [r3986972974](https://github.com/team-youngkk/masit-on/pull/384#discussion_r3986972974) | Googlebot에 적용되는 robots 그룹과 Allow/Disallow를 판정 | 애플리케이션 | 수정 필요 | Googlebot 우선 그룹, wildcard fallback, 최장 경로·동률 Allow 우선순위를 검사하도록 변경 | `sitemap-smoke-test.sh`의 Googlebot·wildcard 차단 fixture 통과 |
| [r3986972977](https://github.com/team-youngkk/masit-on/pull/384#discussion_r3986972977) | `googlebot` 메타와 `X-Robots-Tag`의 noindex도 검사 | 애플리케이션 | 수정 필요 | HTML parser로 `robots`·`googlebot` 메타를 검사하고 응답 header 파일의 `X-Robots-Tag`를 검사하도록 변경 | `sitemap-smoke-test.sh`의 메타·header noindex fixture 통과 |
| [r3995543141](https://github.com/team-youngkk/masit-on/pull/384#discussion_r3995543141) | 표준 배포 후에도 실행 가능한 sitemap smoke 경로 보장 | 배포 | 수정 필요 | bundle·원격 stage 검증·영구 설치·README 경로를 함께 정합화 | bundle 계약 테스트와 Docker Hub 배포 셸 계약 통과 |
| [r3995543144](https://github.com/team-youngkk/masit-on/pull/384#discussion_r3995543144) | Googlebot 기준 sitemap과 공개 URL의 robots 허용 여부 검증 | 애플리케이션 | 수정 필요 | sitemap 자체와 모든 `<loc>`를 robots 규칙으로 검사하도록 변경 | 차단 fixture 실패, Allow 우선순위 정상 fixture 통과 |
| [r3995543146](https://github.com/team-youngkk/masit-on/pull/384#discussion_r3995543146) | 복합 directive·대소문자를 포함한 noindex 표현 검사 | 애플리케이션 | 수정 필요 | 대소문자 무관 `noindex`와 복합 directive를 HTML·header 양쪽에서 검사 | `NoIndex, follow`, 대소문자·속성 순서 fixture 통과 |
| [r4002830268](https://github.com/team-youngkk/masit-on/pull/384#discussion_r4002830268) | 표준 배포 후 Nginx smoke를 운영 호스트에서 다시 실행할 수 있는 영구 경로 보장 | 배포 | 수정 필요 | `nginx-smoke.sh`도 `/opt/masiton/bin/nginx-smoke.sh`로 설치하고 README 실행 경로를 영구 경로로 변경 | Docker Hub 배포 셸 계약·Java 운영 배포 계약 통과 |
| [r4002830271](https://github.com/team-youngkk/masit-on/pull/384#discussion_r4002830271) | percent-encoded URL path를 Googlebot robots 규칙과 같은 형태로 비교 | 애플리케이션 | 수정 필요 | `unquote()`를 제거하고 URL·규칙 path를 percent-encoded 형태로 정규화하며 `$` 종단 규칙도 적용 | encoded `/restaurants/a%2Fb` 차단 fixture 통과 |
| [r4002830274](https://github.com/team-youngkk/masit-on/pull/384#discussion_r4002830274) | `none`과 선택적 user-agent가 있는 `X-Robots-Tag`를 Googlebot 기준으로 판정 | 애플리케이션 | 수정 필요 | HTML·header directive를 토큰화해 `noindex`·`none`을 차단하고, header는 무대상 또는 `Googlebot`일 때만 적용 | `meta none`, `GoogleBot: none`, `otherbot: noindex` fixture 추가 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 기존 구현은 오류 없이 성공할 수 있었으며, 차단 조건을 놓치는 false green이 문제였다.
- 발생 환경: PR head `7c416817`, Docker Hub + SSH 단일 EC2 배포, 원격 root stage를 배포 후 삭제하는 현재 workflow.
- 재현 조건: 새 `sitemap-smoke.sh`만 저장소에 추가한 상태에서 표준 SSH bundle 배포를 수행하거나, `robots.txt`가 Googlebot 또는 wildcard의 `/`를 차단하는 상태에서 smoke를 실행한다.
- 실제 결과: 표준 배포가 끝나면 문서에 적힌 Nginx smoke 경로가 없고, 기존 검사는 sitemap 선언 문자열·HTTP 200·`meta name="robots"`만 확인해 percent-encoded path 차단, `meta name="googlebot"`, `none`, 대상별 `X-Robots-Tag`를 놓친다.
- 기대 결과: 배포 후 운영자가 재현 가능한 영구 경로에서 smoke를 실행하고, Googlebot이 sitemap과 모든 sitemap URL을 실제로 가져올 수 있으며, HTML·응답 header 어느 쪽의 noindex도 실패로 판정한다.
- 영향 범위: Search Console에서 수집·색인이 차단된 운영 sitemap을 정상으로 기록할 수 있고, 배포 후 핵심 검증 절차를 실행하지 못한다.

## 4. 근본 원인

첫째, `sitemap-smoke.sh`를 추가하면서 bundle의 파일 목록을 생산하는 `.github/workflows/ci.yml`과 stage 파일을 소비하는 `dockerhub-app-deploy.sh`를 함께 갱신하지 않았다. 원격 stage는 cleanup trap에서 삭제되므로, bundle에만 포함해도 운영자가 재사용할 영구 경로가 없었다.

둘째, 기존 smoke는 `robots.txt`의 sitemap 선언만 문자열로 확인하고 실제 robots 그룹을 해석하지 않았다. 따라서 `curl` 자체가 응답을 받을 수 있으면 Googlebot에게 허용되지 않은 URL도 성공으로 끝날 수 있었다. URL path를 먼저 `unquote()`하면 sitemap의 percent-encoded 식별자가 robots rule과 다른 의미로 비교될 수 있고, 종단 `$` 규칙도 별도로 처리되지 않았다.

셋째, 응답 페이지 검사는 `meta name="robots"`의 `noindex` 문자열과 무대상 `X-Robots-Tag`만 대상으로 했다. `meta name="googlebot"`, `none`, `Googlebot: ...` 헤더를 놓치거나 `otherbot: noindex`를 Googlebot 차단으로 오인할 수 있었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `git diff origin/develop...HEAD`와 PR Files changed 대조 | PR 원래 변경은 `README.md`, `deploy/scripts/sitemap-smoke.sh` 2개 파일이며 153 additions·1 deletion이었다. | 기존 workflow·wrapper가 새 파일을 전달하지 않는 생산자/소비자 불일치를 확인하고 함께 수정 |
| `.github/workflows/ci.yml`, `dockerhub-app-deploy.sh`, `nginx-install.sh`의 stage 수명과 필수 파일 목록 확인 | bundle에는 새 script가 없고 wrapper cleanup이 stage를 삭제한다. | 영구 설치 경로를 추가하고 bundle·wrapper·계약 테스트를 동기화 |
| 기존 smoke의 robots·noindex 코드 확인 | sitemap 선언과 HTTP 200만 확인하며 robots 규칙과 header 파일은 판정에 사용하지 않았다. | Googlebot 규칙 parser와 HTML/header noindex 검사를 추가 |
| Python `urllib.robotparser` 동작 확인 | 현재 Python 구현은 첫 matching rule을 반환해 `Disallow: /`와 더 구체적인 `Allow: /restaurants` 우선순위를 보장하지 않았다. | 표준 라이브러리 parser에 의존하지 않고 필요한 그룹·경로 우선순위를 직접 구현 |
| 차단·허용·메타·header fixture 실행 | 정상 Allow 우선순위는 통과하고 Googlebot 차단, wildcard 차단, `googlebot` meta noindex, `X-Robots-Tag` noindex는 실패했다. | 요청된 세 가지 리뷰 범위를 회귀 테스트로 고정 |
| encoded path fixture 추가 | `/restaurants/a%2Fb`와 `Disallow: /restaurants/a%2Fb$`를 함께 두었을 때 `unquote()` 기반 비교가 Allow rule로 잘못 통과하는 경계를 확인했다. | percent-encoded 상태 비교와 `$` 종단 규칙을 구현하고 실패 fixture를 유지 |
| `none`·대상별 `X-Robots-Tag` fixture 추가 | `meta`·Googlebot 대상 header의 `none`은 실패해야 하고 `otherbot: noindex`는 통과해야 한다. | directive token과 header user-agent 선택을 분리해 구현 |

## 6. 최종 해결

- 변경 내용: CI bundle과 원격 stage 실행 검증에 `sitemap-smoke.sh`를 추가하고, 배포 성공 후 `/opt/masiton/bin/sitemap-smoke.sh`에 mode `0750`으로 설치했다. README의 운영 실행 경로도 영구 경로로 변경했다.
- 변경 내용: 표준 SSH 배포 성공 후 `nginx-smoke.sh`도 `/opt/masiton/bin/nginx-smoke.sh`에 mode `0750`으로 설치했다. 따라서 원격 stage가 cleanup된 뒤에도 README의 Nginx·sitemap smoke 명령을 동일한 영구 경로에서 실행할 수 있다.
- 변경 내용: Googlebot 전용 그룹을 우선 선택하고 없으면 `*` 그룹을 사용하며, 일치하는 규칙 중 더 긴 경로를 우선하고 길이가 같으면 Allow를 우선하는 robots 판정을 추가했다. sitemap 자체와 모든 `<loc>`를 판정한다.
- 변경 내용: URL·robots rule path를 Google의 percent-encoded 비교 형태로 정규화하고, `$` 종단 rule을 정확히 매칭한다. `HTMLParser`로 `meta name="robots"`와 `meta name="googlebot"`의 directive를 토큰화해 `noindex`·`none`을 검사하고, 각 페이지의 response header는 무대상 또는 `Googlebot` 대상인 `X-Robots-Tag`만 같은 방식으로 검사한다.
- 변경 내용: 차단·허용·noindex fixture를 추가하고 CI와 기존 배포 계약 테스트에서 bundle 및 영구 설치 경계를 검증한다.
- 변경 파일: `.github/workflows/ci.yml`, `README.md`, `deploy/scripts/dockerhub-app-deploy.sh`, `deploy/scripts/sitemap-smoke.sh`, `deploy/scripts/tests/dockerhub-app-deploy-test.sh`, `deploy/scripts/tests/sitemap-smoke-test.sh`, `src/test/java/com/masiton/deployment/DeploymentPipelineContractTest.java`, `src/test/java/com/masiton/deployment/RuntimeDeploymentContractTest.java`
- 고려한 대안: stage 안에서 sitemap smoke를 자동 실행하는 방식은 실제 운영 endpoint·응답 집계 확인 절차와 배포 rollback 경계를 결합하므로 선택하지 않고, 배포 후에도 재현 가능한 영구 운영 도구로 설치했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 공백·patch 형식 오류 없음 |
| `bash -n deploy/scripts/sitemap-smoke.sh deploy/scripts/dockerhub-app-deploy.sh deploy/scripts/tests/sitemap-smoke-test.sh deploy/scripts/tests/dockerhub-app-deploy-test.sh` | 통과 | 변경 셸 문법 |
| `bash deploy/scripts/tests/sitemap-smoke-test.sh` | 로컬 환경 제약 | Git for Windows에서 실패 fixture 종료 뒤 임시 Python 파일 정리가 완료되지 않아 전체 셸 fixture를 끝까지 실행하지 못했다. 현재 파일에서 추출한 동일 Python block의 encoded path·`$` rule·meta `none`·Googlebot/otherbot header 임시 fixture는 통과했다. Linux CI에서 전체 셸 fixture를 확인해야 한다. |
| `bash deploy/scripts/tests/dockerhub-app-deploy-test.sh` | 통과 | wrapper 필수 파일·영구 설치·Docker Hub 인증 경계 검증 |
| `./gradlew.bat test --tests com.masiton.deployment.DeploymentPipelineContractTest --tests com.masiton.deployment.RuntimeDeploymentContractTest --no-daemon --console=plain` | 통과 | 운영 bundle·원격 stage·영구 설치 계약 20개 통과 |
| 실제 EC2 endpoint·Google Search Console | 미실행 | 현재 로컬 작업 범위와 계정·운영 권한이 없어 배포 후 담당자가 확인해야 함 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 새 fixture test를 GitHub Actions backend 셸 계약 단계에 추가했고, Java 배포 계약 테스트가 bundle manifest·원격 stage·영구 설치 문자열을 함께 확인한다.
- 다음 확인: 원격 배포 후 담당자 이우람이 `/opt/masiton/bin/sitemap-smoke.sh`와 Nginx smoke를 실행하고, 같은 시각의 robots·sitemap 응답과 Search Console의 `Crawl allowed`, `Page fetch`, sitemap 성공·발견 페이지 수를 기록한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| sitemap false-green 검출 정확성 | 측정하지 않음 | 차단·허용·noindex fixture를 CI에서 매 변경마다 실행 | 운영 배포 후 실제 응답으로 확인 예정 | 로컬 fixture에서는 차단 조건이 실패로 판정됨 | 이우람, 다음 운영 배포 후 Search Console 재제출 시점 |
| Search Console 발견 페이지 수 | 측정하지 않음 | sitemap 재제출 후 Search Console 보고서에서 확인 | 운영 재검증 때 기록 예정 | 현재 비교 불가 | 이우람, 운영 endpoint 반영 후 |

## 10. 남은 사항

- 최신 재리뷰 스레드 3건(r4002830268, r4002830271, r4002830274)은 `6d9145b7`의 변경·검증 결과를 각 원문 스레드에 답하고 모두 해결 처리했다.
- 운영 endpoint·Google Search Console 지표의 실제 확인은 다음 배포 후 담당자가 수행할 후속 작업으로 남아 있다.
