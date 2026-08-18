---
related_documents:
  - ../05-specs/api/common/authentication-contract.md
  - ../05-specs/data/migration-plan.md
  - ../05-specs/data/table-definitions.md
  - ../06-architecture/security-boundary.md
  - ../07-adr/security/auth-006-cookie-origin-defense.md
  - ../07-adr/security/auth-007-unified-account-rbac-session.md
  - ../07-adr/platform/web-005-application-port-binding.md
  - ../07-adr/platform/web-006-unified-login-rbac-route.md
---

# PR #235 리뷰 트러블슈팅: 통합 인증·라우팅·관리자 계정 전환 계약

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#235 통합 로그인과 RBAC 관리자 진입 계약](https://github.com/team-youngkk/masit-on/pull/235) |
| 작성자 | jinyp01 |
| 처리 일자 | 2026-08-18 |
| 범위 | 통합 Token Origin 경계, 라우팅 ADR 소유권, legacy 관리자 이메일 매핑 입력·검증 |
| 주 문제 유형 | 기타(ADR·API 계약 정합성) / 데이터베이스 |
| 기존 기록 | [PR #211 Origin 방어](pr-211-admin-refresh-logout-origin-review.md)의 단일 헤더·canonical 비교를 재사용하고 역할별 설정을 통합했다. [PR #210 포트 바인딩](pr-210-application-port-binding-review.md)의 결정 소유권 원칙으로 현재 WEB-005를 대조했다. [PR #192 Flyway 계약](pr-192-flyway-model-contract-review.md)의 전진 마이그레이션·증거 기록 원칙을 staging 전환에 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [통합 Origin 계약](https://github.com/team-youngkk/masit-on/pull/235#discussion_r3802633948) | AUTH-006의 역할별 경계와 통합 Token 경로 적용 범위를 정리 | 기타 | 수정 필요 | `AUTH_ALLOWED_ORIGINS` 하나를 역할 공통 입력으로 확정하고 적용 endpoint·선행 순서·legacy 설정 폐기를 API·아키텍처·ADR에 동기화 | 변경 문서 검색, 상대 링크·anchor 검사, `git diff --check` |
| [라우팅 소유권](https://github.com/team-youngkk/masit-on/pull/235#discussion_r3802633953) | WEB-003 대체 뒤 WEB-005의 현재 소유권 참조 정리 | 기타 | 이미 해결 | WEB-005 1절이 WEB-006을 화면·API·인증 복구·상태 확인 경로 소유자로 명시하고 있음을 현재 PR HEAD에서 확인 | WEB-005 frontmatter·1절, ADR index 대조 |
| [관리자 이메일 매핑](https://github.com/team-youngkk/masit-on/pull/235#discussion_r3802633958) | legacy 관리자 매핑 입력원·정규화·중복·미매핑 검증 정의 | 데이터베이스 | 수정 필요 | 승인된 일회성 `admin_account_migration_map` staging, 제약, fail-closed 순서와 계약 단계 증거·제거 조건을 추가 | migration plan·table/constraint/physical model 대조, 상대 링크·anchor 검사 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 구현 전에 발견된 보안·데이터 전환 계약 불일치다.
- 발생 환경: PR #235 `docs/issue-234-unified-login-rbac`, 문서 전용 변경.
- 재현 조건: 통합 Token API가 역할별 Origin 설정을 계속 참조하거나, 이메일 컬럼이 없는 `admin_account`를 입력 원천 없이 `member_account.email`에 연결한다.
- 실제 결과: 구현자가 로그인에도 Origin 검사를 적용하거나 역할별 allowlist를 선택할 수 있었고, 운영자가 재현·감사할 수 없는 임의 이메일로 관리자 권한을 부여할 여지가 있었다.
- 기대 결과: 쿠키 사용 endpoint만 역할 공통 Origin 계약을 따르고, 모든 legacy 관리자는 승인·제약된 입력을 통해 정확히 하나의 회원 계정에 fail-closed로 연결된다.
- 영향 범위: 통합 로그인·재발급·로그아웃, 관리자 권한 부여, 관리자 행위자 FK 전환과 legacy 테이블 제거.

## 4. 근본 원인

통합 계정 ADR을 추가하면서 기존 AUTH-006의 endpoint·헤더 검증은 일부 갱신했지만, 역할별 설정 객체와 운영 입력 문장은 남겨 두었다. 또한 데이터 전환 계약은 `admin_account`에 이메일이 없다는 사실을 기록하면서도 “검증된 이메일 매핑”의 생성·승인·물리 제약을 정의하지 않았다. 반면 WEB-005 소유권 문장은 내부 검토에서 이미 WEB-006 기준으로 정정됐으므로 해당 리뷰는 현재 PR HEAD와 리뷰 시점의 인식 차이였다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| AUTH-006·AUTH-007·API·보안 경계의 endpoint와 설정 입력 대조 | 적용 endpoint는 대체로 통합됐으나 AUTH-006에 역할별 설정이 남음 | 역할 공통 설정과 정확한 method/path를 모든 현재 계약에 명시 |
| WEB-005 frontmatter·1절과 WEB-006·ADR index 대조 | WEB-005가 WEB-006을 현재 경로 소유자로 이미 참조 | 추가 변경 없이 현재 근거를 답글로 제시 |
| `admin_account`, MemberAccount, migration 13절과 데이터 제약 검색 | legacy 원장에는 이메일이 없고 매핑 입력·승인·제약이 없음 | 전환 staging과 검증 순서 추가 |
| 저장소에 실제 관리자 이메일을 넣는 방안 검토 | 개인정보·운영정보 노출과 변경 불가능 이력 문제가 있음 | 저장소에는 실제 입력을 두지 않고 접근 통제된 승인 기록의 checksum·메타데이터만 증거로 남김 |

## 6. 최종 해결

- 변경 내용: Origin 방어를 `AUTH_ALLOWED_ORIGINS` 하나와 정확한 Refresh·Logout endpoint에 묶고 로그인은 제외했다. legacy 관리자 매핑은 공동 승인 입력을 전환 staging에 적재한 뒤 정규화·PK/UK/FK·전수 대응·동일 회원 수렴을 검증하고, 실패 시 역할 부여 전 중단하도록 정했다.
- 선택 이유: 역할 판정 전에 하나의 보안 경계를 적용해야 설정 드리프트가 없고, 권한 상승 데이터는 입력 출처부터 계약 단계 삭제까지 재현 가능한 증거가 필요하다.
- 변경 파일: `docs/07-adr/security/auth-006-cookie-origin-defense.md`, `docs/07-adr/security/auth-007-unified-account-rbac-session.md`, 인증 API·보안 경계 문서, `docs/05-specs/data/migration-plan.md`, `table-definitions.md`, `constraints.md`, `constraint-mapping.md`, `physical-data-model.md`.
- 고려한 대안: 역할별 기존 Origin 설정을 같은 값으로 유지하는 방식은 설정 드리프트를 구조적으로 막지 못해 채택하지 않았다. 이메일을 Flyway SQL이나 저장소 CSV에 직접 넣는 방식은 개인정보와 운영 계정 정보를 변경 이력에 노출하므로 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| 변경 문서 상대 링크·heading anchor 검사 | 통과 | 신규 기록을 포함한 변경 Markdown의 링크 대상과 anchor 유효 |
| `rg` 기반 현재 계약 검색 | 통과 | 역할별 Origin 판정 문구 제거, WEB-005의 WEB-006 소유권, staging 입력·제약·증거 연결 확인 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| GitHub Actions 백엔드·프론트엔드 필수 검사 | 통과 | 리뷰 반영 커밋 push 뒤 최신 HEAD의 백엔드 빌드·테스트와 프론트엔드 빌드·타입 검사 성공 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: Origin 설정의 단일 이름과 exact endpoint를 ADR·API·보안 경계에 반복 검증 가능한 형태로 명시했고, 전환 staging을 table·constraint·migration 계약에 함께 등록했다.
- 다음 확인: 원본 스레드 3건에 처리 판단·변경·검증·기록을 답변하고 해결 처리한 뒤 `w00lam`에게 재검토를 요청했다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 해당 없음 | 문서 계약 정합성 리뷰이며 런타임 수치 비교 대상이 아님 | 구현 PR에서 역할별 Origin 회귀 테스트와 migration preflight 결과로 확인 | 확인 예정 | 확인 예정 | 인증·데이터 소유자, #234 구현 PR 검토 시점 |

## 10. 남은 사항

- 실제 통합 인증 필터·설정 변경과 Flyway SQL 구현은 이 문서 PR 범위가 아니다. 후속 구현 PR에서 이 계약과 회귀 테스트를 적용한다.
- 변경 요청 리뷰는 원본 스레드 답변·해결과 재검토 요청으로 연결했다. 최종 승인은 리뷰어 판단을 기다린다.
