---
related_documents:
  - ../05-specs/api/discovery/restaurant-discovery-api.md
  - ../05-specs/api/common/filtering-contract.md
  - ../../src/main/java/com/masiton/restaurant/presentation/rest/RestaurantSearchController.java
  - ../../src/main/java/com/masiton/restaurant/application/query/RestaurantSearchQueryService.java
  - ../../src/main/java/com/masiton/restaurant/infrastructure/persistence/RestaurantSearchQueryAdapter.java
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #395 리뷰 트러블슈팅: 목록 API 태그 필터 누락

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#395](https://github.com/team-youngkk/masit-on/pull/395) |
| 작성자 | inan0226 |
| 처리 일자 | 2026-10-07 |
| 범위 | 최신 `develop` 병합, `GET /api/restaurants` 단일 `tag` 필터 연결, 프론트 CI에서 확인된 취약 전이 의존성 갱신 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [트러블슈팅 인덱스](README.md), PR #389 재검증 기록 확인. 동일한 태그 목록 API 문제 기록은 없으며 PR #389 기록은 다른 DB 상태 전이 문제라 직접 적용할 해결책은 없었다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [목록 API `tag` 미지원](https://github.com/team-youngkk/masit-on/pull/395#discussion_r4202667475) | 프론트가 지원되지 않는 `tag`를 전달해 400을 받으므로 계약에 맞춰 백엔드를 연결하거나 프론트 지원을 제거 | 애플리케이션 | 수정 필요 | API 계약에 이미 정의된 단일 `tag`를 컨트롤러에서 검증·전달하고 서비스에서 활성 코드인지 확인하도록 수정 | `RestaurantSearchController`가 파라미터를 거부하고 `SearchRestaurantsCommand`에 빈 태그 목록만 넘기는 것을 확인. 컴파일과 테스트 소스 컴파일 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `INVALID_REQUEST` (HTTP 400)
- 발생 환경: PR #395의 맛집 목록 화면, `/api/restaurants?tag={활성태그코드}` 요청
- 재현 조건: URL 또는 폼이 활성 태그 코드를 단일 `tag`로 전달
- 실제 결과: 컨트롤러의 허용 파라미터 목록에 `tag`가 없어 요청이 거부됨
- 기대 결과: 공개·유효 방문에 활성 태그가 연결된 맛집만 반환하고, 유효한 무결과는 빈 목록으로 반환
- 영향 범위: 태그가 포함된 검색 URL과 태그 유지 경로에서 맛집 목록 조회 실패

추가로 최신 `develop`를 병합한 뒤 프론트엔드 CI의 `npm audit --omit=dev --audit-level=high`가 실패했다. 감사 결과 `sharp 0.35.4`와 `source-map-js 1.2.1`이 각각 HIGH 취약 범위에 포함됐고, 같은 develop 커밋에서도 동일한 감사 실패가 확인됐다.

## 4. 근본 원인

프론트엔드와 [맛집 탐색 API 계약](../05-specs/api/discovery/restaurant-discovery-api.md)은 단일 `tag` 필터를 사용하지만, `RestaurantSearchController`의 `KNOWN_FIELDS`에서 이를 누락했고 `SearchRestaurantsCommand` 생성 시 태그 목록을 항상 비워 전달했다. Query Adapter에는 활성 태그·공개 유효 Visit 기준 필터가 이미 있었지만 요청 경로에 연결되지 않았다. 활성 코드 확인 메서드도 자연어 검색에서만 호출되어 직접 목록 필터의 미등록·비활성 코드를 400으로 거부하지 못했다.

최신 npm 감사 데이터에 새 advisory가 반영되면서 기존 `sharp`와 `source-map-js` override 값이 더 이상 수정 버전에 해당하지 않았다. Accepted [ADR-WEB-007](../07-adr/platform/web-007-next-security-patch-baseline.md)의 보안 감사 게이트가 이 취약점 때문에 실패했다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR diff와 리뷰 인라인 문맥 확인 | 프론트 요청 파라미터와 백엔드 컨트롤러 허용 필드가 불일치 | 리뷰 현상을 코드 경로로 확인 |
| API 계약 및 검색 Query Adapter 확인 | 계약은 단일 `tag`와 활성 태그·공개 Visit 조건을 정의하고 Query Adapter는 필터 SQL을 이미 구현 | 계약 변경이나 필터 제거 대신 기존 기능 경로를 연결 |
| 최신 `origin/develop` 반영 | ADR 문서 5곳에서 보안 패치 ADR 상태 충돌; develop에서 확정된 Accepted 상태와 문구로 해결 | 최신 기반선 유지 |
| PR #395 및 최신 develop CI 로그 | `sharp 0.35.4`, `source-map-js 1.2.1`의 새 HIGH advisory로 감사 실패; 백엔드 빌드·테스트, Terraform, RSA 검사는 통과 | 보안 ADR의 감사 기준에 맞춰 두 의존성을 수정 버전으로 갱신 |
| GitHub Advisory Database 확인 | `sharp` 수정 버전은 0.35.5, `source-map-js` 수정 버전은 1.2.2 | 해당 버전만 npm override와 lockfile에 반영 |

## 6. 최종 해결

- 변경 내용: 컨트롤러 허용 필드에 `tag`를 추가하고 단일 값을 Application Command의 태그 목록으로 전달했다. Application Service에서 요청된 코드가 모두 활성인지 먼저 검증해 비활성·미등록 값은 `INVALID_FIELD_VALUE(tag)`로 거부한다. npm `overrides`를 `sharp 0.35.5`, `source-map-js 1.2.2`로 갱신하고 기술 정책과 ADR 검증 버전을 동기화했다.
- 선택 이유: API 계약이 이미 지원하는 동작이며 Query Adapter에 공개 Visit와 활성 태그를 적용하는 필터가 있으므로 현재 구조에 연결하면 된다.
- 변경 파일: `src/main/java/com/masiton/restaurant/presentation/rest/RestaurantSearchController.java`, `src/main/java/com/masiton/restaurant/application/query/RestaurantSearchQueryService.java`, `src/test/java/com/masiton/restaurant/application/query/RestaurantSearchQueryServiceTest.java`, `frontend/package.json`, `frontend/package-lock.json`, `docs/06-architecture/technology-policy.md`, `docs/07-adr/platform/web-001-frontend-platform.md`, `docs/troubleshooting/pr-395-restaurant-tag-filter.md`, `docs/troubleshooting/README.md`

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat compileJava compileTestJava --no-daemon --console=plain` | 통과 | 애플리케이션과 테스트 소스 컴파일 성공 |
| `git diff --check` | 통과 | 공백 오류 없음 |
| `npm audit --omit=dev --audit-level=high` | 통과 | 0 vulnerabilities |
| `npm ls next typescript sharp source-map-js` | 통과 | Next 16.3.8, TypeScript 7.0.2, sharp 0.35.5, source-map-js 1.2.2 확인. 로컬 Node는 24.14.0이며 프로젝트 기준 24.18.0과 차이 있음 |
| PR CI `37579600027` | 백엔드·Terraform·RSA 통과, 프론트 보안 감사 실패 | 전이 의존성 갱신 전 실행. 새 버전 반영 후 CI 재실행 필요 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 기존 API 계약과 Query Adapter 기능 사이의 호출 경로를 연결하고 기존 태그 Criteria 단위 테스트에서 활성 태그 조회를 명시적으로 stub 했다. 프론트 보안 기준선 문서와 npm override 버전을 같은 변경에서 갱신했다.
- 다음 확인: 수정한 의존성을 포함한 PR CI에서 프론트 빌드·감사와 전체 백엔드 검사가 통과하는지 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 해당 없음 | 해당 없음 | 요청 경로 결함 수정으로 제품 성능 지표 비교 대상이 아님 | 해당 없음 | 해당 없음 | 해당 없음 |

## 10. 남은 사항

- 수정 버전 반영 후 실행되는 PR CI 결과를 확인해야 한다.
