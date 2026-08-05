---
related_documents:
  - README.md
  - pr-139-popular-restaurant-security-boundary.md
  - pr-141-admin-curation-review.md
  - ../04-product/prd/curation/admin-curation.md
  - ../05-specs/api/curation/curation-api.md
  - ../05-specs/api/common/second-expansion-contract.md
  - ../06-architecture/query-composition.md
  - ../06-architecture/security-boundary.md
---

# PR #142 리뷰 트러블슈팅: 공개 큐레이션 조회 계약과 화면 상태 보완

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#142](https://github.com/team-youngkk/masit-on/pull/142) |
| 작성자 | `inan0226` |
| 처리 일자 | 2026-08-05 |
| 범위 | 미해결 리뷰 스레드 13건의 재현, 수정, 회귀 검증 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #141](pr-141-admin-curation-review.md)의 불투명 식별자·Restaurant 공개 참조 Port 원칙과 [PR #139](pr-139-popular-restaurant-security-boundary.md)의 완전 공개 조회 Bearer 미해석 원칙을 재사용했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [보안 문서 동기화](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718565167) | 큐레이션 공개 GET과 Bearer 미해석 경계 기록 | 애플리케이션 | 수정 필요 | 인기·큐레이션 공개 경로를 보안 경계에 추가 | 문서·Security 테스트 대조 |
| [4인자 생성자 제거](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718565168) | `roadAddress=null` 편의 생성자 제거 | 애플리케이션 | 수정 필요 | 오버로드 삭제, 테스트 호출부를 5인자로 변경 | 컴파일·서비스 테스트 |
| [조회 조합 결정 기록](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718565172) | Curation Application의 Restaurant 참조 조합 근거 명시 | 애플리케이션 | 수정 필요 | 제한 목록의 일괄 공개 입력 Port 조합 규칙 추가 | 문서·현재 구현 대조 |
| [상태 heading 여백](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718565178) | `.state`의 `h1`·`h3` margin 초기화 | 애플리케이션 | 수정 필요 | 선택자에 `h1`·`h3` 추가 | 프런트 빌드 |
| [재시도 동작](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718565180) | 현재 URL Link를 실제 재조회 버튼으로 변경 | 애플리케이션 | 수정 필요 | `router.refresh()` 기반 `RetryButton`으로 목록·상세 오류 상태 교체 | 타입 검사·프런트 빌드 |
| [게시 없음 테스트](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718565184) | 빈 `items` Controller 경계 검증 | 애플리케이션 | 수정 필요 | `200`·`items=[]`·`no-store` 테스트 추가 | Controller 테스트 |
| [미사용 생성자](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718575580) | 4인자 생성자 제거 | 애플리케이션 | 수정 필요 | 중복 요청과 같은 수정으로 반영 | 컴파일·서비스 테스트 |
| [Collectors import](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718575587) | 인라인 완전정규명 제거 | 애플리케이션 | 수정 필요 | 일반 import로 정리 | 컴파일 |
| [Comparator import](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718575592) | 인라인 완전정규명 제거 | 애플리케이션 | 수정 필요 | 일반 import로 정리 | 컴파일 |
| [잘못된 식별자 화면 상태](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718581710) | `400 INVALID_IDENTIFIER`를 찾을 수 없음으로 처리 | 애플리케이션 | 수정 필요 | `not-found` 분기와 회귀 테스트 추가 | 프런트 테스트 8건 |
| [생성자 계약 중복 지적](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718581713) | 4인자 오버로드 제거 | 애플리케이션 | 수정 필요 | 같은 원인의 세 스레드를 한 수정으로 반영 | 컴파일·서비스 테스트 |
| [테스트 표시명](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718581714) | `상세은`을 `상세는`으로 수정 | 기타 | 수정 필요 | 자연스러운 한글 문장으로 정정 | 통합 테스트 |
| [공개 캐시 헤더](https://github.com/team-youngkk/masit-on/pull/142#discussion_r3718645383) | 목록·상세 성공 응답에 `no-store` 적용 | 애플리케이션 | 수정 필요 | 두 응답에 `Cache-Control: no-store`와 회귀 테스트 추가 | Controller 테스트 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `INVALID_IDENTIFIER`가 일시적 조회 오류로 표시되고, 오류 화면의 "다시 시도"가 현재 URL Link로 렌더링됨
- 발생 환경: Windows, Java 21, Next.js 16 App Router, `feature/t-111-public-curation`
- 재현 조건: UUID가 아닌 상세 경로, 일시적 공개 API 오류, 게시·비게시 또는 구성 변경 뒤 캐시 가능한 공개 조회, `roadAddress` 없는 4인자 참조 생성
- 실제 결과: 영구 입력 오류에 재시도를 안내하고 재조회 동작은 보장되지 않았으며, 공개 성공 응답은 캐시 금지 계약을 적용하지 않았다. 공유 참조 Port에는 `roadAddress=null`을 만들 수 있는 오버로드가 남았다.
- 기대 결과: 영구 식별자 오류는 찾을 수 없음으로 닫고, 일시적 오류는 실제 재조회를 실행하며, 공개 큐레이션 응답은 저장되지 않아야 한다. 공개 표시 참조는 주소를 명시적으로 제공해야 한다.
- 영향 범위: 공개 큐레이션 목록·상세 API와 화면, Restaurant 공개 참조 Port, 보안·조회 조합 문서

## 4. 근본 원인

공개 화면 상태와 서버 응답 정책을 각각 구현하면서 2차 확장 공통 계약의 캐시 헤더와 기존 상세 화면의 불투명 식별자 결정을 함께 적용하지 않았다. 오류 화면은 탐색 Link를 재시도 제어로 재사용해 실제 데이터 재조회 책임이 드러나지 않았다. 또한 `roadAddress` 추가 시 기존 테스트 호출을 유지하려고 호환 오버로드를 남겨, 공개 응답 필수 표시값을 `null`로 만들 수 있는 경로가 생겼다. 보안·조회 조합 문서는 구현과 동시에 갱신되지 않아 결정 근거의 역추적성이 끊겼다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR #141 기록과 식별자 계약 대조 | 클라이언트가 내부 UUID 형식을 전제하지 않고 영구 형식 오류는 미존재와 같이 다루는 선례 확인 | `INVALID_IDENTIFIER`를 `not-found`로 통합 |
| PR #139 기록과 Security 코드 대조 | 큐레이션은 회원 부수효과 없는 완전 공개 조회로 이미 구현됨 | 코드 변경 없이 권위 문서 동기화 |
| `RestaurantReference` 호출부 검색 | 4인자 호출은 테스트 한 곳뿐이고 운영 구현은 5인자 사용 | 오버로드 삭제 후 테스트 값을 명시 |
| 공통 2차 확장 API 계약 대조 | 공개 조회에 `Cache-Control: no-store` 필수 | 목록·상세 성공 응답과 테스트에 헤더 추가 |
| 오류 화면의 Link 동작 추적 | 현재 URL을 가리킬 뿐 명시적 재조회 호출이 없음 | `router.refresh()`를 호출하는 Client Component 도입 |

## 6. 최종 해결

- 변경 내용: 공개 응답 `no-store`, 빈 목록·헤더 테스트, `INVALID_IDENTIFIER` 화면 분기, 실제 재시도 버튼, 상태 heading 여백, 참조 Port 생성자·import·테스트 문구 정리, 보안·조회 조합 문서 동기화
- 선택 이유: 기존 계약과 PR #139·#141에서 검증된 경계 결정을 재사용하면서 각 리뷰 요청을 최소 변경으로 해결하기 위해서다.
- 변경 파일: `PublicCurationController`, `PublicCurationService`, `FindRestaurantReferenceUseCase`, 관련 백엔드 테스트, `frontend/app/curations/**`, `frontend/lib/curations-api*`, `security-boundary.md`, `query-composition.md`
- 고려한 대안: 오류 화면에서 전체 페이지 강제 reload를 호출할 수 있으나 App Router의 명시적 RSC 재조회인 `router.refresh()`가 현재 화면 상태를 유지하는 더 작은 변경이다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\\gradlew.bat compileJava compileTestJava` | 통과 | 생산·테스트 소스 컴파일과 생성자 호출부 정합성 |
| `.\\gradlew.bat test --tests com.masiton.curation.* --tests com.masiton.restaurant.application.RestaurantReferenceQueryServiceTest --tests com.masiton.security.infrastructure.configuration.SecurityConfigurationApiTest` | 통과 | 공개 큐레이션 서비스·API·PostgreSQL·Restaurant 참조·보안 회귀 |
| `node --test lib/curations-api.test.ts` | 통과 | `INVALID_IDENTIFIER` 포함 공개 API 분기 8건 |
| `npm.cmd run typecheck` | 통과 | `RetryButton`과 Server/Client Component 타입 경계 |
| `npm.cmd run build` | 통과 | 프런트 전체 테스트·타입 검사·Next.js 운영 빌드 |
| `git diff --check` | 통과 | 공백 오류 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 공개 목록·상세의 `Cache-Control`, 게시 없음 빈 목록, 잘못된 식별자 화면 상태를 회귀 테스트로 고정했다. 보안·조회 조합 문서에 큐레이션 경계를 명시했다.
- 다음 확인: 없음.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| `no-store`가 검증된 공개 큐레이션 성공 경로 | 0/2 | Controller API 테스트 | 2/2 | 목록·상세 캐시 금지 고정 | PR #142 병합 전 |
| 실제 재조회 동작이 있는 오류 CTA | 0/2 | 목록·상세 소스와 프런트 빌드 | 2/2 | 자기 자신 Link 제거 | PR #142 병합 전 |
| `roadAddress=null` 4인자 생성 경로 | 1개 오버로드 | 정적 검색·컴파일 | 0개 | 필수 표시값 누락 경로 제거 | PR #142 병합 전 |

## 10. 남은 사항

없음. 최초 미해결 스레드 13건을 모두 반영했다.
