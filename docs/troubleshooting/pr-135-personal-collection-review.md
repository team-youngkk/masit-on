---
related_documents:
  - README.md
  - ../04-product/user-flows/second-expansion-user-flows.md
  - ../05-specs/api/personal/personal-collection-api.md
  - ../06-architecture/dependency-rules.md
  - ../06-architecture/package-structure.md
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
| 범위 | 리뷰 스레드 6건의 구현·검증과 후속 계약 결정 반영 |
| 주 문제 유형 | 애플리케이션 / 데이터베이스 |
| 기존 기록 | [PR #124](pr-124-email-verification-code-review.md)의 브라우저 `maxLength` 선행 절단 사례를 이름 길이 판단에 적용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [완료 조건 검증](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3712311679) | 정상·빈 상태·인증·타 회원 404·API 오류 재시도를 브라우저 또는 컴포넌트 수준에서 검증 | 애플리케이션 | 수정 필요 | 실제 목록·상세 화면이 공유하는 상태 컴포넌트와 5개 회귀 테스트 추가 | `npm.cmd run build` 통과 |
| [상세 이름 maxLength](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3712320621) | `maxLength`를 100에서 50으로 변경 | 애플리케이션 | 수정 불필요 | 100을 유지 | HTML 길이는 UTF-16 코드 단위, 계약과 검증은 유니코드 코드 포인트 기준이다. 50개 보조 평면 문자는 100 코드 단위가 필요하며 51개 입력은 제출 검증이 차단한다. |
| [목록 이름 maxLength](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3712321812) | `maxLength`를 100에서 50으로 변경 | 애플리케이션 | 수정 불필요 | 100을 유지 | 위와 동일하며 `collections-coordination.test.ts`가 50/51 코드 포인트 경계를 검증한다. |
| [누락 회원 잠금](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3712322794) | 없는 회원 조회가 JDBC 예외로 500이 되지 않도록 처리 | 데이터베이스 | 수정 필요 | 잠금 조회 결과가 비면 `AUTHENTICATION_REQUIRED`를 반환하도록 변경하고 통합 테스트 추가 | GitHub Actions 백엔드 전체 빌드·테스트 통과 |
| [맛집별 컬렉션 상태](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3717026163) | 각 컬렉션의 포함 여부·개수·추가 가능 여부를 표시하고 중복 추가를 막음 | 애플리케이션 | 수정 필요 | 리뷰어 결정에 따라 맛집 문맥 전용 옵션 API와 상태 UI, 성공·실패 후 재조회를 구현 | 실제 관계 수는 서버 상한 판정에만 사용하고 공개 수만 응답하며, 백엔드 개인화 테스트 47개와 프론트 96개가 통과했다. |
| [교차 도메인 조회 경계](https://github.com/team-youngkk/masit-on/pull/135#discussion_r3717373740) | Personal persistence의 타 도메인 테이블 접근과 Restaurant 공개 판정을 분리 | 애플리케이션 | 수정 필요 | 조회는 Personal 소유 Query Port와 `orchestration.infrastructure.query` Adapter로 옮기고, 회원 잠금은 Member 공개 Port로 위임하며 SQL 테이블 경계 테스트 추가 | Personal persistence 운영 소스가 Personal 소유 테이블만 참조하는지 자동 검증 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없는 회원으로 생성할 때 `EmptyResultDataAccessException`이 공통 오류 처리까지 전달될 수 있었다.
- 발생 환경: `feature/t-107-personal-collection`, Java 21, Next.js 16.
- 재현 조건: 존재하지 않는 회원 ID로 생성, 목록·상세 비정상 상태 진입, 또는 맛집 상세에서 컬렉션 추가 상태 조회.
- 실제 결과: 저장소 예외가 인증 오류로 변환되지 않았고 화면 완료 조건 테스트와 맛집별 포함·상한 상태 계약이 없었다. 옵션 계약을 추가한 뒤에는 Personal 쓰기 Adapter가 Restaurant 테이블과 공개 판정을 직접 SQL로 복제했다.
- 기대 결과: 인증 오류를 안정적으로 반환하고 화면 상태를 자동 검증하며 컬렉션별 추가 가능 여부를 서버 기준으로 동기화한다.
- 영향 범위: 개인 컬렉션 생성·조회 API와 목록·상세·맛집 추가 화면의 회귀 안전성.

## 4. 근본 원인

회원 잠금에 단건 반환을 강제하는 `queryForObject`를 사용해 행이 없다는 정상적인 경계 상태가 JDBC 예외가 됐다. 프론트 테스트는 요청 조정 순수 함수만 다뤄 렌더링 완료 조건이 테스트 경계 밖에 있었다. 맛집별 포함 상태는 기존 목록 응답에 현재 맛집과 실제 관계 수 문맥이 없어 정확히 계산할 수 없었다. 리뷰어가 기존 목록을 유지하고 전용 옵션 API를 추가하기로 결정하면서 계약 공백이 해소됐다.

옵션 API를 최소 변경으로 추가하면서 기존 `JdbcPersonalCollectionAdapter`의 목록·상세 조회 패턴을 그대로 확장한 것이 교차 도메인 경계 위반의 근본 원인이었다. 같은 Adapter가 회원 행의 존재만 직접 잠가 Member의 활성 상태 규칙도 우회했다. Java import만 검사하는 ArchUnit 규칙으로는 문자열 SQL의 타 도메인 테이블 접근과 공개 판정 복제를 찾을 수 없었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 미해결 리뷰 스레드 GraphQL 조회 | 최초 5건, 후속 결정 답글 1건 확인 | 전 건을 수정 필요·수정 불필요로 최종 분류 |
| 사용자 흐름과 개인 컬렉션 API 비교 | 목록 응답에 현재 맛집 포함 여부 없음 | 기존 목록을 바꾸지 않고 `API-COLLECTION-008`을 추가 |
| 이름 길이 검증과 브라우저 `maxLength` 비교 | 코드 포인트 50자는 UTF-16 최대 100단위 | `maxLength={100}` 유지 |
| JDBC 통합 테스트 실행 | 최초 Docker 환경 탐색 실패, 재실행에서는 통과 | 공개 수·실제 수 분리와 상태 우선순위를 PostgreSQL로 검증 |
| 백엔드 테스트와 프론트 빌드 병렬 실행 | Next.js 페이지 수집 워커가 오류 메시지 없이 종료 | 자원 경합을 피한 단독 재실행에서 프로덕션 빌드 통과 |
| 아키텍처 계약과 Personal JDBC SQL 대조 | 조회 4개 경로가 Restaurant 테이블을, 생성 경로가 `member_account`를 직접 참조 | 읽기는 Personal Query Port와 orchestration Adapter로, 회원 활성 잠금은 Member 공개 Port로 분리하고 소스 SQL 경계 검증 추가 |
| 전체 빌드 출력 대기 만료 후 `clean build` 중복 실행 | 첫 빌드의 테스트 클래스가 두 번째 `clean`에 삭제돼 `NoClassDefFoundError`와 Mockito 로딩 실패가 연쇄 발생 | 잔여 프로세스 종료를 확인하고 단일 `clean build`로 재실행해 전체 607건 통과 |

## 6. 최종 해결

- 변경 내용: 화면 상태 테스트와 누락 회원 오류 변환에 더해 `GET /api/me/collection-options`와 `AVAILABLE`, `ALREADY_INCLUDED`, `LIMIT_REACHED` 상태를 추가했다. 프론트는 상태별 표시·비활성화와 성공·실패 후 재조회를 수행한다. 후속 재리뷰에서는 Restaurant 상태가 필요한 목록·옵션·상세 조회를 Personal Query Port와 orchestration 읽기 Adapter로 옮기고, 회원 활성 잠금은 Member 공개 Port에 위임해 쓰기 Adapter에는 Personal 관계 잠금과 변경만 남겼다.
- 선택 이유: 기존 컬렉션 목록 계약을 보존하면서 단일 집계 조회에서 공개 수·실제 수·포함 여부를 분리하면 비공개 관계 수를 노출하지 않고도 정확한 상한 상태를 제공할 수 있다.
- 변경 파일: 개인 컬렉션 API·추적 문서, `personal` Application/Presentation/JDBC 쓰기 Adapter와 Query Port, `orchestration.infrastructure.query` 읽기 Adapter와 경계 테스트, `frontend/lib/member/collections*`, `CollectionAddControl`과 옵션 렌더 테스트.
- 고려한 대안: `maxLength={50}`은 유효한 보조 평면 문자 50개를 25개에서 잘라 제외해 채택하지 않았다. 기존 목록 응답 확장과 컬렉션 상세 반복 조회는 계약 오염, 페이지네이션과 비공개 관계 때문에 채택하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `npm.cmd run build` | 통과 | 관리자 검토 충돌 해결까지 포함한 프론트 105개 테스트, TypeScript 검사, Next.js 프로덕션 빌드 |
| `.\gradlew.bat test --tests "com.masiton.architecture.PersonalPersistenceSqlBoundaryTest" --tests "com.masiton.personal.*" --tests "com.masiton.orchestration.infrastructure.query.PersonalCollectionQueryAdapterIntegrationTest"` | 통과 | SQL 경계, Application·MockMvc·PostgreSQL Projection을 포함한 관련 테스트 56개 |
| `.\gradlew.bat clean build` | 통과 | 알림 기능 PR #137 병합 기준 전체 백엔드 테스트 644개, 컴파일, 패키징 |
| `.\gradlew.bat compileJava compileTestJava test --tests ...` | 통과 | 관리자 검토 PR #138 병합 뒤 컴파일과 관련 테스트 56개 재검증 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| [GitHub Actions CI](https://github.com/team-youngkk/masit-on/actions/runs/30970898205) | 통과 | Projection 분리, 최신 `develop` 병합과 충돌 해결을 포함한 최종 HEAD에서 백엔드 전체 빌드·테스트와 프론트엔드 빌드·타입 검사를 검증했다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 화면 상태 5종, 없는 회원 생성, 옵션 상태 3종, 공개 수와 실제 수 분리, 성공·실패 후 재조회와 Personal persistence SQL 테이블 경계를 회귀 테스트로 고정했다.
- 다음 확인: 리뷰 답글에 변경·검증 근거를 연결하고 해당 스레드를 해결한 뒤 새 피드백 여부를 다시 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 화면 완료 상태 자동 검증 | 0개 | PR 테스트 실행 | 5개 | 정상·빈 상태·인증·404·재시도를 자동 검증 | PR #135 작성자, CI 완료 시 |
| 없는 회원 생성의 미변환 JDBC 예외 경로 | 1개 | 저장소 통합 테스트 | 0개 | 인증 오류로 변환하고 CI 통과 | PR #135 작성자, 2026-08-05 |
| 컬렉션 추가 옵션 UI 자동 검증 | 0개 | 프론트 테스트 실행 | 8개 | 상태 표시·선택·비활성화와 성공·실패 후 재조회를 자동 검증 | PR #135 작성자, 2026-08-05 |
| Personal persistence의 타 도메인 테이블 직접 참조 | 조회·생성 5개 경로 | 운영 소스 SQL 테이블 경계 테스트 | 0개 | 조회는 orchestration Projection으로, 회원 잠금은 Member 공개 Port로 이동 | PR #135 작성자, 2026-08-05 |

## 10. 남은 사항

- 요청된 변경의 구현과 로컬·원격 자동화 검증은 완료했으며, 리뷰어의 최종 승인만 남아 있다.
- 상태 전이 자체를 마운트하는 테스트 도구는 현재 없으므로 성공·실패 후 재조회와 선택 계산은 순수 함수로, 옵션 상태 표시는 서버 렌더 컴포넌트로 각각 검증했다.
