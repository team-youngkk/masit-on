---
related_documents:
  - ../AGENTS.md
  - ../docs/00-overview/scope.md
  - ../docs/04-product/wireframes/README.md
version: alpha
name: 맛잇온 — 동네 취재 노트
description: 유튜버가 다녀간 맛집을 지역과 음식 정보로 읽어 내려가는 서비스이며, 동네 기록장처럼 따뜻하고 믿을 수 있게 느껴져야 한다.
colors:
  ground: "#f4efe5"
  ink: "#2e2a25"
  muted: "#645c52"
  signal: "#2f6b4f"
  darkGround: "#28241f"
  darkInk: "#f4eee4"
typography:
  display:
    fontFamily: "Pretendard, Noto Sans KR, Apple SD Gothic Neo, Malgun Gothic, sans-serif"
    fontSize: "clamp(2rem, 5vw, 3.25rem)"
    fontWeight: 700
    letterSpacing: "-0.02em"
  body:
    fontFamily: "Pretendard, Noto Sans KR, Apple SD Gothic Neo, Malgun Gothic, sans-serif"
    fontSize: "1rem"
    lineHeight: 1.6
rounded:
  sm: "6px"
  md: "10px"
  lg: "16px"
spacing:
  sm: "0.5rem"
  md: "1rem"
  lg: "1.5rem"
components:
  button-primary:
    backgroundColor: "{colors.signal}"
    textColor: "#fffaf0"
    rounded: "{rounded.md}"
    padding: "0 1rem"
  control:
    minHeight: "44px"
---

## Overview

맛잇온은 유튜버가 방문한 맛집을 지역·음식 종류·유튜버별로 탐색하는 MVP 서비스다. 공개 화면과 회원·관리자 화면은 같은 취재 노트의 시각 언어를 공유하되, 음식 사진과 방문 근거가 장식보다 먼저 읽혀야 한다.

방향의 재료는 동네 취재 노트다. 따뜻한 종이색 표면, 잉크로 적은 듯한 본문, 절제된 경계선으로 지역 정보를 담고, 녹색은 실제로 누를 수 있는 행동·선택·검증을 표시할 때만 쓴다. 화면을 어둡게 볼 때도 검은 터미널이나 산성 녹색의 조합이 아니라 따뜻한 숯색 종이와 낮은 채도의 숲색을 사용한다.

## Colors

- **Ground (`--color-background`, `#f4efe5`):** 전체 페이지의 종이 바닥. 콘텐츠가 놓이는 기준 표면이다.
- **Surface (`--color-surface`, `#fffaf0`):** 카드·입력·헤더처럼 내용을 직접 담는 밝은 종이 표면이다.
- **Muted surface (`--color-surface-muted`, `#ebe3d6`):** 보조 정보와 상태 영역을 구분한다. 경계선보다 면 차이를 먼저 사용한다.
- **Ink (`--color-text`, `#2e2a25`):** 본문과 제목. 밝은 바닥에서 읽는 기본 잉크색이다.
- **Muted ink (`--color-text-muted`, `#645c52`):** 보조 설명과 메타 정보. 본문과 같은 역할로 사용하지 않는다.
- **Action / selection / verification (`--color-primary-*`):** 녹색의 단일 역할이다. 주요 버튼·링크·선택됨·검증 완료처럼 사용자의 행동 또는 신뢰 신호를 나타낼 때만 쓴다.
- **Status (`--color-danger`, `--color-warning`, `--color-info`):** 실패·주의·정보를 녹색과 분리해 전달한다. 상태 색상만으로 의미를 전달하지 않고 문구·아이콘·ARIA를 함께 둔다.
- **Dark theme:** `--color-background: #28241f`, `--color-surface: #342f28`, `--color-text: #f4eee4`를 기준으로 한다. 순수 검정 바닥과 형광 녹색 대비를 사용하지 않는다.

모든 색상은 역할 토큰으로만 참조한다. 화면별 raw hex를 추가하지 않으며, 사진 위 텍스트는 `--color-text-on-image`와 충분한 오버레이를 함께 사용한다.

## Typography

한국어 본문은 기존 시스템 산세리프 스택(`Pretendard`, `Noto Sans KR`, 플랫폼 한글 산세리프)을 유지한다. 별도 웹 폰트나 의존성을 추가하지 않고, 제목은 같은 글꼴의 크기·굵기·행간 차이로 위계를 만든다. 장식용 올캡스 레이블, 모노스페이스 메타, 제목 일부만 다른 색으로 칠하는 처리는 사용하지 않는다.

- 본문은 `--font-size-base`와 `--line-height-base`를 기본으로 한다.
- 보조 문장과 메타 정보는 `--font-size-sm`을 쓰되 `--color-text-muted`의 대비를 유지한다.
- 제목은 `--font-size-lg`부터 `--font-size-3xl`까지 내용의 중요도에 따라 단계적으로 사용한다.
- 제목은 `--line-height-tight`, 본문은 `--line-height-base`, 긴 설명은 `--line-height-relaxed`를 사용한다.
- 굵기는 일반 400, UI 레이블 600, 제목·강조 700을 기준으로 하며 굵기만으로 상태를 표현하지 않는다.

## Layout

공개 프레임은 헤더–본문–푸터의 세로 흐름을 유지하고, 본문은 `--layout-max-width: 1200px` 안에서 가운데 정렬한다. 관리자 프레임은 기존처럼 전체 폭 작업 영역을 사용하며, 공개 내비게이션과 섞지 않는다. `.shell`, `.publicFrame`, `.main`, `.adminMain` 클래스와 `AppFrame`의 public/admin 분기는 기존 API로 고정한다.

반응형 기준은 360px, 390px, 768px, 1280px, 1440px다.

- 360·390px: 한 열, `--layout-gutter: 1rem`, 가로 스크롤 없음. 버튼·필터·메타는 필요한 경우 줄바꿈한다.
- 768px 이상: `--layout-gutter: 2rem`, 목록과 상세의 열을 콘텐츠에 맞게 확장한다.
- 1280·1440px: 본문 폭을 1200px로 제한해 긴 행을 막고, 여백을 콘텐츠의 호흡으로 사용한다.
- 관리자 화면은 자체 작업 레이아웃의 폭을 존중하되, 이 공통 토큰과 44px 조작 영역을 사용한다.

새 Route, 메뉴, 확장 기능을 이 계약에서 추가하지 않는다. 지도·찜·테마 큐레이션·일반 사용자 로그인·평점·리뷰 등 MVP 범위 밖 기능은 화면 골격에도 노출하지 않는다.

## Elevation & Depth

기본 화면은 평면 종이 표면으로 두고, 카드·드롭다운처럼 실제로 떠 있는 요소에만 `--shadow-card`를 사용한다. 모달이나 중요한 부유 표면에는 `--shadow-raised`를 제한적으로 쓴다. 모든 박스를 같은 그림자로 장식하거나 카드 안에 카드를 중첩해 계층을 만들지 않는다.

## Shapes

입력·버튼·작은 컨트롤은 `--radius-md`, 콘텐츠 카드와 상태 패널은 `--radius-lg`, 작은 배지와 진행 표시는 `--radius-full`을 사용한다. 모서리는 의미를 구분하는 보조 수단이며, 모양만으로 상태나 클릭 가능 여부를 암시하지 않는다.

## Surfaces & States

표면은 `background → surface → surface-muted` 순으로 역할을 나누고, `border`와 `border-strong`은 영역 경계를 얇게 표시한다. 상태 패널과 입력 오류의 `role`, `aria-invalid`, `aria-describedby`, `aria-live`, `aria-busy` 같은 의미 계약은 시각 개편으로 바꾸지 않는다.

- **Default:** 종이색 바닥과 잉크색 본문.
- **Action:** 주요 버튼과 링크는 `--color-primary-deep` 또는 `--color-primary-strong`을 사용한다.
- **Selected / verified:** `--color-primary-soft`·`--color-primary-faint`와 문구·아이콘을 함께 사용한다.
- **Hover / active:** 배경·경계·텍스트의 상태를 짧게 바꾸되 레이아웃을 이동시키지 않는다.
- **Focus:** 모든 키보드 조작 대상에 `:focus-visible` 링을 제공하고, 최소 3px 외곽선과 충분한 대비를 유지한다.
- **Disabled:** 비활성화는 색상·커서·상호작용을 함께 낮추며, 텍스트를 읽을 수 없게 만들지 않는다.
- **Danger / warning / info:** 각각 빨강·황색·청색 계열을 사용하며 녹색 성공 상태와 혼동하지 않는다.
- **Loading / empty / error:** 기존 공통 `StatePanel`의 props와 역할을 유지한다. 일시적인 로딩과 실제 빈 결과·오류를 같은 문구로 합치지 않는다.

## Components

`SiteHeader`, `SiteFooter`, `AppFrame`의 Route·세션·테마 토글·알림·모바일 메뉴 동작을 유지한다. `Button`, `Field`, `Card`, `StatePanel`, `StatusBadge`의 props, HTML role, 오류 연결, 상태 의미를 변경하지 않고 이 토큰을 시각 스타일의 단일 출처로 사용한다.

Root layout의 `MemberSessionProvider`, `GoogleAnalytics`, `themeInitScript`, `metadata`, `viewport`도 보존한다. 다크 테마는 `html[data-theme='dark']`가 공개·관리자 프레임의 토큰을 덮어쓰는 현재 계약을 유지한다.

## Accessibility & Motion

모든 버튼·링크·입력·선택 컨트롤의 조작 영역은 최소 44px을 유지한다. 색상만으로 행동이나 상태를 구분하지 않고 텍스트·구조·ARIA를 함께 사용한다. 본문·보조 문구는 WCAG AA 대비를 목표로 하며, `focus-visible`은 마우스 클릭 테두리와 분리해 키보드 위치를 분명히 한다. 360px에서도 확대 없이 가로 스크롤이나 잘린 조작 영역이 없어야 한다.

상호작용 전환은 짧고 절제된 값만 사용한다. `prefers-reduced-motion: reduce`에서는 전역 애니메이션·전환을 사실상 제거하고 스크롤을 즉시 처리한다. 기능 의미를 전달하는 상태 변경은 모션 없이도 이해되어야 하며, 자동 재생·반복 애니메이션을 새로 추가하지 않는다.

## 지켜야 할 것과 금지할 것

- 공개·회원·관리자 화면 모두에 따뜻한 종이색 표면, 잉크색 본문, 절제된 경계선을 재사용한다.
- 녹색은 행동·선택·검증과 그 포커스 표현에만 사용한다.
- 새 화면이 공유 값을 필요로 하기 전에 이 문서와 `globals.css`에 토큰을 먼저 추가한다.
- 기존 Route, API 요청, URL 쿼리 상태, 권한, Provider, 오류 의미를 유지한다.
- 다크 테마에 near-black와 산성 녹색을 함께 사용하지 않는다.
- 보라색 그라디언트, 상투적인 크림·테라코타 조합, 올캡스 크롬, 장식용 `01/02/03`, 제목 일부만 강조색으로 칠하는 패턴을 사용하지 않는다.
- 새 의존성·Route·메뉴·MVP 범위 밖 기능을 추가하지 않는다.
- 공통 계약 작업에서 `frontend/components/layout/**` 또는 `frontend/components/ui/**`의 API를 바꾸지 않는다.
