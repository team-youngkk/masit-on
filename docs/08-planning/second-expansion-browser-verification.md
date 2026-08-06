---
status: In Progress
verification_date: 2026-08-06
owners:
  - 양성훈
related_documents:
  - second-expansion-test-matrix.md
  - mvp-local-verification.md
  - expansion-2-task-breakdown.md
  - ../00-overview/scope.md
  - ../01-requirements/non-functional-requirements.md
  - ../07-adr/platform/web-004-supported-browser-matrix.md
  - ../07-adr/platform/web-001-frontend-platform.md
  - ../07-adr/platform/deploy-003-validation-cookie-session.md
  - ../07-adr/quality/test-001-automation-strategy.md
---

# 2차 확장 브라우저 검증 기록

## 1. 문서 목적과 판정 기준

`TST-E2-E2E-001`의 지원 브라우저·화면 폭·접근성 확인 증거를 남긴다. 담당자는 양성훈이고 후속 추적은 [#149](https://github.com/team-youngkk/masit-on/issues/149)에서 한다.

판정 대상 매트릭스는 [ADR-WEB-004](../07-adr/platform/web-004-supported-browser-matrix.md) 6.1절의 PC Chrome·Edge, Android Chrome과 대표 화면 폭 360px, 390px, 768px, 1280px, 1440px다. iPhone Safari는 같은 ADR에 따라 판정 대상이 아니며 이 문서에서도 **미검증**으로 남긴다.

**이 문서는 부분 검증 상태다.** 확인한 항목과 확인하지 못한 항목을 5절에서 구분한다. 확인하지 못한 항목을 통과로 적지 않는다. 5절이 비워질 때 상태를 `Verified`로 바꾼다.

## 2. 확인 환경

| # | 브라우저 | 버전 | 단말·OS | 대상 환경 | 빌드 | 화면 폭 |
|---|---|---|---|---|---|---|
| A | Android Chrome | 150.0.7871.186 | Galaxy S25 Ultra | `https://masiton.click` (제한 공개) | 배포본 = **1차 확장까지** | 단말 기본 폭 1종 |
| B | 삼성 인터넷 | 30.0.0.67 | Galaxy S25 Ultra | `https://masiton.click` (제한 공개) | 배포본 = 1차 확장까지 | 단말 기본 폭 1종 |
| C | PC Chrome | 151.0.7922.72 | Windows 11 | `https://masiton.click` (제한 공개) | 배포본 = 1차 확장까지 | 데스크톱 폭 1종. **대표 폭 5종 조합은 확인하지 않았다** |
| D | PC Edge | 151.0.4129.59 | Windows 11 | `https://masiton.click` (제한 공개) | 배포본 = 1차 확장까지 | 데스크톱 폭 1종. **대표 폭 5종 조합은 확인하지 않았다** |
| E | Chromium(Claude Code 내장 브라우저) | Chrome 148.0.7778.280 / Electron 42.7.0 | Windows 11 Home 26200 | `http://localhost:3000` + `localhost:8080` | `develop` `cdcce68`, Flyway V1~V3 적용 | 360·390·768·1280·1440px |

환경 E의 Node.js는 24.18.0이고 백엔드는 `docker compose`의 `local` 프로파일로 실행했다. 화면 폭은 브라우저 viewport를 각 폭으로 지정해 확인했다.

## 3. 배포 환경에는 2차 확장 화면이 아직 없다

환경 A~D에서 확인한 `masiton.click`은 **1차 확장까지의 배포본**이다.

- 헤더 진입점이 `맛집 탐색`, `지도`, 로그인/`내 메뉴`뿐이고 `인기`·`큐레이션`이 없다.
- `/popular`와 `/curations`가 모두 `404`다.

따라서 실브라우저에서 확인한 것은 MVP·1차 확장 화면이고 **2차 확장 화면의 실브라우저 확인은 아직 없다.** 2026-08-06 팀은 2차 확장 화면 검증을 **운영 배포 이후에 수행**하기로 정했다. 배포 뒤 환경 A·C·D에서 2차 확장 여정을 확인해야 `TST-E2-E2E-001`의 실브라우저 조건이 채워진다.

환경 B(삼성 인터넷)는 Blink 계열 참고 증거다. 매트릭스의 `Android Chrome` 행을 대체하지 않는다.

### 3.1 배포본(1차 확장) 확인 결과

환경 A·C·D에서 다음 화면이 레이아웃 깨짐 없이 표시되고 여정이 이어졌다. 각 브라우저의 단일 폭 확인이며 대표 폭 5종 조합 확인이 아니다.

| 화면 | Android Chrome (A) | PC Chrome (C) | PC Edge (D) |
|---|---|---|---|
| 맛집 탐색 `/restaurants` (검색·필터·페이지) | 확인 | 확인 | 확인 |
| 맛집 상세 `/restaurants/{id}` (방문 유튜버·영상·카카오 링크) | 미확인 | 확인 | 확인 |
| 지도 탐색 `/map` | 확인 | 확인 | 확인 |
| 지도 유튜버 필터 적용 후 선택 요약·`상세 보기` | 확인 | 확인 | 확인 |
| 로그인 `/login`, 회원가입 `/signup`, 이메일 인증 `/verify-email` | 확인 | 확인 | 확인 |
| 내 계정 `/me` | 확인 | 확인 | 확인 |
| 찜한 맛집 `/me/favorites` (빈 상태) | 확인 | 확인 | 확인 |
| 최근 본 맛집 `/me/recent-restaurants` | 확인 | 확인 | 확인 |

이 표는 `TST-E2-E2E-001`(2차 확장)의 증거가 아니다. 2차 확장 화면이 배포되기 전 지원 브라우저에서 기존 화면이 회귀 없이 동작함을 남긴 기록이다.

## 4. 2차 확장 화면 확인 결과 (환경 E)

### 4.1 화면 폭 5종

각 폭에서 문서 가로 스크롤 발생 여부, viewport를 넘는 요소, 24px 미만 조작 대상, 접근 이름 없는 링크·버튼, 제목 계층, `alt` 없는 이미지, label 없는 폼 컨트롤을 측정했다.

| 화면 | 360px | 390px | 768px | 1280px | 1440px |
|---|---|---|---|---|---|
| 인기 맛집 `/popular` | 통과 | 통과 | 통과 | 통과 | 통과 |
| 큐레이션 목록 `/curations` | 통과 | 통과 | 통과 | 통과 | 통과 |
| 큐레이션 상세 `/curations/{id}` | 통과 | 통과 | 통과 | 통과 | 통과 |
| 맛집 탐색 `/restaurants` (진입점) | 통과 | 통과 | 통과 | 통과 | 통과 |

전 조합에서 가로 스크롤 0건, viewport 초과 요소 0건, 24px 미만 조작 대상 0건, 접근 이름 없는 링크·버튼 0건, `alt` 속성 없는 이미지 0건이었다. 제목 계층은 각 화면이 `h1` 하나로 시작하고 단계를 건너뛰지 않는다. 360px에서 헤더 진입점이 두 줄로 줄바꿈되지만 잘리거나 겹치지 않는다. 이름이 긴 맛집(`아주 긴 이름을 가진 마라탕 전문점 성수 본점`)도 360px에서 카드 안에서 줄바꿈된다.

`/restaurants`의 `size` 컨트롤이 label 없이 검출됐으나 `type="hidden"` 입력이라 접근성 문제가 아니다.

### 4.2 공개 큐레이션 계약

- 게시(`PUBLISHED`) 큐레이션 2건만 공개 목록에 나오고 `DRAFT` 1건은 목록에 없다.
- `DRAFT` 큐레이션 상세는 `404`다. 존재를 드러내지 않는다.
- 큐레이션 상세의 구성 맛집이 지정한 표시 순서대로 나온다.

### 4.3 인기 맛집 정렬

찜 3·2·1·0건으로 만든 데이터에서 `rank` 1·2·3이 찜 수 내림차순으로 나오고 찜 0건 맛집은 목록에 없다.

## 5. 확인하지 못한 항목

| 항목 | 상태 | 이유 | 다음 단계 |
|---|---|---|---|
| PC Chrome·Edge의 대표 폭 5종 조합 | 미검증 | 환경 C·D는 데스크톱 폭 1종에서만 확인했다. 환경 E는 5종을 확인했으나 Chromium 기반 내장 브라우저이며 Chrome·Edge 실빌드가 아니다 | 담당자가 각 브라우저 실빌드에서 360·390·768·1280·1440px을 확인한다 |
| 실브라우저에서의 2차 확장 화면 | 미검증 | 배포본에 2차 확장 화면이 없다(3절). 2026-08-06 팀이 배포 이후 수행으로 정했다 | 2차 확장 운영 배포 후 환경 A·C·D에서 확인한다 |
| 회원 2차 확장 화면(컬렉션, 알림, 제보·신고 내역) | 미검증 | 회원 로그인 세션이 필요하다 | 담당자 세션으로 4.1절과 같은 항목을 확인한다 |
| 관리자 2차 확장 화면(큐레이션 관리, 제보·신고 검토) | 미검증 | 관리자 로그인 세션이 필요하다 | 담당자 세션으로 확인한다 |
| iPhone Safari | 미검증(판정 대상 아님) | 실단말이 없다. [ADR-WEB-004](../07-adr/platform/web-004-supported-browser-matrix.md) | 해제 조건 충족 시 매트릭스로 되돌린다 |
| 색 대비, 키보드 초점 표시, 보조기기 낭독 | 미검증 | 이번 확인은 DOM 측정 기반이며 대비 계산과 실제 보조기기 확인을 포함하지 않는다 | 담당자가 수동 확인 범위를 정한다 |

## 6. 관찰 사항

로그인하지 않은 방문에서 공개 화면을 열면 `POST /api/auth/tokens/refresh`가 `401`로 두 번 호출되고 브라우저 콘솔에 오류로 남는다. 세션 복구 시도이므로 계약상 정상 응답이지만, 익명 방문에서 같은 요청이 두 번 나가는 점은 담당자가 판단할 사항이다. 결함으로 판정하면 별도 이슈로 분리한다.

## 7. 재현 절차 (환경 E)

1. `docker compose up -d postgres redis wiremock`
2. `.env`에 `MEMBER_ACTION_MAIL_ACTIVE_KEY_ID`, `MEMBER_ACTION_MAIL_ACTIVE_KEY`, `MEMBER_RATE_LIMIT_SECRET`이 있어야 한다. `scripts/Initialize-LocalJwt.ps1`은 `.env`에 **이미 있는 줄만** 채우므로 1차 확장 이전에 만든 `.env`에는 이 세 줄이 없고 애플리케이션이 기동에 실패한다. 없으면 `.env.example`에서 해당 줄을 복사한 뒤 스크립트를 실행한다.
3. `docker compose up -d --build app` 후 `/internal/health/ready`가 `200`인지 확인한다.
4. 화면 확인용 공개 데이터를 넣는다. 맛집 4건(자치구·대표 음식 혼합, 이름이 긴 것 1건), 유튜버 1명, 영상 1건, 방문 4건, 찜 3·2·1·0건, 게시 큐레이션 2건과 `DRAFT` 1건이면 4절 항목을 모두 볼 수 있다. 관리자·회원 화면을 함께 볼 때는 로그인 가능한 계정이 따로 필요하다.
5. `npm --prefix frontend run dev` 후 각 폭에서 4.1절 화면을 확인한다.
