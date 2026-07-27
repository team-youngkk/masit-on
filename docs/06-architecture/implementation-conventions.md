---
related_documents:
  - README.md
  - technology-policy.md
  - architecture-overview.md
  - package-structure.md
  - dependency-rules.md
  - transaction-boundaries.md
  - query-composition.md
  - external-integration.md
  - ../05-specs/api/README.md
  - ../05-specs/data/migration-plan.md
  - ../07-adr/quality/test-001-automation-strategy.md
  - ../02-analysis/mvp-workstreams.md
  - ../03-team/roles.md
---

# 구현 컨벤션 및 공통 정책

## 1. 목적과 적용 우선순위

이 문서는 맛잇온 1차 MVP에 참여하는 네 명의 개발자와 AI 에이전트가 공통으로 적용할 구현 규칙을 한곳에서 확인하기 위한 기준이다. 상세 설계나 계약을 이 문서에 중복 정의하지 않고 권위 있는 원문을 연결한다.

규칙 충돌 시 다음 순서로 적용한다.

1. 확정된 요구사항과 1차 MVP 범위
2. Accepted ADR
3. API·데이터 계약
4. 아키텍처 상세 설계
5. 이 구현 컨벤션
6. 외부 코딩 컨벤션

## 2. 아키텍처와 패키지

- 단일 모듈 도메인 중심 계층형 모놀리스를 사용한다.
- 계층은 Presentation, Application, Domain, Infrastructure로 나눈다.
- Controller는 Application 입력 Port만 호출한다.
- Application이 영속성이나 외부 API를 사용할 때는 자신이 소유한 출력 Port를 호출한다.
- Infrastructure Adapter가 출력 Port를 구현한다.
- Domain은 Spring, JPA, 외부 제공자 SDK에 의존하지 않는다.
- 다른 도메인의 Entity나 Repository를 직접 참조하지 않는다. 공개 Port 또는 `orchestration`을 사용한다.
- Java 루트 패키지는 `com.masiton`을 사용한다.
- 세부 패키지 위치는 [패키지 구조](package-structure.md), 허용 의존성은 [의존성 규칙](dependency-rules.md), 도메인 소유권은 [모듈 경계](module-boundaries.md)를 따른다.

## 3. Java 코딩 컨벤션

- 기본 Java 스타일은 NAVER의 [캠퍼스 핵데이 Java 코딩 컨벤션](https://naver.github.io/hackday-conventions-java/)을 사용한다.
- 저장소에 Java 빌드가 추가될 때 NAVER 가이드의 `.editorconfig`, IDE Formatter와 Checkstyle 설정을 함께 적용한다.
- NAVER 컨벤션과 이 프로젝트의 아키텍처·Spring/JPA·테스트 규칙이 충돌하면 프로젝트 규칙을 우선한다.
- 자동 포매팅 또는 정적 분석 결과만 수정하는 변경은 업무 로직 변경과 분리한다.
- 컨벤션 예외가 필요하면 PR 설명에 예외 규칙과 이유를 기록한다.

## 4. Spring·JPA

### 4.1 트랜잭션

- 트랜잭션은 Application Service의 public 메서드에서 시작한다.
- 조회 유스케이스에는 `@Transactional(readOnly = true)`를 적용한다.
- Controller와 Repository 구현체에서 업무 트랜잭션을 시작하지 않는다.
- 외부 HTTP 호출 중에는 DB 트랜잭션을 열지 않는다.
- 세부 경계와 동시성 규칙은 [트랜잭션 경계](transaction-boundaries.md)를 따른다.

### 4.2 영속성 설정

- OSIV를 비활성화한다.
- Hibernate `ddl-auto`는 `validate`로 설정한다.
- 모든 스키마 변경은 Flyway로 수행한다.
- 이미 적용된 Flyway 파일은 수정하지 않고 새 마이그레이션을 추가한다.

### 4.3 Entity

- Entity에 공개 setter와 Lombok `@Data`를 사용하지 않는다.
- Entity 생성은 생성자 또는 정적 팩터리를 사용한다.
- 상태 변경은 의도를 드러내는 도메인 메서드로 수행한다.
- Entity의 Lombok 사용은 `@Getter`, `@NoArgsConstructor(access = PROTECTED)` 수준으로 제한한다.
- 연관관계의 기본 Fetch 전략은 `LAZY`로 한다.
- 양방향 연관관계와 `ManyToMany`는 원칙적으로 사용하지 않는다.
- 다른 도메인의 Entity는 객체 연관관계로 연결하지 않고 식별자로 참조한다.
- API 요청과 응답에 Entity를 직접 노출하지 않는다.

### 4.4 Repository와 조회

- Spring Data Repository는 Infrastructure 계층 내부에서만 사용한다.
- `save()` 호출 전에 Application과 Domain의 업무 규칙을 검증한다.
- 목록 조회는 반드시 페이징하고 안정적인 정렬 기준을 명시한다.
- N+1 문제는 Query Adapter의 fetch join, EntityGraph 또는 Projection으로 해결한다.
- 여러 도메인의 조회 결과 조합은 [조회 조합](query-composition.md)의 책임과 제한을 따른다.

## 5. API와 데이터베이스

- API 경로, 응답, 오류, 식별자, 필터, 페이징과 날짜·시간 표현은 [API 계약](../05-specs/api/README.md)을 따른다.
- Controller는 요청 검증과 HTTP 변환만 담당하고 업무 규칙과 조회 조합을 소유하지 않는다.
- 테이블, 제약과 인덱스는 데이터 명세를 따르고 마이그레이션 파일 규칙은 [Flyway 마이그레이션 계획](../05-specs/data/migration-plan.md)을 따른다.
- API 계약이나 테이블을 변경하려면 관련 담당자와 먼저 합의하고 코드와 문서를 같은 PR에서 변경한다.

## 6. 테스트

### 6.1 도구와 이름

- JUnit 5, AssertJ와 Mockito를 사용한다.
- 단위 테스트 클래스는 `XxxTest`, 통합 테스트는 `XxxIntegrationTest`, API 테스트는 `XxxApiTest`로 명명한다.
- 테스트 메서드명은 `행위_조건_기대결과` 형식을 사용하고 `@DisplayName`은 자연스러운 한글 문장으로 작성한다.
- 테스트 본문은 Given-When-Then 구조를 사용한다.

### 6.2 계층별 범위

- 단위 테스트는 외부 저장소 없이 Domain과 Application 규칙을 검증한다.
- Repository, JPA 매핑, 제약과 트랜잭션은 PostgreSQL Testcontainers로 검증한다.
- Controller와 API 계약은 MockMvc로 검증한다.
- 외부 API Adapter의 HTTP 계약과 장애는 WireMock으로 검증한다.
- 외부 Port를 사용하는 Application 단위 테스트는 Fake 또는 고정 응답을 사용한다.
- 로컬과 자동화 테스트에서 실제 Kakao·YouTube API를 호출하지 않는다.

### 6.3 필수 규칙

- 각 기능은 정상, 예외와 경계 조건을 최소 한 건씩 검증한다.
- 중복 등록과 원자성은 동시 요청과 실패 후 부분 저장 0건을 검증한다.
- 테스트 간 실행 순서 의존을 금지한다.
- 공용 Fixture와 Builder는 `testFixtures` 또는 테스트 공통 패키지에서 관리한다.
- 비동기·시간 검증에서 `Thread.sleep()`이나 임의 실행 대기를 사용하지 않는다.
- 코드 커버리지 수치를 병합 기준으로 강제하지 않는다. 요구사항별 필수 시나리오 통과를 완료 기준으로 사용한다.
- 모든 PR은 변경 범위와 관련된 단위·통합 테스트를 통과해야 한다.
- 세부 테스트 계층과 실패 시나리오는 [ADR-TEST-001](../07-adr/quality/test-001-automation-strategy.md)을 따른다.

## 7. Git 협업

### 7.1 브랜치

- `main`은 배포 가능한 기준 브랜치, `develop`은 기능 통합 브랜치다.
- 기능과 수정 브랜치는 최신 `develop`에서 분기한다.
- 기능 브랜치는 `feature/ws-{번호}-{기능명}`, 수정 브랜치는 `fix/{기능명}` 형식을 사용한다.
- Workstream에 속하지 않는 작업은 커밋 유형과 같은 접두사를 사용한다. 접두사는 `docs/`, `chore/`, `build/`, `ci/`, `test/`, `refactor/`이며 뒤에 `{작업명}`을 붙인다.
- 구현 계획의 기반 Task처럼 Workstream 번호가 없는 기능 작업은 `feature/t-{번호}-{작업명}`을 사용한다. 예: `feature/t-02-web-foundation`
- `main`과 `develop`에 직접 push하지 않는다.

### 7.2 PR과 병합

- 모든 변경은 PR을 통해 병합한다.
- PR 본문 첫 줄에 `Closes #{이슈번호}`로 구현한 이슈를 연결한다. 기본 브랜치가 `develop`이므로 병합 시 해당 이슈가 자동으로 닫힌다. 닫을 이슈가 없으면 근거 문서로 대신한다.
- PR 본문에 담당자, 리뷰어와 레이블을 함께 적고 GitHub 사이드바에도 같은 값을 지정한다.
- PR 본문과 커밋 메시지에 AI 도구 생성 표기를 남기지 않는다. `Generated with Claude Code` 같은 문구, 도구 서명과 배지를 넣지 않는다.
- 기능·수정 브랜치에서 `develop`으로 병합할 때 일반 Merge를 사용한다.
- `develop`에서 `main`으로 병합할 때만 Squash Merge를 사용한다.
- 모든 PR은 작성자를 제외한 최소 두 명의 승인을 받아야 한다.
- 빌드와 관련 테스트를 통과하기 전에는 병합하지 않는다.
- API, DB, 인증 경계 또는 공유 설정 변경은 관련 소유자와 사전 합의하고 해당 소유자에게 리뷰를 요청한다.

### 7.3 커밋

- Conventional Commits 형식을 사용한다.
- 대표 유형은 `feat`, `fix`, `test`, `refactor`, `docs`, `build`, `ci`, `chore`다.
- 제목은 변경 목적이 드러나게 작성한다. 예: `feat: 맛집 목록 조회 구현`
- 서로 독립적인 변경은 별도 커밋과 PR로 분리한다.

## 8. 문서와 AI 구현

- 구현 전에 관련 요구사항, PRD, API, ADR와 테이블 정의를 확인한다.
- 구현 결과에서 관련 요구사항 ID와 근거 문서를 추적할 수 있어야 한다.
- AI는 요구사항, MVP 범위, API 계약과 DB 구조를 임의로 변경하지 않는다.
- 문서 간 충돌을 발견하면 임의 해석하거나 범위를 확장하지 않고 팀 결정을 요청한다.
- 요청 범위 밖의 리팩터링과 파일 변경을 하지 않는다.
- 새로운 라이브러리, 플러그인과 외부 서비스를 임의로 추가하지 않는다.
- 비밀키, 실제 인증정보와 개인정보를 코드, 테스트, 로그와 문서에 기록하지 않는다.
- 생성한 코드는 컴파일하고 관련 테스트를 실행한다. 검증하지 못한 항목을 완료로 보고하지 않는다.
- 실패한 테스트와 알려진 제약은 PR에 명시한다.
- 주석은 코드만으로 드러나지 않는 의도와 제약을 설명할 때만 사용한다.
- 임시 구현에는 담당자와 제거 조건이 없는 `TODO`를 남기지 않는다.
- AI가 작성한 코드도 동일한 PR, 테스트와 최소 두 명 승인 규칙을 적용한다.
- 코드와 문서가 달라지면 관련 문서를 같은 PR에서 동기화한다.

## 9. PR 완료 점검

- [ ] 구현한 이슈를 `Closes #{이슈번호}`로 연결하고 담당자·리뷰어·레이블을 지정했다.
- [ ] PR 본문의 변경 범위가 실제 diff와 일치한다.
- [ ] PR 본문과 커밋 메시지에 AI 도구 생성 표기가 없다.
- [ ] 관련 요구사항·PRD·API·ADR·테이블을 확인했다.
- [ ] NAVER Java 컨벤션과 프로젝트 우선 규칙을 준수했다.
- [ ] 계층, 도메인, 트랜잭션과 외부 Port/Adapter 경계를 지켰다.
- [ ] API·DB 계약 변경을 사전 합의하고 문서에 반영했다.
- [ ] 정상·예외·경계 조건과 필요한 통합 테스트가 통과했다.
- [ ] 실제 외부 API와 운영 비밀정보를 사용하지 않았다.
- [ ] 최소 두 명의 승인을 받았고 대상 브랜치의 병합 방식을 지켰다.
