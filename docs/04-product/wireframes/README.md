---
related_documents:
  - ../../00-overview/scope.md
  - ../prd/00-product-overview.md
  - ../prd/discovery/restaurant-discovery.md
  - ../prd/detail/restaurant-detail.md
  - ../prd/admin/admin-data-management.md
  - ../../05-specs/data/entity-definitions.md
  - ../../07-adr/platform/web-006-unified-login-rbac-route.md
  - second-expansion-wireframes.md
  - third-expansion-wireframes.md
---

# 맛잇온 와이어프레임 적용 기준

## 1. 목적

이 디렉터리는 맛잇온 화면의 시각적 기준을 공유한다. 원본 와이어프레임에는 MVP와 1차부터 3차까지의 확장 기능이 함께 표현되어 있으므로, 이미지에 있다는 이유만으로 기능 범위에 포함하지 않는다. 구현 범위는 항상 [프로젝트 범위](../../00-overview/scope.md)를 우선한다.

### 1.1 현재 저장소 기준 개요

현재 화면 구조의 기준은 [현재 애플리케이션 와이어프레임](current-application-wireframe.svg)이다. 이 이미지는 `frontend/app`의 현재 Route와 공통 헤더의 실제 메뉴를 바탕으로 공개 탐색, 회원 개인화, 관리자 운영 화면을 도형으로 배치한다.

| 영역 | 현재 기준 화면 | 핵심 Route |
|---|---|---|
| 공개 탐색 | 맛집 탐색·지도·인기·큐레이션·맛집 코스·맛집/유튜버 상세 | `/restaurants`, `/map`, `/popular`, `/curations`, `/course`, `/restaurants/{id}`, `/creators/{id}` |
| 회원 개인화 | 로그인·회원가입·내 정보·찜·최근 본 맛집·컬렉션·제보·신고·알림 | `/login`, `/signup`, `/me`, `/me/favorites`, `/me/recent-restaurants`, `/me/collections`, `/me/requests`, `/me/notifications` |
| 관리자 운영 | 대시보드·수동 등록·큐레이션·제보·신고 검토·AI 영상 추출·채널 감시 | `/admin`, `/admin/restaurants/new`, `/admin/creators/new`, `/admin/videos/new`, `/admin/visits/new`, `/admin/curations`, `/admin/participation`, `/admin/ai`, `/admin/ai/youtube-channel-watches` |

이 화면 와이어프레임은 기능 계약을 새로 정의하지 않는다. 각 화면의 상태·오류·권한·반응형 세부 기준은 해당 PRD, API 계약과 [2차 확장 와이어프레임](second-expansion-wireframes.md), [3차 확장 와이어프레임](third-expansion-wireframes.md)을 따른다.

## 2. 이미지별 단계 분류

| 이미지 | 화면 | MVP 적용 | 제외 또는 후속 단계 |
|---|---|---|---|
| [home-reference.png](home-reference.png) | 홈 | 헤더, 검색 영역, 맛집 카드의 간격·색상·타이포그래피, 푸터 | 지도, 최근 본 맛집, 로그인·회원가입, 찜, 인기·평점 |
| [map-detail-reference.png](map-detail-reference.png) | 지도와 맛집 정보 패널 | 맛집 이름·주소·카테고리와 방문 유튜버·영상 목록의 표현 방식 | 지도, 위치 마커, 길찾기, 평점·리뷰, 영업시간 |
| [creator-list-reference.png](creator-list-reference.png) | 유튜버 목록 | 녹색 계열과 카드 시각 스타일 참고만 허용 | 유튜버 목록·상세 화면, 구독자·리뷰 수는 MVP 제외 |
| [theme-curation-reference.png](theme-curation-reference.png) | 테마 큐레이션 | 시각 스타일 참고만 허용 | 테마·큐레이션·이메일 구독은 후속 확장 |
| [saved-list-reference.png](saved-list-reference.png) | 보관함 | 시각 스타일 참고만 허용 | 일반 사용자 인증, 찜, 보관함, 최근 본 맛집은 후속 확장 |

## 3. MVP 화면

### 공개 화면

- `/` 또는 `/restaurants`: 맛집 목록, 이름 검색, 서울 자치구·음식 카테고리·유튜버 필터, 페이지 이동
- `/restaurants/{restaurantId}`: 맛집 기본 정보, 카카오 장소 링크, 방문 유튜버와 관련 YouTube 영상
- 영상이 없는 맛집의 상세: 기본 정보는 표시하고 콘텐츠 영역은 정상 빈 상태로 표현

### 관리자 화면

- `/login`: 회원·관리자 공용 이메일·비밀번호 로그인. 역할 선택은 표시하지 않음
- `/admin/login`: 기존 링크 호환을 위해 `/login?returnTo=/admin`으로 이동
- 메인 공통 헤더의 `/admin` 링크: 현재 역할이 정확히 `ADMIN`일 때만 표시
- 맛집·유튜버·영상 등록 화면: 검증 미리보기, 후보 확인과 등록 확정
- 방문 관계 등록 화면: 맛집·유튜버·영상 선택과 등록 결과 확인

관리자 화면의 세부별 별도 와이어프레임은 없으므로 공개 화면과 같은 색상, 타이포그래피, 입력 필드와 버튼 규칙을 사용한다. 현재 Route와 관리자 진입 관계는 위 개요도에서 확인한다.

2차 확장 화면의 정보 구조와 상태는 [2차 확장 와이어프레임](second-expansion-wireframes.md)에서 관리한다. 해당 문서는 초안이며 Workstream·담당자 승인 전 구현 기준으로 확정하지 않는다.

3차 확장 화면의 정보 구조와 상태는 [3차 확장 와이어프레임](third-expansion-wireframes.md)에서 관리한다. 특히 AI 영상 추출은 일반 사용자 화면이 아니라 자동 등록 결과와 관리자 예외 보정 화면의 정상·부분·오류·검증 충돌 상태를 우선한다. WS-14~WS-16과 담당자가 배정됐고 관련 API·데이터 계약이 승인됐으며 현재 운영 중이다. 브라우저 기준선과 현재 운영 완료 판정은 [3차 확장 운영 완료 기록](../../08-planning/third-expansion-operational-completion-record.md)과 [기준선 테스트 추적표](../../08-planning/third-expansion-test-matrix.md)에 연결한다.

## 4. MVP 시각 규칙

- 서비스명 표기는 **맛잇온**이다. 기존 참고 이미지의 `맛있온`은 오기이므로 따르지 않으며, 현재 기준 SVG와 코드·문서는 모두 맛잇온을 사용한다.
- 주요 색상은 와이어프레임의 녹색 계열을 사용한다.
- 데스크톱 헤더, 검색·필터 영역, 카드와 푸터의 시각적 위계를 유지하되 모바일에서는 세로 흐름으로 재배치한다.
- 제외 기능의 메뉴, 비활성 버튼이나 준비 중 링크를 노출하지 않는다.
- 로그인 UI는 `/login` 하나로 통합하고 회원·관리자 역할 선택을 두지 않는다. `/admin/login`에는 별도 폼을 만들지 않는다.
- 관리자 링크는 세션 역할이 정확히 `ADMIN`일 때만 표시한다. 링크 숨김은 보안 판정이 아니므로 모든 관리자 API 권한은 서버에서 다시 검증한다.
- 맛집 평점·리뷰·대표 이미지·영업시간은 확정 데이터 모델에 없으므로 표시하지 않는다.
- Restaurant 대표 이미지를 임의의 고정 이미지나 외부 URL로 대체하지 않는다. Video 썸네일은 확정 API 계약에 따라 맛집 상세의 관련 영상에서만 사용한다.
- 유튜버 필터에는 공개 유튜버의 식별자와 현재 채널명만 표시하고 프로필 이미지·구독자 수를 추가하지 않는다.

## 5. 구현 검수 기준

- [ ] 현재 단계 범위 밖인 테마 메뉴와 미지원 기능의 준비 중 링크가 공개 화면에 없다.
- [ ] 검색과 필터 조건이 URL에 반영되고 새로고침 후 유지된다.
- [ ] 맛집 카드와 상세에 평점·리뷰·대표 이미지 등 미지원 데이터가 없다.
- [ ] 영상 썸네일은 관련 영상 데이터가 있을 때만 표시된다.
- [ ] 관리자 화면이 공개 화면과 동일한 기본 디자인 언어를 사용한다.
- [ ] `/login`에 역할 선택이 없고, 비로그인·`MEMBER`·`ADMIN`의 `/admin/**` 접근이 각각 안전한 로그인 복귀·403·허용으로 구분된다.
- [ ] `ADMIN`에게만 정확한 `/admin` 링크가 보이며 열린 redirect·접두사 역할 판정·오래된 역할 표시가 없다.
- [ ] 360px, 390px, 768px, 1280px와 1440px 폭에서 핵심 흐름을 사용할 수 있다.
