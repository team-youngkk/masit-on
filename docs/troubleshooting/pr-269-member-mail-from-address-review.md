---
related_documents:
  - ../../deploy/scripts/app-secrets-render.sh
  - ../../src/main/resources/application.yml
  - ../../src/main/resources/application-prod.yml
  - ../../src/test/java/com/masiton/deployment/AppRunScriptContractTest.java
  - ../08-planning/m2-provisioning-record.md
  - ../06-architecture/implementation-conventions.md
  - ../07-adr/security/sec-001-secrets-workload-identity.md
---

# PR #269 리뷰 트러블슈팅: 회원 메일 발신 주소 주입 경로와 렌더러 중복

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#269 회원 인증 메일 발신 주소를 서비스 도메인으로 전환](https://github.com/team-youngkk/masit-on/pull/269) |
| 작성자 | `inan0226` |
| 처리 일자 | 2026-08-20 |
| 범위 | 미해결 인라인 리뷰 5건(`w00lam` 4건, `jinyp01` 1건) |
| 주 문제 유형 | 배포 — SSM 렌더링 함수의 책임 중복과 설정 계층 가시성 |
| 기존 기록 | [트러블슈팅 인덱스](README.md)와 최근 운영·배포 기록([PR #261](pr-261-performance-deps-separation-review.md), [운영 부트스트랩 장애](ops-2026-08-20-perf-env-bootstrap-failure.md))를 확인했다. 기존 기록은 비밀값의 tmpfs·SSM 주입 경계를 다루지만, 이번 셸 함수 중복과 기본 프로파일 placeholder 누락은 기록되지 않아 새 기록으로 남긴다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [범용 렌더러 오류 메시지](https://github.com/team-youngkk/masit-on/pull/269#discussion_r3818577627) | `render_required_non_blank`의 메일 전용 오류 메시지를 일반화 | 배포 | 수정 필요 | 공통 `read_required_parameter`와 `필수 설정값(non-blank)` 메시지로 변경 | `AppRunScriptContractTest`에서 helper 호출과 오류 메시지를 고정 |
| [기동 시점 fail-fast 검증](https://github.com/team-youngkk/masit-on/pull/269#discussion_r3818577631) | 발신 주소 누락·공백을 기동 시 차단한 구현 칭찬 | 애플리케이션 | 이미 해결 | `MemberActionMailProperties.validate()`의 null/공백 검증을 유지 | `ProdSecretsConfigTreeTest` 및 영향 테스트 통과 |
| [기본 프로파일 placeholder](https://github.com/team-youngkk/masit-on/pull/269#discussion_r3818577632) | `application.yml`에도 `MEMBER_ACTION_MAIL_FROM_ADDRESS` placeholder 명시 | 애플리케이션 | 수정 필요 | 기본 프로파일의 `action-mail` 블록에 빈 기본값 placeholder 추가 | `AppRunScriptContractTest`에서 placeholder와 운영 매핑 통과 |
| [운영 주입 경로 계약 테스트](https://github.com/team-youngkk/masit-on/pull/269#discussion_r3818577634) | SSM·configtree·운영 문서 매핑 계약 테스트 설계 칭찬 | 배포 | 이미 해결 | 기존 계약 테스트를 유지하고 공통 helper·기본 placeholder 검증을 보강 | 관련 계약 테스트 통과 |
| [SSM 조회 코드 중복](https://github.com/team-youngkk/masit-on/pull/269#discussion_r3818764761) | `render_required`와 `render_required_non_blank`가 공통 조회 로직을 공유하도록 개선 | 배포 | 수정 필요 | SSM 조회·None/빈 값 처리를 `read_required_parameter`로 추출하고 각 렌더러는 추가 검증만 수행 | `AppRunScriptContractTest`에서 공통 helper와 두 호출 경로를 고정 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 기존 non-blank 실패 시 `필수 메일 발신 주소를 읽지 못했다: ...`가 출력됨
- 발생 환경: 운영 설정을 SSM에서 `/run/masiton/secrets`로 렌더링하는 `deploy/scripts/app-secrets-render.sh`와 Spring 공통·프로파일 설정 계층
- 재현 조건: 공백만 허용하지 않는 필수 Parameter를 다른 속성에 재사용하거나, 기본 `application.yml`만 읽는 환경에서 발신 주소 환경 변수 이름을 확인하는 경우
- 실제 결과: 렌더러가 범용 함수 이름과 달리 메일 발신 주소에 종속된 오류를 출력했고, 기본 프로파일에는 발신 주소 placeholder가 없어 프로파일 간 설정 가시성이 달랐다. 두 렌더러의 SSM 조회 코드도 별도로 존재했다.
- 기대 결과: 공통 조회 실패 메시지는 속성 종류와 무관해야 하며, 공통 프로파일에도 발신 주소 주입 경로가 드러나고, SSM 조회 코드는 한 곳에서 관리되어야 한다.
- 영향 범위: 현재 운영 동작의 직접 장애는 아니지만, 향후 필수 설정 추가·변경 시 오진 로그와 수정 누락 가능성이 생긴다. 기본 프로파일 가시성 누락은 설정 계층 추적성을 낮춘다.

## 4. 근본 원인

PR에서 공백-only 발신 주소를 별도로 거부하려고 `render_required_non_blank`를 추가하면서 기존 `render_required`의 SSM 조회·기본값 검사 코드를 복사했다. 이 과정에서 함수의 범용성에 맞춰 오류 메시지를 추상화하지 못했다. 또한 발신 주소를 local/prod 프로파일과 운영 렌더러에는 추가했지만, 공통 `application.yml`의 `action-mail` 블록에는 같은 환경 변수 placeholder를 추가하지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #269의 미해결 인라인 스레드·최신 답글 확인 | P3 3건과 구현 칭찬 P5 2건 확인 | 각 스레드를 별도로 기록하고, 실제 변경이 필요한 P3만 수정한다. |
| `deploy/scripts/app-secrets-render.sh`의 두 렌더러 비교 | `aws ssm get-parameter`와 빈 값 검사가 중복됨 | 공통 조회 helper를 추출한다. non-blank 검사는 호출자에 남긴다. |
| `application.yml`, `application-local.yml`, `application-prod.yml` 대조 | local/prod와 운영 renderer에는 from-address가 있으나 공통 프로파일에는 없음 | 공통 프로파일에 `${MEMBER_ACTION_MAIL_FROM_ADDRESS:}`를 추가한다. 빈 기본값은 기존 로컬·테스트·운영 주입 정책을 바꾸지 않는다. |
| `AppRunScriptContractTest` 보강 | helper 호출, 오류 메시지, 기본 placeholder를 문자열 계약으로 고정 가능 | 다음 리팩터링에서 조회 로직과 설정 경로가 다시 분리되지 않도록 회귀 조건으로 추가한다. |
| Windows에서 `bash -n deploy/scripts/app-secrets-render.sh` 실행 시도 | `/bin/bash`가 없어 실행하지 못함 | 셸 문법 검증은 CI의 Bash 환경에서 확인 대상으로 남긴다. |

## 6. 최종 해결

- 변경 내용
  - `read_required_parameter`가 SSM 조회와 null/빈 값 처리를 담당하고, 오류 문구를 인자로 받아 범용성을 유지하도록 했다.
  - `render_required`와 `render_required_non_blank`는 공통 helper를 호출하고, non-blank renderer만 공백-only 검사를 추가로 수행하도록 했다.
  - `application.yml`의 `masiton.member.action-mail` 블록에 `from-address: ${MEMBER_ACTION_MAIL_FROM_ADDRESS:}`를 추가했다.
  - 계약 테스트가 helper 분리, 두 호출 메시지, 공백 정규식, 기본 placeholder, 운영 SSM·configtree·문서 매핑을 함께 검증하도록 보강했다.
- 선택 이유: 오류 메시지와 SSM 조회 경로를 한 곳에 두면 필수 설정 종류가 늘어날 때 동작과 진단 문구가 함께 유지된다. 공통 프로파일의 빈 placeholder는 실제 기본값을 만들지 않으면서 환경 변수 이름과 설정 계층을 명시한다.
- 변경 파일: `deploy/scripts/app-secrets-render.sh`, `src/main/resources/application.yml`, `src/test/java/com/masiton/deployment/AppRunScriptContractTest.java`
- 고려한 대안: 오류 문구만 수정하고 중복을 유지하는 방법은 단기 변경량이 작지만 조회 방식 변경 시 두 경로가 다시 어긋날 수 있어 채택하지 않았다. 공통 프로파일에 로컬 주소를 기본값으로 넣는 방법은 운영 주입 누락을 가릴 수 있어 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\gradlew.bat test --tests com.masiton.deployment.AppRunScriptContractTest --tests com.masiton.member.infrastructure.mail.AesGcmMemberActionTokenCipherTest --tests com.masiton.member.infrastructure.mail.MemberActionTokenMailAdapterTest --tests com.masiton.common.config.ProdSecretsConfigTreeTest --no-daemon --console=plain` | 통과 | 운영 주입 경로·공통 helper·기본 placeholder와 메일 From·기동 fail-fast 관련 테스트가 통과했다. |
| `git diff --check` | 통과 | 공백·패치 형식 오류가 없다. |
| `bash -n deploy/scripts/app-secrets-render.sh` | 미실행 | 현재 Windows 환경에 `/bin/bash`가 없다. CI Bash 검증에서 확인한다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지: `AppRunScriptContractTest`가 SSM 조회 helper 공유와 오류 메시지, 공백-only 거부, 공통 프로파일 placeholder, 운영 매핑을 계약으로 고정한다.
- 다음 확인: CI Bash 단계에서 `app-secrets-render.sh` 문법 검증을 수행하고, 운영 배포 승인 시 SSM 실제 값·IAM 읽기 권한·SMTP 발신 도메인 승인과 SPF/DKIM을 확인한다. 운영 담당자가 배포 승인 전에 수행한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 필수 렌더러의 SSM 조회 구현 수 | 2곳(`render_required`, `render_required_non_blank`) | 셸 함수 정의를 정적 검색 | 1곳(`read_required_parameter`) | 공통 조회 로직이 단일 경로로 감소 | 개발자, PR 반영 직후 |
| 공백 발신 주소의 기동 차단 | 기존 PR 구현에서 차단 | `ProdSecretsConfigTreeTest` 공백 fixture 실행 | 통과 | 동작은 유지하고 진단·구조만 개선 | 개발자, PR 반영 직후 |
| 운영 SMTP·DNS 실제 수신 검증 | 미측정 | 운영 SSM·SMTP·DNS와 테스트 메일 헤더 확인 | 확인 예정 | 로컬에서는 외부 자격·DNS를 사용하지 않음 | 운영 담당자, 배포 승인 전 |

## 10. 남은 사항

- 코드 및 테스트 변경과 관련된 미해결 스레드는 원격 반영 후 인라인 답글을 달고 해결 처리한다.
- Windows에서 Bash 문법 검증과 실제 SSM·SMTP·DNS 검증은 실행하지 못했으며, CI와 운영 배포 게이트에서 확인한다.
