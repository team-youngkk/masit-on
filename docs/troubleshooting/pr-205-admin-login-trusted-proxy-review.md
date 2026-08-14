---
related_documents:
  - ../06-architecture/security-boundary.md
  - ../07-adr/platform/deploy-003-validation-cookie-session.md
  - ./pr-129-deploy-cutover-and-rate-limit-review.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #205 리뷰 트러블슈팅: 관리자 로그인 trusted proxy 출처 해석

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#205](https://github.com/team-youngkk/masit-on/pull/205) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-08-14 |
| 범위 | 관리자 로그인 출처 해석, 공통 proxy 헤더 검증, 배포 순서 충돌 리뷰 반영 |
| 주 문제 유형 | 애플리케이션 / 배포 |
| 기존 기록 | [PR #129 Basic Auth 전환·rate limit](./pr-129-deploy-cutover-and-rate-limit-review.md)을 확인했다. 배포 단계에서 설정 검사 시점을 분리하고 rate-limit 경계를 검증하는 원칙을 재사용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [DNS 조회 가능성](https://github.com/team-youngkk/masit-on/pull/205#discussion_r3781973628) | 불완전한 IPv6 형태가 `InetAddress.getByName`으로 동기 DNS 조회를 유발할 수 있음 | 애플리케이션 | 수정 필요 | 공통 resolver에서 DNS API를 제거하고 IPv4·IPv6 리터럴 문법을 직접 검증 | `TrustedProxyClientAddressResolverTest`에서 `1:2:3:4:5:6:7:8:9` fallback 통과 |
| [다중 X-Forwarded-For 헤더](https://github.com/team-youngkk/masit-on/pull/205#discussion_r3781973639) | `getHeader`가 여러 헤더 중 첫 값을 신뢰할 수 있음 | 애플리케이션 | 수정 필요 | `getHeaders`로 헤더 개수를 확인하고 다건이면 peer로 fallback | 동일 테스트의 다중 헤더 시나리오 통과 |
| [사용하지 않는 4-인자 생성자](https://github.com/team-youngkk/masit-on/pull/205#discussion_r3781973640) | Spring이 사용하지 않는 resolver 직접 생성 경로가 남아 있음 | 애플리케이션 | 수정 필요 | `AdminAuthenticationController`에 resolver를 주입하는 5-인자 생성자만 유지 | 관리자 Controller focused test 통과 |
| [공통 resolver/interface 중복](https://github.com/team-youngkk/masit-on/pull/205#discussion_r3781973643) | 네 도메인에 출처 해석 로직이 복제되고 공통 interface가 일부에만 적용됨 | 애플리케이션 | 수정 필요 | 공통 `TrustedProxyClientAddressResolver`로 추출하고 Admin·Member·Verification·Map adapter가 interface를 구현하도록 정리 | 네 도메인 resolver 테스트와 공통 리터럴 검증 테스트 통과 |
| [Nginx `/api` 설정 중복](https://github.com/team-youngkk/masit-on/pull/205#discussion_r3781973645) | exact `/api`와 prefix `/api/`의 설정이 중복됨 | 기타 | 수정 불필요 | 현재 exact location은 bare `/api` 경계를 명시적으로 고정하며 기능상 오류가 아니다. 내부 redirect/include 리팩터링은 이번 보안 수정 범위를 넓히므로 적용하지 않음 | 기존 Nginx contract test 통과 |
| [SecurityProperties 관심사 분리](https://github.com/team-youngkk/masit-on/pull/205#discussion_r3781973651) | rate-limit 정책과 trusted proxy 경계 설정이 같은 nested class에 있음 | 기타 | 수정 불필요 | 현재 외부 설정 키와 fail-fast 계약을 유지하는 것이 우선이며, 별도 properties로 분리하면 설정 경로 변경이 발생한다. 동작 결함이 아닌 유지보수 제안으로 기록함 | 기존 `SecurityProperties` proxy boundary test 통과 |
| [보안 경계 문구 명시](https://github.com/team-youngkk/masit-on/pull/205#discussion_r3781973656) | 문서에서 `reverseProxyEnabled` 조건이 암시적으로만 보임 | 기타 | 수정 필요 | `reverseProxyEnabled=true` 및 trusted peer 조건을 문장에 명시 | 문서 diff 확인 및 관련 resolver 테스트 통과 |
| [app-deploy와 Nginx 설치 순서 충돌](https://github.com/team-youngkk/masit-on/pull/205#discussion_r3781978438) | Nginx 바이너리만 있어도 미설정 호스트에서 `nginx -t`가 배포를 중단시킴 | 배포 | 수정 필요 | `/etc/nginx/conf.d/masiton.click.conf`가 존재할 때만 app-deploy 설정 검사를 실행하고, 없으면 nginx-install 단계로 위임 | `AppRunScriptContractTest` 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `nginx -t` 설정 검사 실패 또는 비정상 `X-Forwarded-For`가 rate-limit 출처로 사용될 가능성.
- 발생 환경: reverse proxy를 신뢰하도록 설정한 Spring 애플리케이션과, app-deploy가 nginx-install보다 먼저 실행될 수 있는 운영 EC2.
- 재현 조건: trusted peer가 유효하지 않은 IPv6 형태 또는 동일 이름의 여러 `X-Forwarded-For` 헤더를 전달하거나, Nginx 패키지만 설치된 새 호스트에서 app-deploy를 실행한다.
- 실제 결과: 기존 구현은 DNS 조회를 실행하거나 첫 번째 헤더를 신뢰할 수 있었고, Nginx 설정이 아직 배치되지 않은 호스트의 애플리케이션 배포에서 `nginx -t`를 강제했다.
- 기대 결과: 검증 가능한 단일 IP 리터럴만 출처로 사용하고, 관리 Nginx 설정이 배치된 경우에만 Nginx 설정 검사를 실행한다.
- 영향 범위: 로그인 실패 rate-limit 출처 분리, 회원·검증·지도 rate-limit 경계, 신규 호스트 배포 순서.

## 4. 근본 원인

출처 resolver가 각 도메인에 복제되어 있었고 Admin resolver만 IP 형식 검증을 추가한 상태였다. 특히 `InetAddress.getByName`은 문자열을 DNS 이름으로 해석할 수 있으므로 정규식 통과만으로는 DNS 없는 리터럴 검증이 되지 않았다. 또한 Servlet `getHeader`는 동일 헤더 다건 여부를 표현하지 않아 `getHeaders` 확인이 필요했다.

배포 스크립트에서는 Nginx 설치 여부를 실제 관리 설정 설치 여부와 동일하게 취급한 것이 원인이었다. 새 호스트의 패키지 설치 단계와 `nginx-install.sh`의 설정·인증서 배치 단계를 구분하지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 기존 `MemberClientAddressResolver`, `VerificationClientAddressResolver`, `MapClientAddressResolver` 대조 | 동일한 peer·X-Forwarded-For 파싱이 복제되어 있었고 공통 interface 적용이 일관되지 않음 | 공통 resolver로 추출 |
| `InetAddress.getByName` 호출과 비정상 IPv6 입력 대조 | DNS 없는 형식 검증을 보장하지 못함 | 직접 IPv4·IPv6 리터럴 검증으로 교체 |
| `HttpServletRequest#getHeader`와 `getHeaders` 동작 대조 | 여러 줄 헤더에서 첫 값을 별도로 구분하지 못함 | 다건이면 fallback하도록 변경 |
| `app-deploy.sh`와 `nginx-install.sh` 실행 순서 확인 | 앱 배포가 Nginx 설치보다 먼저 실행될 수 있고, 관리 site 설정은 nginx-install에서 배치됨 | 관리 site 파일 존재 여부를 guard로 사용 |
| 기존 [PR #129 기록](./pr-129-deploy-cutover-and-rate-limit-review.md) 확인 | 배포 cutover 전 검증과 rate-limit 경계 기록 원칙이 이번 문제와 관련됨 | 검증·기록 구조에 재사용 |

## 6. 최종 해결

- 변경 내용: 공통 `TrustedProxyClientAddressResolver`를 추가해 DNS 없는 IP 리터럴 검증과 다중 헤더 거부를 모든 출처 resolver에 적용했다.
- 변경 내용: Admin controller의 죽은 4-인자 생성자를 제거하고 resolver 의존성을 단일 생성자로 고정했다.
- 변경 내용: app-deploy의 `nginx -t`를 관리 site 설정 파일이 존재하는 경우에만 실행하도록 분리했다.
- 변경 내용: 보안 경계 문서에 `reverseProxyEnabled=true` 조건을 명시했다.
- 선택 이유: 기존 외부 설정 키와 Nginx 설치 단계의 책임을 유지하면서 리뷰에서 지적된 입력·배포 경계만 최소 변경으로 보완하기 위해서다.
- 변경 파일: `src/main/java/com/masiton/common/web/TrustedProxyClientAddressResolver.java`, 관련 네 도메인 resolver, `AdminAuthenticationController.java`, `deploy/scripts/app-deploy.sh`, `docs/06-architecture/security-boundary.md`, 관련 테스트.
- 고려한 대안: Nginx `/api` exact/prefix 블록을 내부 redirect/include로 합치는 방안과 SecurityProperties를 별도 properties로 분리하는 방안은 동작 결함이 없고 설정·라우팅 계약 변경 위험이 있어 이번 PR에서는 적용하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests com.masiton.common.web.TrustedProxyClientAddressResolverTest --tests com.masiton.security.infrastructure.web.AdminClientAddressResolverTest --tests com.masiton.member.infrastructure.web.MemberClientAddressResolverTest --tests com.masiton.security.infrastructure.web.VerificationClientAddressResolverTest --tests com.masiton.restaurant.infrastructure.web.MapClientAddressResolverTest --tests com.masiton.security.presentation.AdminAuthenticationControllerTest --tests com.masiton.deployment.AppRunScriptContractTest --no-daemon --console=plain` | 통과 | 컴파일과 관련 테스트 전체 통과 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| `bash -n deploy/scripts/app-deploy.sh deploy/scripts/nginx-install.sh` | 통과 | 배포 스크립트 문법 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 공통 resolver 단위 테스트에 유효 IPv4·IPv6, 잘못된 IPv6, 동일 헤더 다건을 고정하고, 각 도메인 adapter가 동일 구현을 사용하도록 했다.
- 재발 방지: `AppRunScriptContractTest`에 관리 Nginx site 설정이 있을 때만 `nginx -t`를 실행하는 배포 순서 계약을 추가했다.
- 다음 확인: 실제 EC2에서 Nginx 패키지만 설치된 상태의 app-deploy와 nginx-install 후 상태의 app-deploy를 각각 담당자가 배포 전에 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| rate-limit 출처 오판·DNS 대기 | 측정하지 않음 | 비정상 header 입력 테스트와 운영 로그에서 resolver 지연·fallback을 확인 | 확인 예정 | 수치 비교 예정 | 보안 담당자, 운영 배포 후 첫 주 |
| 신규 호스트 app-deploy 실패율 | 측정하지 않음 | Nginx 미설치·패키지만 설치·관리 설정 설치 상태별 배포 결과 기록 | 확인 예정 | 수치 비교 예정 | 배포 담당자, 다음 EC2 배포 |

## 10. 남은 사항

- 실제 EC2 종단 smoke와 Redis Testcontainers는 PR 본문에 기재된 기존 검증 제약으로 남아 있다.
- Nginx `/api` 설정 중복 및 `SecurityProperties` 분리는 이번 PR에서 수정 불필요로 판단했으며, 별도 리팩터링이 필요하면 독립 변경으로 다룬다.
