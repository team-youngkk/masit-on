---
related_documents:
  - ../05-specs/api/common/region-contract.md
  - ../05-specs/api/discovery/restaurant-discovery-api.md
  - ../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../04-product/prd/discovery/restaurant-discovery.md
  - ../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../07-adr/platform/web-004-supported-browser-matrix.md
  - ../troubleshooting/pr-179-browser-capture-evidence-review.md
---

# 전국 지역 계층 브라우저 인수 검증 기록

이 문서는 [이슈 #401](https://github.com/team-youngkk/masit-on/issues/401)의 전국 지역 계층 인수 검증 증적이다. API·목록·자연어·코스의 주요 지역 시나리오는 확인했지만, 관리자 등록과 지원 브라우저 매트릭스 전체는 아직 수행하지 않았다. 따라서 아래의 `확인`과 `미확인`을 구분하며, 이 기록만으로 이슈 완료를 선언하지 않는다.

## 1. 실행 환경

| 항목 | 값 |
|---|---|
| 검증 기준 | `origin/develop` `5e073bd9` (`#403` 반영 기준) |
| OS | Windows, Codex 데스크톱 실행 환경 |
| 브라우저 | Google Chrome Codex Browser Use 세션, URL `http://192.168.0.7:3000` |
| 브라우저 버전 | 자동화 세션에서 노출되지 않아 기록 불가 |
| viewport | 자동화 세션에서 고정값 확인 불가; 지원 매트릭스의 360/390/768/1280/1440 검증으로 산정하지 않음 |
| 백엔드 | Spring Boot 4.1.0, Java 21.0.11 |
| 프론트엔드 | Next.js 16.3.8, Node.js 24.14.0 (계약 버전 24.18.0보다 낮음) |
| 의존 서비스 | PostgreSQL 17.10, Redis 8.8, WireMock 로컬 컨테이너 |
| 실행 주소 | 백엔드 `http://localhost:8080`, 프론트 `http://192.168.0.7:3000` |

`/internal/health/ready`는 `200`으로 응답했다. 기존 사용자 변경 파일은 수정하지 않았다.

사설 IP 개발 서버에서 클라이언트 hydration이 동작하도록 검증용 임시 실행 설정에 `allowedDevOrigins=192.168.0.7`을 적용했다. 저장소의 `next.config.ts`는 변경하지 않았으며, production build 또는 동일 origin 배포 환경에서 재확인이 필요하다.

## 2. 확인한 API·화면 증적

| 시나리오 | 요청 | 응답 | 화면 결과 |
|---|---|---|---|
| 지역 트리 | `GET /api/regions` | `200`, 상위 16개, 전체 245개 기준 데이터 | 서버 렌더링 목록 필터에 시·도 값이 반영됨 |
| 서울 상위 지역 | `GET /api/restaurants?regionCode=1100000000&page=1&size=21` | `200`, 1건, `page=1/1` | `서울특별시`, 결과 1곳, 지역 필터 적용 안내 표시 |
| 서울 하위 지역 | `GET /api/restaurants?regionCode=1144000000&page=1&size=21` | `200`, 1건, `page=1/1` | `서울특별시 > 마포구`, 결과 1곳 |
| 세종 | `GET /api/restaurants?regionCode=3611000000&page=1&size=21` | `200`, 빈 `items`, `totalPages=0` | `하위 지역 없음` 비활성 표시와 빈 결과 안내 |
| 제주 상위 | `GET /api/restaurants?regionCode=5000000000&page=1&size=21` | `200`, 빈 `items`, `totalPages=0` | `제주특별자치도`와 빈 결과 안내 |
| 제주 하위 | `GET /api/restaurants?regionCode=5011000000&page=1&size=21` | `200`, 빈 `items`, `totalPages=0` | `제주특별자치도 > 제주시`와 빈 결과 안내 |
| 필터 초기화 | 서울 결과 화면의 `필터 초기화` | `/restaurants?page=1&size=21` 링크 확인 | 지역 조건과 페이지가 초기화되는 링크 확인 |
| 지도 차단 | `GET /map?regionCode=1100000000` | `200` | `선택한 지역은 현재 지도에서 지원하지 않습니다`와 지역 해제 링크 표시; 목록 화면의 지도 메뉴도 비활성 안내 표시 |
| 클릭 기반 지역 선택 | `/restaurants`에서 서울 → 마포구 → 검색, 서울 → 세종 → 검색, 제주 → 제주시 → 검색 | 각각 `regionCode=1144000000`, `3611000000`, `5011000000`으로 URL 생성 | 하위 목록 갱신, 세종 `하위 지역 없음`, 제주 2개 하위 지역 및 선택 결과 확인 |
| 코스 지역 선택 | `GET /course` 및 코스 화면의 지역 선택기 | 화면 `200`, 지역 마스터 로딩 완료 | 제주 → 제주시 선택지와 빈 후보 결과 확인 |

모든 목록 요청은 `size=21`을 사용했다. 제주 하위 지역은 지역 트리 응답에서 `5011000000 제주시`, `5013000000 서귀포시` 두 개를 확인했다. 세종은 하위 지역이 없고 자기 코드로 직접 조회된다.

## 3. 오류·충돌 증적

| 시나리오 | 요청 | 상태·응답 | traceId |
|---|---|---|---|
| 형식 오류 | `regionCode=bad-code` | `400 INVALID_FIELD_VALUE` (`10자리 행정구역 코드를 지정해야 합니다`) | `400be14667f641378c047f8c5eff828a` |
| 미등록 또는 비활성 | `regionCode=9999900000` | `400 INVALID_FIELD_VALUE` (`존재하지 않거나 비활성인 지역입니다`) | `df7bdceca36c4e2589cd40bdce854071` |
| 복수 값 | `regionCode=1100000000,2600000000` | `400 INVALID_FIELD_VALUE` (`값은 한 번만 지정할 수 있습니다`) | `586d578b3dcc46f3922e0577f98d2b3f` |
| `district` 충돌 | `regionCode=1100000000&district=마포구` | `400 INVALID_FIELD_VALUE` (`district와 regionCode는 함께 지정할 수 없습니다`) | `41d3a4c922684fc08f33d5eeabf4de1e` |

초기 기준 데이터는 모두 활성 상태라 별도 비활성 행의 화면 선택 시나리오는 만들지 않았다. 미등록 코드가 계약상 `존재하지 않거나 비활성` 오류로 닫히는 것은 확인했다.

## 4. 자연어 지역 해석 증적

직접 `POST /api/restaurants/natural-language-search` 요청으로 다음을 확인했다.

| 문장·직접 조건 | 상태 | 확인 내용 |
|---|---|---|
| `서울 맛집`, 직접 지역 없음 | `200 APPLIED` | `appliedConditions.regionCode=1100000000` |
| `서울 맛집`, 직접 `regionCode=2600000000` | `200 PARTIAL` | `conflicts=[{field: regionCode, resolution: DIRECT_FILTER_WON}]` |
| `달빛 아래 별빛 맛집` | `200 FAILED` | 미지원 조건을 `ignoredConditions`로 남기고 빈 결과 반환 |

API 응답의 지역 코드 적용·직접 필터 우선·미해결 구분을 확인했고, 브라우저에서도 `서울 맛집`은 `조건 적용 완료`와 `지역: 서울특별시`로 표시됐다. 직접 `부산광역시` 필터와 충돌할 때는 `일부 조건 적용`·`직접 필터 우선: 지역`, 미해결 문장은 `적용하지 않은 조건: 지원되지 않는 조건`으로 표시됐다.

## 5. 확인하지 못한 항목과 재현 조건

초기 사설 IP 개발 서버에서는 `/_next/hmr` cross-origin 경고로 hydration이 완료되지 않았지만, 검증용 임시 `allowedDevOrigins` 설정을 적용한 뒤 드롭다운·자연어·코스 상호작용이 정상 동작했다. 이 설정은 저장소에 반영하지 않았다.

다음은 미확인으로 남겼다.

- 관리자 로그인 후 맛집 등록 화면의 전국 지역 선택·주소 검색·등록 확정
- 관리자 등록에서 Kakao 주소 후보의 지역 자동 지정과 중복 결과 화면
- 지원 브라우저 매트릭스(Chrome/Edge PC, Android Chrome)의 360/390/768/1280/1440 viewport별 수동 캡처
- 실제 production build·배포 origin에서의 동일 시나리오

관리자 자원은 인증정보를 임의로 만들거나 입력하지 않고 `/admin/restaurants/new` 접근에서 인증 상태 확인 오류 화면까지만 확인했다. 관리자 계정과 별도 인증 세션이 준비된 환경에서 등록 시나리오를 이어서 수행해야 한다.

## 6. 자동화 회귀 검증

브라우저 러너를 새 의존성으로 추가하지 않고 기존 순수 상태·API 계약 테스트를 기준으로 회귀를 확인했다. 프론트는 전체 build 스크립트로 자연어 테스트, 전체 프론트 테스트, 타입 검사, production build를 한 번에 실행했고, 백엔드는 clean build로 단위·통합·아키텍처 검사를 포함해 실행했다.

| 명령 | 결과 |
|---|---|
| `npm.cmd run build` (`frontend`) | 자연어 18개 + 전체 360개 테스트 통과, TypeScript 통과, Next production build 성공 |
| `./gradlew.bat clean build --no-daemon --console=plain` | `BUILD SUCCESSFUL` (8분 31초) |

첫 프론트 병렬 실행에서는 Windows `.next` 산출물 디렉터리 생성 단계가 `EPERM`으로 중단됐으나, 해당 생성 산출물을 정리한 뒤 단독 재실행해 성공했다. 저장소 소스 변경이나 테스트 실패는 없었다.

기존 테스트에는 전국 지역 코드·세종·제주·동명 지역·필터 초기화·지도 `regionCode` 차단·자연어 직접 지역 우선 및 코스 지역 마스터 계약이 포함돼 있다. 현재 저장소에는 Playwright/Cypress 실행 기반이 없고, 이번 이슈는 브라우저 매트릭스 수동 증적이 주 목적이므로 새 브라우저 의존성은 추가하지 않았다.

## 7. 이슈 완료 판단

- API 계약 및 서버 렌더링 기반 시나리오: 확인
- 오류 응답 traceId 기록: 확인
- 기존 자동화 회귀: 확인
- 지역 목록·자연어·코스의 클릭 기반 인수: 확인
- 관리자 등록·지원 브라우저 전체 매트릭스 인수: 미완료

따라서 현재 상태는 **부분 검증·브라우저 재검증 필요**다. 인증 세션과 지원 브라우저 매트릭스가 준비된 환경에서 미확인 항목을 수행한 뒤, 각 항목에 요청 URL·상태 코드·화면 결과·실패 시 traceId를 추가해야 이슈를 닫을 수 있다.
