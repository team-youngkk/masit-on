---
related_documents:
  - ../07-adr/platform/ci-001-github-actions-quality-gate.md
  - ../07-adr/platform/runtime-001-docker.md
  - ../08-planning/m2-deployment-plan.md
  - pr-129-deploy-cutover-and-rate-limit-review.md
  - pr-253-codedeploy-only-contract-test-review.md
---

# PR #369 리뷰 트러블슈팅: 파일 기반 SSH 운영 배포와 Nginx 전환 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#369 파일 기반 SSH 운영 배포 안정화](https://github.com/team-youngkk/masit-on/pull/369) |
| 작성자 | `w00lam` |
| 처리 일자 | 2026-09-07 |
| 범위 | Nginx signal/EXIT 복구, 파일 기반 TLS·앱 설정 셸 테스트의 필수 CI 연결, files 모드의 기존 ACM timer 비활성화·실패 복구 |
| 주 문제 유형 | 배포 |
| 기존 기록 | [PR #129 Nginx 컷오버 rollback](pr-129-deploy-cutover-and-rate-limit-review.md)의 전체 전환 구간 ERR trap 원칙과 [PR #253 배포 계약 테스트 CI 회귀](pr-253-codedeploy-only-contract-test-review.md)의 구현·계약·CI 동시 갱신 원칙을 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [Nginx signal rollback](https://github.com/team-youngkk/masit-on/pull/369#discussion_r3948772258) | `INT`·`TERM`·`HUP`·`EXIT`에서도 Nginx 설정을 직전 상태로 복구 | 배포 | 수정 필요 | `nginx-install.sh`에 signal/EXIT 공통 rollback trap과 timer 상태 복구 경계를 추가 | `bash -n`, `nginx-tls-files-test.sh`, deployment 계약 테스트 통과 |
| [파일 설정·TLS 테스트 CI 연결](https://github.com/team-youngkk/masit-on/pull/369#discussion_r3948772263) | 신규 셸 테스트 2개를 필수 backend CI 단계로 연결 | 배포 | 수정 필요 | `backend` job에서 앱 설정 테스트를 `sudo`로, TLS 테스트를 일반 Bash로 실행 | CI workflow에 두 단계 추가, 셸 문법 검사 통과 |
| [files 모드 ACM timer 정리](https://github.com/team-youngkk/masit-on/pull/369#discussion_r3948772275) | files 전환 시 기존 `masiton-tls-renew.timer`를 비활성화하고 실패 시 이전 상태 복구 | 배포 | 수정 필요 | enabled/active 상태를 캡처해 files 전환 시 timer를 중지·비활성화하고 실패 시 복구 | `nginx-tls-files-test.sh`, `bash -n`, deployment 계약 테스트 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 리뷰에서 signal 중단과 기존 ACM timer 잔존 가능성을 지적했다.
- 발생 환경: PR #369의 단일 EC2 SSH 배포, `TLS_SOURCE=files`, 기존 ACM 갱신 unit이 설치된 호스트.
- 재현 조건: Nginx 설정 교체 이후 `nginx -t`, 재시작, smoke 또는 후속 설치 명령 중 signal·실패가 발생하거나, ACM에서 files TLS로 전환한다.
- 실제 결과: 기존 스크립트는 `ERR` trap만 설치해 signal과 명시적 `EXIT` 경로의 복구를 보장하지 않았고, files 모드가 기존 ACM timer를 중지하지 않았다. 신규 셸 테스트도 CI backend job에서 실행되지 않았다.
- 기대 결과: 설치 중단·실패 시 Nginx 산출물과 files 전환 전 timer 상태를 복구하고, files 모드 성공 후에는 ACM timer가 AWS를 호출하지 않으며, 관련 계약 테스트가 모든 PR의 필수 품질 게이트로 실행돼야 한다.
- 영향 범위: 운영 Nginx 설정과 TLS 갱신 경로, 배포 실패 복구, CI가 탐지하는 회귀 범위.

## 4. 근본 원인

기존 `nginx-install.sh`의 rollback 경계가 `ERR`에만 걸려 있어 signal·EXIT를 같은 복구 함수로 전달하지 않았다. 또한 TLS source 분기에서 files 모드는 새 ACM timer 산출물 설치만 건너뛰고, 호스트에 이미 설치된 `masiton-tls-renew.timer`의 enabled/active 상태를 변경하지 않았다. 마지막으로 테스트 파일을 추가하면서 backend job에는 기존 Redis 셸 계약만 남아 있어, 새 파일 설정·TLS 계약이 CI 품질 게이트에 포함되지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 기존 `nginx-install.sh`의 trap과 PR #129 기록 대조 | `ERR`만 처리하고 signal/EXIT trap은 없었다. | 전체 설치 경계를 signal/EXIT까지 확장한다. |
| `TLS_SOURCE=files` 분기와 systemd timer 상태 확인 | 기존 `masiton-tls-renew.timer`를 조회·중지·비활성화하는 경로가 없었다. | 전환 전 enabled/active 상태를 캡처하고 실패 시 복구한다. |
| `.github/workflows/ci.yml` backend job 확인 | Redis 관련 셸 계약 2개만 실행했다. | 신규 앱 설정·TLS 셸 계약을 Gradle 이전 필수 단계로 연결한다. |
| `nginx-tls-files-test.sh` 실행 | Windows OpenSSL에서 기존 `-days -1` fixture가 거부됐다. | 만료 시각에 해당하는 `-days 0` fixture로 바꾸고 검증을 재실행했다. |
| 관련 셸·Gradle 계약 테스트 실행 | 최종 수정 후 모두 통과했다. | 코드·테스트·CI 변경을 함께 커밋한다. |

## 6. 최종 해결

- 변경 내용:
  - `nginx-install.sh`에 `INT`·`TERM`·`HUP`·`EXIT` trap을 추가하고, 기존 설정 복구와 files 모드 ACM timer 상태 복구를 하나의 실패 경계에서 수행한다.
  - files 모드 전환 전에 `masiton-tls-renew.timer`의 enabled/active 상태를 캡처하고 중지·비활성화한다. 정상 완료 후에는 비활성 상태를 유지하고, 실패 시 이전 상태를 복원한다.
  - `nginx-tls-files-test.sh`가 signal/EXIT trap과 timer 복구 계약을 확인하도록 보강하고, OpenSSL 버전 차이에 영향받지 않는 만료 인증서 fixture를 사용한다.
  - backend CI job에 `app-file-config-test.sh`와 `nginx-tls-files-test.sh`를 필수 단계로 추가한다.
- 선택 이유: 기존 Nginx 산출물 rollback 구조와 PR #129의 전체 전환 경계 원칙을 유지하면서, files 전환에서 실제로 바뀌는 systemd 상태까지 같은 실패 복구 경계에 포함하기 위해서다.
- 변경 파일: `deploy/scripts/nginx-install.sh`, `deploy/scripts/tests/nginx-tls-files-test.sh`, `.github/workflows/ci.yml`, `src/test/java/com/masiton/deployment/AppRunScriptContractTest.java`
- 고려한 대안: 명령별 `if` 분기에 signal 복구를 추가하는 방식은 새 설치 명령이 추가될 때 누락될 수 있어 채택하지 않았다. 파일 기반 테스트를 Gradle에만 연결하는 방식은 셸 fixture와 root 권한 경계를 그대로 검증하지 못해 CI 셸 단계로 연결했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `bash -n deploy/scripts/nginx-install.sh deploy/scripts/tests/nginx-tls-files-test.sh deploy/scripts/tests/app-file-config-test.sh` | 통과 | 변경 셸 문법 |
| `deploy/scripts/tests/nginx-tls-files-test.sh` | 통과 | files/acm 입력 검증, TLS 무변경, signal/EXIT·timer 복구 계약 |
| `./gradlew.bat --no-daemon test --tests 'com.masiton.deployment.*' --console=plain` | 통과 | deployment 계약 테스트 전체 |
| `git diff --check` | 통과 | 변경 diff 공백 |
| GitHub Actions backend build/test | 확인 예정 | 새 커밋 push 후 앱 설정·TLS 셸 계약과 전체 backend 테스트를 함께 재확인 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: files TLS 셸 계약을 backend 필수 CI 단계에서 실행하고, Nginx installer의 signal/EXIT trap과 ACM timer 복구 표식을 계약 테스트로 고정한다.
- 재발 방지: 배포 경로 변경 시 생산자(workflow)·소비자(script)·계약 테스트를 같은 PR에서 갱신한다.
- 다음 확인: 새 커밋의 GitHub Actions backend job에서 root 권한 앱 설정 테스트와 TLS 테스트가 통과하는지 확인한다. 실제 운영에서 signal 중단·timer 상태 복구를 의도적으로 유발하는 검증은 운영 장애를 만들 수 있어 이번 PR에서는 실행하지 않는다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 파일 기반 설정·TLS 계약의 필수 CI 실행 여부 | 0/2 | backend job 단계 목록 확인 | 새 커밋 CI에서 2/2 확인 예정 | CI 누락 경로를 제거 | 이우람, 새 CI run 완료 시 |
| Nginx signal/timer 복구 실패율 | 측정 없음 | 실제 운영 중단 없이 정적 계약·셸 fixture로 복구 경계 확인 | 운영 적용 전이라 측정 없음 | 수치 비교 불가 | 운영 장애를 유발하지 않는 별도 복구 리허설 필요 |

## 10. 남은 사항

- 코드와 테스트 수정은 완료했으며, 새 커밋 push 후 CI 결과와 각 리뷰 스레드 답글·해결 처리를 진행한다.
- 실제 운영 호스트에서 signal을 유발하는 복구 리허설은 실행하지 않았다.
