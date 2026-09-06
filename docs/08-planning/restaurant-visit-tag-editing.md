---
related_documents:
  - ../04-product/prd/detail/restaurant-detail.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/restaurant-visit-tags-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - ../07-adr/security/auth-007-unified-account-rbac-session.md
---

# 맛집 상세의 관리자 방문 태그 조회·수정

## 배경과 범위

2026-09-05 사용자 요청으로 맛집 상세(`/restaurants/{id}`)에서 관리자가 현재 검색 태그를 확인하고 수정하는 기능을 구현한다. FR-AIEXTRACT-007의 사후 태그 보정과 FR-NLSEARCH-004의 확정 태그 검색을 연결한다. 별도 관리자 맛집 목록, 태그 정의 생성, 자연어 사전 확대, 검색 알고리즘 변경은 포함하지 않는다.

공개 상세는 기존 서버 렌더링과 무인증 조회를 유지한다. ADMIN 세션에서만 관리 패널을 표시하고 별도 관리자 API를 호출한다. 전역 MemberSessionProvider에 이미 있는 TanStack Query를 사용한다. 이전 검토에서 관리자 레이아웃에만 Provider가 있다고 설명한 부분은 현재 코드 확인 결과와 달라 정정한다.

## 사용자 흐름과 완료 조건

1. ADMIN이 맛집 상세를 열면 방문별 영상·유튜버·현재 태그를 확인한다. 태그가 없는 방문도 표시한다.
2. 방문별 편집에서 기존 활성 태그 정의를 선택·해제하고 보정 사유를 입력한다. 자유 태그 생성은 제공하지 않는다.
3. 저장/취소를 명시하고 저장 중 중복 제출을 막는다. 서버 성공 후 태그를 재조회한다.
4. 다른 수정 또는 연결 변경이 있으면 409로 거절하고 최신 값을 다시 조회한 뒤 재편집하도록 안내한다. 편집 중 자동 조회가 입력을 조용히 덮어쓰지 않는다.
5. 익명·MEMBER에는 패널과 관리자 요청이 없고, 세션 전환 시 이전 관리자 캐시·편집 상태를 재사용하지 않는다. 최종 인가는 서버의 ADMIN 경계다.
6. 공개·활성이며 영상/유튜버가 이용 가능한 방문만 이 상세 패널에서 편집한다. 다른 맛집의 방문 수정은 404다.
7. 태그 변경과 변경 전후·사유·관리자·시각의 감사 이력은 같은 트랜잭션으로 저장한다. 실패 시 모두 롤백한다.

## 검색 의미

태그는 Restaurant가 아니라 Visit에 연결한다. 여러 조건은 동일한 유효 Visit 안에서 AND로 만족해야 한다. 화면은 방문별 태그를 보여주며 서로 다른 방문의 태그를 합쳐 하나의 검색 근거로 표현하지 않는다. 검색은 다음 요청부터 변경된 DB 연결을 사용한다. 이미 표시된 별도 검색 화면을 실시간 갱신하는 기능은 범위 밖이다.

## 구현과 검증

- backend: Visit 소유 application port/service, JDBC adapter, ADMIN controller. 기존 적용 마이그레이션을 수정하지 않고 V9 감사 테이블을 추가한다.
- frontend: 공개 상세의 관리자 전용 client 패널, 기존 인증 fetch와 TanStack Query query/mutation 재사용.
- 검증: 인증/인가, 소속 불일치, 빈 태그, 비활성·중복 태그, stale 동시 저장, 감사 원자성/불변성, 검색 반영, 프론트 입력·오류·세션 경계, 컴파일/타입 검사와 관련 회귀 테스트.
- 사용자에게 문서화·이슈·구현·PR 생성 요청을 받았다. 별도 팀원의 API/데이터 소유자 승인을 받은 것으로 기록하지 않으며, 해당 경계의 병합 전 리뷰가 필요하다.

## 실행 기록

- 이슈: [#358](https://github.com/team-youngkk/masit-on/issues/358), 브랜치: codex/restaurant-visit-tags, 대상: develop.
- 로컬 프론트 npm run build 통과: 자연어 17개 + 전체 337개 테스트, 명시적 tsc, Next 프로덕션 빌드. 호스트 Node는 24.19.0이며 저장소 버전은 바꾸지 않았다.
- 로컬 Java 소스·테스트 컴파일 및 VisitTagServiceTest 2개·ArchitectureTest 10개 통과.
- Edge headless 브라우저의 로컬 응답 fixture로 ADMIN 조회·저장·취소·409 최신 조회, 익명/MEMBER 관리자 요청 0건, 390px 가로 넘침 없음과 pageerror 0건을 확인했다. 실제 백엔드 연동 인수 검증과 구분한다.
- 로컬 PostgreSQL/Testcontainers 실행은 Docker Desktop의 sailor-ingest.sock 접근 오류로 엔진 기동에 실패했다. CI의 Docker 환경에서 통합 검증을 이어간다.
- 독립 기능·보안 리뷰에서 잘못된 식별자 400 처리 및 탈퇴 시 감사 행위자 익명화 보완을 확인했다. 팀원의 병합 승인은 별도다.
