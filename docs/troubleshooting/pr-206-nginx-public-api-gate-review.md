---
related_documents:
  - README.md
  - ../05-specs/api/common/validation-access-contract.md
  - ../07-adr/platform/deploy-003-validation-cookie-session.md
  - ../07-adr/platform/deploy-004-public-api-validation-gate-boundary.md
  - ../07-adr/platform/web-003-routing-boundary.md
  - ../07-adr/README.md
  - ../07-adr/adr-index.md
  - ../07-adr/adr-traceability.md
  - ../../deploy/scripts/nginx-smoke.sh
---

# PR #206 리뷰 트러블슈팅: Nginx 공개 API smoke·Accepted ADR 정합화

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#206](https://github.com/team-youngkk/masit-on/pull/206) |
| 작성자 | `jinyp01` |
| 처리 일자 | 2026-08-14 |
| 범위 | 2절 표에 나열된 최초 미해결 인라인 리뷰 스레드 |
| 주 문제 유형 | 배포, 기타(계약 문서 정합성) |
| 기존 기록 | [PR #123 검증 세션 프록시 기록](pr-123-verification-session-review.md)의 실제 Nginx 응답 검증 원칙과 [PR #129 배포 전환 기록](pr-129-deploy-cutover-and-rate-limit-review.md)의 Accepted ADR·API 계약 동시 갱신 원칙을 재사용했다. 같은 증상의 기존 기록은 없었다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [공개 API smoke의 3xx false green](https://github.com/team-youngkk/masit-on/pull/206#discussion_r3782020737) | 공개 API가 로그인 redirect를 반환해도 smoke가 통과하는 문제 수정 | 배포 | 수정 필요 | 공통 API 라우팅 실패 판정에 모든 3xx를 추가하고 정적 계약 테스트로 고정 | `AppRunScriptContractTest`, `bash -n` 통과 |
| [Accepted ADR과 공개 범위 불일치](https://github.com/team-youngkk/masit-on/pull/206#discussion_r3782022095) | Issue #197의 공개 API gate 제외 결정과 상위 ADR 동기화 | 기타(계약 문서 정합성) | 수정 필요 | 후속 ADR-DEPLOY-004에 결정·조건·트레이드오프·검증을 정의 | 상충 문구 제거, 문서 링크·diff 검사 통과 |
| [Accepted ADR 대체 절차 누락](https://github.com/team-youngkk/masit-on/pull/206#discussion_r3782123250) | 기존 Accepted ADR 결론을 직접 수정하지 말고 새 ADR과 인덱스·추적표로 대체 | 기타(ADR 거버넌스) | 수정 필요 | ADR-DEPLOY-003을 원결정으로 복원해 Superseded 처리하고 ADR-DEPLOY-004 신설, 인덱스·추적표·기술 정책 갱신 | `superseded_by`·`supersedes` 양방향 연결과 현재 계약 링크 확인 |
| [ADR 대체 절차 중복 재지적](https://github.com/team-youngkk/masit-on/pull/206#discussion_r3782145129) | 후속 ADR 분리와 대체 메타데이터·인덱스·추적표 갱신 | 기타(ADR 거버넌스) | 이미 해결 | 앞선 대체 절차 지적과 같은 원인이며 ADR-DEPLOY-004 반영 커밋이 현재 PR head에 존재 | 원격 head와 양방향 메타데이터·인덱스·추적표 재확인 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 운영 배포 전 리뷰에서 false green과 계약 불일치를 발견했다.
- 발생 환경: `fix/nginx-public-api-verification-gate`, Nginx 재기동 직후 `deploy/scripts/nginx-smoke.sh` 실행 및 PR 문서 검토 시점.
- 재현 조건: 공개 API가 `302 /verification/login` 등 3xx를 반환하거나, API 계약 4.2절을 ADR-DEPLOY-003 4.3절의 기존 “그 밖의 `/api/**`는 gate” 문구와 대조한다.
- 실제 결과: smoke는 `VALIDATION_ACCESS_REQUIRED`와 502·503·504만 실패로 보아 3xx를 성공으로 판정했다. API 계약은 비관리자 공개 API를 gate에서 제외했지만 Accepted ADR은 운영 진입점·Callback 외 `/api/**`를 gate 대상으로 유지했다.
- 기대 결과: 공개 API smoke는 로그인 redirect를 포함한 모든 3xx를 실패로 처리한다. Accepted ADR과 하위 API 계약은 동일한 경로·Method 경계를 설명한다.
- 영향 범위: 배포 직후 공개 API 라우팅 오류 탐지, 제한 공개 보안 경계의 구현·리뷰·운영 판단.

## 4. 근본 원인

smoke 판정은 검증 gate 고유 오류와 upstream 장애만 구분하면 충분하다고 가정해 HTTP redirect를 실패 조건에서 누락했다. 그러나 Nginx의 화면 fallback은 정상적인 302를 반환할 수 있어 상태 코드 자체가 성공적으로 수신돼도 API 라우팅은 실패한 상태다.

계약 불일치는 Issue #197 구현에서 Spring Security와 Nginx 목록 및 API 계약을 동기화하면서, 권위가 더 높은 ADR-DEPLOY-003과 이를 위임하는 ADR-WEB-003의 파급 범위를 추적하지 않은 것이 원인이다. 공개 API 제외는 단순 목록 추가가 아니라 기존 “그 밖의 `/api/**`” 결정 조건을 바꾸므로 후속 Accepted ADR이 필요했다.

첫 리뷰 반영에서는 상위 계약도 함께 고쳐야 한다는 정합성에만 집중해 `docs/07-adr/README.md` 9절의 불변 이력 규칙을 확인하지 않았다. 그 결과 Accepted ADR의 결론을 직접 덮어쓰는 방식으로 수정했고, `superseded_by`·`supersedes`, 인덱스와 추적표 갱신을 누락했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 최초 미해결 GraphQL review thread와 현재 diff 대조 | 두 스레드 모두 현재 코드·문서에서 재현 | 모두 `수정 필요`로 분류 |
| `assert_not_validation_gate`와 `assert_not_validation_access_error` 조건 비교 | 두 함수 모두 3xx를 거부하지 않음 | 공통 `is_api_routing_failure`로 판정을 통일 |
| ADR-DEPLOY-003 4.3·6·7·9절과 API 계약 4.2절 대조 | 공개 API와 Redis 장애 동작 설명이 상충 | 결정 조건·장애 경계·대안·검증을 함께 수정 |
| ADR-WEB-003 6.1절과 API 계약 대조 | 기존 gate 조건을 ADR-DEPLOY-003에 위임해 후속 결정 연결이 필요 | 직접 결론 수정은 되돌리고 현재 ADR-DEPLOY-004 참조만 연결 |
| `docs/07-adr/README.md` 9절과 기존 ADR-DEPLOY-001→002 대체 사례 확인 | Accepted 결론 변경은 새 ADR과 양방향 대체 메타데이터가 필수 | 직접 수정 방식을 폐기하고 ADR-DEPLOY-004로 분리 |
| PR #123·#129 기존 기록 확인 | 실제 Nginx 응답 확인과 상위 계약 동시 수정 원칙을 재사용할 수 있음 | 새 원인과 증거만 별도 PR #206 기록으로 작성 |

## 6. 최종 해결

- 변경 내용: `nginx-smoke.sh`의 공통 라우팅 실패 조건에 `3[0-9][0-9]`를 추가해 공개 GET·POST·Webhook 모두 3xx에서 실패하게 했다. `AppRunScriptContractTest`가 해당 정규식과 공통 함수 사용을 강제한다. ADR-DEPLOY-003은 원결정을 보존한 채 `Superseded`로 전환하고, 신규 ADR-DEPLOY-004에 비관리자 `permitAll` API만 경로·Method 단위로 gate에서 제외하는 후속 결정과 트레이드오프·검증 항목을 정의했다. ADR 인덱스·추적표·기술 정책과 현재 OPS-VALIDATION 계약 참조를 ADR-DEPLOY-004로 전환했다.
- 선택 이유: 상태별 기대값을 모든 공개 API에 하드코딩하면 잘못된 입력을 쓰는 POST smoke의 정상 4xx까지 과도하게 결합한다. API 계약상 공통으로 금지할 수 있는 3xx만 라우팅 실패로 분류하면 false green을 막으면서 Spring 오류 상태 변화에는 덜 취약하다.
- 변경 파일: `deploy/scripts/nginx-smoke.sh`, `src/test/java/com/masiton/deployment/AppRunScriptContractTest.java`, `docs/07-adr/platform/deploy-003-validation-cookie-session.md`, 신규 `docs/07-adr/platform/deploy-004-public-api-validation-gate-boundary.md`, `docs/07-adr/platform/web-003-routing-boundary.md`, `docs/07-adr/adr-index.md`, `docs/07-adr/adr-traceability.md`, `docs/07-adr/platform/README.md`, `docs/06-architecture/technology-policy.md`, 현재 OPS-VALIDATION 계약·소유권 문서, `docs/troubleshooting/README.md`, 이 문서.
- 고려한 대안: 모든 공개 경로의 상태·Content-Type을 개별 고정하는 방식은 malformed JSON을 사용하는 부작용 없는 POST smoke가 다양한 정상 4xx를 반환할 수 있어 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests com.masiton.deployment.AppRunScriptContractTest --tests com.masiton.security.infrastructure.configuration.SecurityConfigurationApiTest` | 통과 | 28개 테스트에서 공개 API 매트릭스·Nginx smoke 계약과 Spring Security 경계 확인 |
| `bash -n deploy/scripts/nginx-smoke.sh` | 통과 | 셸 구문 정상 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| 변경 문서의 상대 링크 해석 검사 | 통과 | 수정 ADR와 트러블슈팅 문서 링크가 모두 존재 |
| `nginx:1.30.3-alpine nginx -t` | 통과 | 저장소 `nginx.conf`·upgrade map·site 설정을 함께 적재해 구문 정상 확인 |
| ADR 대체 메타데이터·인덱스·추적표 검사 | 통과 | ADR-DEPLOY-003 `Superseded`↔ADR-DEPLOY-004 `Accepted` 양방향 연결과 현재 참조 확인 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 공개 API smoke의 3xx 거부를 정적 계약 테스트에 추가했다. 공개 API 목록 변경 시 Spring Security·Nginx·API 계약과 현재 Accepted ADR을 대조한다. Accepted ADR의 결론 변경이 필요하면 본문을 직접 고치기 전에 `docs/07-adr/README.md` 9절과 대체 메타데이터·인덱스·추적표를 먼저 확인한다.
- 다음 확인: 실제 EC2 배포 직후 `nginx-smoke.sh`가 운영 응답에서도 3xx를 거부하는지는 Issue #197 운영 반영 시 OPS-VALIDATION 담당자가 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 공개 API 3xx false green 허용 여부 | 허용 | 302 응답을 공통 판정식에 대입하고 정적 계약 테스트로 지속 확인 | 로컬 기준 거부, 운영 확인 예정 | 로컬 개선 확인 | OPS-VALIDATION 이우람, Issue #197 운영 배포 직후 |
| Accepted ADR과 공개 API gate 계약의 상충 | ADR-DEPLOY-003의 기존 문구 1곳과 하위 계약 상충 | 현재 Accepted ADR의 결정·트레이드오프·검증 절과 API 계약 4절을 PR 리뷰 때 대조 | ADR-DEPLOY-004 기준 상충 문구 0곳 | 문서 기준 해소 | 필수 리뷰어 승인 시 확정 |

## 10. 남은 사항

- 실제 EC2·운영 도메인의 배포 후 smoke와 실패 주입 rollback은 이 처리 범위에서 실행하지 않았다.
