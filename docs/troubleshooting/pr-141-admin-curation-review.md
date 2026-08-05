---
related_documents:
  - README.md
  - ../04-product/prd/curation/admin-curation.md
  - ../05-specs/api/curation/curation-api.md
  - ../05-specs/api/common/identifier-contract.md
  - ../05-specs/api/common/error-contract.md
  - ../05-specs/data/second-expansion-data-contract.md
  - ../06-architecture/module-boundaries.md
---

# PR #141 리뷰 트러블슈팅: 관리자 큐레이션 입력·조회 경계와 계약 불일치

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#141](https://github.com/team-youngkk/masit-on/pull/141) |
| 작성자 | `inan0226` |
| 처리 일자 | 2026-08-05 |
| 범위 | 미해결 리뷰 스레드 15건의 재현, 수정, 회귀 검증 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #134](pr-134-participation-request-review.md)의 불투명 식별자·교차 도메인 조회 경계 원칙과 [PR #129](pr-129-deploy-cutover-and-rate-limit-review.md)의 코드·계약 동기화 원칙을 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [생성 폼 길이](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718015397) | HTML 길이 제한을 API와 일치 | 애플리케이션 | 수정 필요 | 100/1000자로 수정 | 프론트 테스트·빌드 |
| [상세 폼 길이](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718015402) | 수정 폼 길이 제한을 API와 일치 | 애플리케이션 | 수정 필요 | 100/1000자로 수정 | 프론트 테스트·빌드 |
| [UUID 정규식 완화](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718015405) | UUID 버전 제약 완화 | 애플리케이션 | 수정 필요 | 더 강한 계약 지적에 따라 형식 검사를 전부 제거 | 불투명 문자열 테스트 |
| [구성 검증 N+1](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718015407) | 단건 반복 조회 제거 | 애플리케이션 | 수정 필요 | Restaurant 다건 입력 포트와 저장소 다건 조회 추가 | 서비스 단위 테스트 |
| [클라이언트 UUID 검증](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718030460) | 식별자 내부 형식 전제 제거 | 애플리케이션 | 수정 필요 | 공백·중복·20개 상한만 검사하고 서버에 전달 | 프론트·Controller 테스트 |
| [상한 경계 테스트](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718030467) | 구성 20개·게시 5개 상한 검증 | 애플리케이션 | 수정 필요 | 21개 거부와 6번째 게시 거부 테스트 추가 | 서비스 테스트 8건 통과 |
| [관리 응답 계약](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718030472) | 응답 문서 동기화와 내부 관리자 ID 제거 | 애플리케이션 | 수정 필요 | 응답 예시를 명시하고 감사용 ID·생성 시각 제거 | Controller API 테스트 |
| [미사용 순서 검증](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718030477) | 실행 경로에 없는 검증 제거 또는 연결 | 애플리케이션 | 수정 필요 | 서버 검증을 단일 기준으로 두고 미사용 함수·테스트 제거 | 타입 검사·프론트 테스트 |
| [추측성 응답 폴백](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718030483) | 계약에 없는 필드 폴백 제거 | 애플리케이션 | 수정 필요 | 실제 응답 필드만 읽는 타입과 정규화로 축소 | 타입 검사·프론트 테스트 |
| [Restaurant 판정 소유권](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718030488) | 공개·활성 판정 일원화 | 애플리케이션 | 수정 필요 | Restaurant 입력 포트에서 이름·상태·공개 여부 일괄 제공 | ArchUnit·서비스 테스트 |
| [교차 도메인 Adapter 위치](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718113493) | 타 도메인 테이블 직접 조회 제거 | 애플리케이션 | 수정 필요 | Curation Query Adapter를 삭제하고 공개 Restaurant 포트 사용 | curation 패키지 SQL 검색·ArchUnit |
| [공통 감사 로그](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718113497) | 큐레이션 전용 로거의 공통화 여부 확인 | 애플리케이션 | 수정 필요 | 커밋 후 기록을 보장하는 공통 `OperationAuditLogger`로 추출 | 전체 백엔드 빌드 |
| [감사 이전·이후 값](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718113500) | 길이 구간 기록과 데이터 계약 동기화 | 애플리케이션 | 수정 필요 | 원문 비기록·길이 구간 기록을 데이터 계약에 명시 | 문서·구현 대조 |
| [게시 상태 누락](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718113502) | 누락과 허용값 오류 구분 | 애플리케이션 | 수정 필요 | null은 `MISSING_REQUIRED_FIELD`, 잘못된 값은 `INVALID_FIELD_VALUE` 유지 | Controller API 테스트 |
| [메인 순서 필드 누락](https://github.com/team-youngkk/masit-on/pull/141#discussion_r3718113506) | 배열 필드 자체 누락 오류 구분 | 애플리케이션 | 수정 필요 | null 배열을 `MISSING_REQUIRED_FIELD`로 선검증 | Controller API 테스트 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 클라이언트의 `맛집 식별자는 UUID 형식이어야 합니다.`, 서버의 계약과 다른 길이 제한 및 누락 필드 오류
- 발생 환경: Windows, Java 21, Node.js/Next.js 프론트엔드, `feature/t-110-admin-curation`
- 재현 조건: 비 UUID 불투명 식별자 입력, 21개 구성 또는 6번째 게시, 상태·순서 필드 누락, 여러 맛집을 포함한 목록·상세 조회
- 실제 결과: 정상 식별자를 클라이언트가 선제 거부하고, Restaurant 조회와 공개 판정이 Curation SQL 및 단건 포트에 중복되며, 응답·감사 계약과 구현이 달랐다.
- 기대 결과: 클라이언트는 식별자를 불투명 문자열로 전달하고, Restaurant 도메인이 다건 참조와 공개 판정을 소유하며, 입력·응답·오류·감사 계약이 코드와 일치해야 한다.
- 영향 범위: 관리자 큐레이션 생성·편집·구성·게시·목록·상세 API와 운영 감사 로그

## 4. 근본 원인

입력 제약과 응답 형태를 각 화면에서 추측해 중복 구현했고, 편의를 위해 Curation 인프라가 Restaurant 테이블과 공개 판정을 직접 소유했다. 이로 인해 불투명 식별자 계약을 위반하고 최대 20회 단건 조회와 판정 규칙 드리프트가 생겼다. 또한 API 응답 레코드와 감사 로그가 먼저 구현된 뒤 계약 문서와 경계 테스트가 함께 갱신되지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 식별자 계약과 PR #134 기록 대조 | 클라이언트 UUID 검증 금지 확인 | 정규식 완화 대신 전면 제거 |
| Curation 패키지의 Restaurant SQL·판정 검색 | 직접 조회·판정이 세 경로에 중복 | Restaurant 다건 입력 포트로 일원화 |
| 상한 분기 테스트 검색 | 구성 20개·게시 5개 분기 미실행 | 경계 테스트 2건 추가 |
| API·데이터 계약과 직렬화 레코드 대조 | 내부 감사 ID와 감사 메타데이터 불일치 | 응답 필드 제거·문서 동기화·공통 로거 추출 |

## 6. 최종 해결

- 변경 내용: 입력 제한 일치, 클라이언트 UUID 검증 제거, Restaurant 다건 참조 포트 추가, 교차 도메인 SQL Adapter 삭제, 관리 응답 계약 축소·문서화, 공통 감사 로거 추출, 누락 필드 오류 구분, 경계 테스트 추가
- 선택 이유: 식별자·모듈 경계·오류 계약을 단일 소유 지점에 두고 조용한 폴백과 N+1을 함께 제거하기 위해서다.
- 변경 파일: `frontend/components/admin/*`, `frontend/lib/admin/curations*`, `src/main/java/com/masiton/curation/**`, `src/main/java/com/masiton/restaurant/**`, `src/main/java/com/masiton/common/observability/OperationAuditLogger.java`, 관련 테스트와 계약 문서
- 고려한 대안: Query Adapter를 orchestration으로 이동하는 방법도 가능하지만, 이미 존재하는 Restaurant 공개 입력 포트의 다건 확장이 판정 중복과 N+1을 동시에 제거한다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew test --tests ...AdminCurationServiceTest --tests ...AdminCurationControllerApiTest --tests ...CurationPostgreSqlIntegrationTest --tests ...RestaurantReferenceQueryServiceTest --tests ...ArchitectureTest` | 통과 | 서비스·API·PostgreSQL·다건 참조·아키텍처 |
| `npm test` | 통과 | 프론트 단위 테스트 110건 |
| `npm run typecheck` | 통과 | TypeScript 응답 계약과 컴포넌트 타입 |
| `./gradlew assemble test --tests ...AdminCurationServiceTest --tests ...AdminCurationControllerApiTest --tests ...CurationPostgreSqlIntegrationTest --tests ...RestaurantReferenceQueryServiceTest --tests ...ArchitectureTest` | 통과 | 배포 산출물 생성과 관련 테스트 30건 |
| `npm run build` | 통과 | 프론트 테스트 110건, 타입 검사, Next.js 운영 빌드와 25개 페이지 생성 |
| `./gradlew build` | 미완료 | 전체 테스트 실행 종료 단계에서 기존 Spring scheduling 비데몬 스레드가 남아 제한 시간 안에 프로세스가 종료되지 않았다. 관련 테스트와 `assemble`은 별도 실행으로 통과했다. |
| [GitHub Actions CI](https://github.com/team-youngkk/masit-on/actions/runs/30978323804) | 통과 | 프론트 빌드·타입 검사와 백엔드 전체 빌드·자동화 테스트 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 불투명 식별자, 누락 필드, 구성·게시 상한, 다건 참조 호출 수를 회귀 테스트로 고정했다.
- 다음 확인: 다른 관리자 도메인이 운영 감사 로그를 도입할 때 공통 `OperationAuditLogger`를 재사용한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 구성 맛집 참조 조회 | 요청당 최대 20회 | 서비스 mock 호출 수 | 요청당 1회 | N+1 제거 | PR #141 병합 전 |
| 상한 회귀 테스트 | 0건 | 관련 분기 테스트 수 | 2건 | 20개·5개 상한 고정 | PR #141 병합 전 |
| 클라이언트 식별자 형식 전제 | UUID 정규식 1개 | 정적 검색 | 0개 | 불투명 문자열 계약 준수 | PR #141 병합 전 |

## 10. 남은 사항

- 로컬 전체 테스트 종료 시 여러 `scheduling-1` 비데몬 스레드가 남는 기존 테스트 환경 문제는 이번 큐레이션 변경과 분리해 추적할 필요가 있다.
