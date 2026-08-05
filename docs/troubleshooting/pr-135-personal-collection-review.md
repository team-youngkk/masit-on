---
related_documents:
  - README.md
  - ../04-product/user-flows/second-expansion-user-flows.md
  - ../05-specs/api/personal/personal-collection-api.md
  - ../08-planning/second-expansion-test-matrix.md
  - pr-124-email-verification-code-review.md
---

# PR #135 리뷰 트러블슈팅: 개인 컬렉션 완료 조건과 저장 오류

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#135](https://github.com/team-youngkk/masit-on/pull/135) |
| 작성자 | jinyp01 |
| 처리 일자 | 2026-08-05 |
| 범위 | 미해결 리뷰 스레드 5건의 구현·검증·계약 판단 |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 |
| 기존 기록 | [PR #124](pr-124-email-verification-code-review.md)의 브라우저 `maxLength` 선행 절단 사례를 이름 길이 판단에 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [완료 조건 검증](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3712311679) | 정상·빈 상태·인증·타 회원 404·API 오류 재시도를 브라우저 또는 컴포넌트 수준에서 검증 | 애플리케이션 | 수정 필요 | 실제 목록·상세 화면이 공유하는 상태 컴포넌트와 5개 회귀 테스트 추가 | `npm.cmd test` 88개 통과 |
| [상세 이름 maxLength](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3712320621) | `maxLength`를 100에서 50으로 변경 | 애플리케이션 | 수정 불필요 | 100을 유지 | HTML 길이는 UTF-16 코드 단위, 계약과 검증은 유니코드 코드 포인트 기준이다. 50개 보조 평면 문자는 100 코드 단위가 필요하며 51개 입력은 제출 검증이 차단한다. |
| [목록 이름 maxLength](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3712321812) | `maxLength`를 100에서 50으로 변경 | 애플리케이션 | 수정 불필요 | 100을 유지 | 위와 동일하며 `collections-coordination.test.ts`가 50/51 코드 포인트 경계를 검증한다. |
| [누락 회원 잠금](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3712322794) | 없는 회원 조회가 JDBC 예외로 500이 되지 않도록 처리 | 데이터베이스 | 수정 필요 | 잠금 조회 결과가 비면 `AUTHENTICATION_REQUIRED`를 반환하도록 변경하고 통합 테스트 추가 | GitHub Actions 백엔드 전체 빌드·테스트 통과 |
| [맛집별 컬렉션 상태](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3717026163) | 각 컬렉션의 포함 여부·개수·추가 가능 여부를 표시하고 중복 추가를 막음 | 애플리케이션 | 결정 필요 | 계약 변경 없이 구현하지 않고 스레드 유지 | 승인 API 목록 응답에는 현재 맛집 포함 여부가 없고 공개 `restaurantCount`는 비공개 관계를 제외하므로 정확한 추가 가능 여부를 계산할 수 없다. |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없는 회원으로 생성할 때 `EmptyResultDataAccessException`이 공통 오류 처리까지 전달될 수 있었다.
- 발생 환경: `feature/t-107-personal-collection`, Java 21, Next.js 16.
- 재현 조건: 존재하지 않는 회원 ID로 컬렉션 생성 또는 목록·상세 화면의 비정상 상태 진입.
- 실제 결과: 저장소 예외가 인증 오류로 변환되지 않았고, 화면 상태별 완료 기준을 직접 검증하는 테스트가 없었다.
- 기대 결과: 인증 오류를 안정적으로 반환하고 정상·빈 상태·401·타 회원 404·재시도를 자동 검증한다.
- 영향 범위: 개인 컬렉션 생성 API와 목록·상세 화면의 회귀 안전성.

## 4. 근본 원인

회원 잠금에 단건 반환을 강제하는 `queryForObject`를 사용해 행이 없다는 정상적인 경계 상태가 JDBC 예외가 됐다. 프론트 테스트는 요청 조정 순수 함수만 다뤄 렌더링 완료 조건이 테스트 경계 밖에 있었다. 한편 맛집별 포함 상태 요청은 구현 누락만의 문제가 아니라 사용자 흐름과 승인 API 응답 사이의 계약 공백이다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 미해결 리뷰 스레드 GraphQL 조회 | 5건 확인 | 전 건을 수정 필요·수정 불필요·결정 필요로 분류 |
| 사용자 흐름과 개인 컬렉션 API 비교 | 목록 응답에 현재 맛집 포함 여부 없음 | API 소유자 결정 전 임의 필드 추가 금지 |
| 이름 길이 검증과 브라우저 `maxLength` 비교 | 코드 포인트 50자는 UTF-16 최대 100단위 | `maxLength={100}` 유지 |
| JDBC 통합 테스트 실행 | Docker 환경을 찾지 못해 Testcontainers 초기화 실패 | 컴파일을 확인하고 CI에서 실제 PostgreSQL 회귀 테스트 재검증 |

## 6. 최종 해결

- 변경 내용: 화면 상태 공통 컴포넌트와 5개 테스트를 추가하고, 누락 회원 잠금 결과를 명시적인 인증 오류로 변환했다.
- 선택 이유: 실제 화면이 사용하는 렌더링 경계를 작은 컴포넌트로 검증하면서 서버 계약은 바꾸지 않고 저장소 예외 누출만 제거한다.
- 변경 파일: `frontend/components/personal/CollectionScreenState.ts`, 목록·상세 컴포넌트와 테스트 명령, `JdbcPersonalCollectionAdapter.java`, 해당 통합 테스트.
- 고려한 대안: `maxLength={50}`은 유효한 보조 평면 문자 50개를 25개에서 잘라 제외했고, 맛집별 상세 조회 반복은 페이지네이션·비공개 관계 때문에 정확성과 요청 수 요구를 만족하지 못해 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm.cmd test` | 통과 | 프론트 88개, 신규 화면 상태 5개 포함 |
| `npm.cmd run typecheck` | 통과 | 신규 컴포넌트 및 연결부 타입 |
| `.\gradlew.bat compileJava compileTestJava` | 통과 | 백엔드 구현과 통합 테스트 컴파일 |
| `.\gradlew.bat test --tests com.masiton.personal.infrastructure.persistence.JdbcPersonalCollectionAdapterIntegrationTest` | 실패 | 코드 실패가 아니라 Docker Desktop 미기동으로 Testcontainers 초기화 실패 |
| [GitHub Actions 백엔드 빌드·테스트](https://github.com/team-youngkk/masit-on/actions/runs/30963100332) | 통과 | PostgreSQL 통합 테스트를 포함한 전체 자동화 검증 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 화면 상태 5종과 없는 회원 생성 경계를 회귀 테스트로 고정했다.
- 다음 확인: WS-09 개인 컬렉션 API 소유자와 리뷰어가 현재 맛집 포함 여부 및 실제 관계 수 기반 추가 가능 여부의 응답 계약을 결정한 뒤 해당 스레드에서 후속 구현 범위를 확정한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 화면 완료 상태 자동 검증 | 0개 | PR 테스트 실행 | 5개 | 정상·빈 상태·인증·404·재시도를 자동 검증 | PR #135 작성자, CI 완료 시 |
| 없는 회원 생성의 미변환 JDBC 예외 경로 | 1개 | 저장소 통합 테스트 | 0개 | 인증 오류로 변환하고 CI 통과 | PR #135 작성자, 2026-08-05 |

## 10. 남은 사항

- 맛집별 컬렉션 포함 상태·추가 가능 여부는 승인 API 계약에 필요한 정보가 없어 결정 필요 상태로 남긴다.
- 로컬 Docker가 실행되지 않아 타깃 통합 테스트를 직접 재실행하지 못했지만, PR CI의 백엔드 전체 빌드·테스트는 통과했다.
