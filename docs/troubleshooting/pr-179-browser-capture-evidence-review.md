---
related_documents:
  - ../08-planning/third-expansion-browser-verification.md
  - ../08-planning/third-expansion-test-matrix.md
  - ../07-adr/platform/web-004-supported-browser-matrix.md
---

# PR #179 리뷰 트러블슈팅: 브라우저 인수 캡처 증거 보존

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#179](https://github.com/team-youngkk/masit-on/pull/179) |
| 작성자 | 양성훈 |
| 처리 일자 | 2026-08-12 |
| 범위 | `E3-T12` 브라우저 검증 기록의 `TST-E3-E2E-001` 캡처 증거 누락 |
| 주 문제 유형 | 기타(검증 증거 보존). 조사 과정에서 인프라(브라우저 실행 환경) 제약이 원인으로 확인됨 |
| 기존 기록 | [PR #122 테스트 결과 최신화 누락](pr-122-map-viewport-independent-query-review.md) 확인. 실행 결과를 문서에 반영하라는 규칙은 같지만 캡처 아티팩트 보존을 다룬 기록은 없어 새로 작성 |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [r3764947091](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3764947091) | 대표 화면 폭과 정상·빈·오류·복구 흐름의 캡처 또는 보존 가능한 아티팩트 링크 추가 | 기타 | 수정 필요 | 공개 화면 초기 상태 캡처 12장을 저장소에 보존하고 3.1절에 연결. 여정·관리자 화면 캡처는 6절 미검증으로 남김 | 캡처 파일과 문서 링크, 3.1절 재현 절차 |
| [r3765086407](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3765086407) | 같은 요청(캡처 또는 CI·외부 아티팩트 링크) | 기타 | 수정 필요 | 위와 같음 | 위와 같음 |
| [r3765411828](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3765411828) | 초기 상태 외에 4절 여정과 관리자 화면의 대표 상태별 캡처도 추가 | 기타 | 수정 필요 | 여정·관리자 상태 캡처 23장을 추가하고 3.2절에 연결 | 캡처 파일과 문서 링크, 8절 재현 절차 |

앞의 두 스레드는 같은 원인이라 하나의 변경으로 처리했다. 세 번째 스레드는 첫 대응이 초기 상태 캡처에 그친 것을 지적한 후속 요청이며 9절에 처리 과정을 남긴다.

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음. 문서 완료 조건 미충족이다.
- 발생 환경: `feature/t-165-third-expansion-browser-verification`, 기준 커밋 `35d0f94`, Windows 11, Node.js 24.18.0, 검증 시점 Flyway `V1`~`V7`(현재 `#192` 통합 후 적용 결과 스키마 동일).
- 재현 조건: [3차 확장 테스트 추적표](../08-planning/third-expansion-test-matrix.md) 5절이 브라우저 인수 완료 조건을 "`TST-E3-E2E-001` 화면 캡처·환경·접근성 결과 기록"으로 정하고, 이슈 [#165](https://github.com/team-youngkk/masit-on/issues/165)도 "브라우저 캡처·환경·접근성 증거"를 요구한다.
- 실제 결과: 검증 기록에 DOM 측정값과 여정 서술만 있고 캡처나 보존 가능한 아티팩트가 없어 제3자가 재검증할 수 없었다.
- 기대 결과: 대표 화면 폭의 렌더링을 확인할 수 있는 캡처가 저장소에 남아 문서에서 연결된다.
- 영향 범위: `E3-T12` 완료 판정과 `E3-T13` 활성화 게이트. 코드·데이터 영향은 없다.

## 4. 근본 원인

검증을 Claude Code 내장 브라우저의 DOM 측정으로만 수행했고, 이 경로는 화면 이미지를 파일로 저장하지 못한다. 측정값은 남았지만 완료 조건이 요구한 이미지 아티팩트가 만들어지지 않았다.

캡처를 만들려다 확인한 2차 원인은 브라우저 실행 환경 제약이다. **Windows의 Chromium은 창 너비를 500px 미만으로 만들지 못한다.** `--headless=new --window-size=360,800`으로 캡처하면 문서는 500px 폭으로 배치되고 이미지만 360px로 잘려, 실제 360px 화면과 다른 잘린 화면이 저장된다. 확인 방법과 관측값은 5절에 있다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 완료 조건 원문 확인(테스트 추적표 5절, 이슈 #165) | 캡처가 명시된 완료 조건임 | 두 스레드를 `수정 필요`로 판단 |
| 저장소 이미지 커밋 관례 확인 | `docs/04-product/wireframes/*.png` 존재 | 캡처를 저장소에 두는 방식 채택 |
| 프런트엔드 의존성 확인 | Playwright·Puppeteer 없음 | 라이브러리 추가는 기술 정책상 합의 대상이라 배제 |
| PC Chrome·Edge 실빌드 존재 확인 | 두 실행 파일 모두 설치돼 있음 | 의존성 추가 없이 `--headless=new --screenshot` 사용 |
| `--window-size=360,800`으로 캡처 | 이미지 폭은 360px이지만 본문·헤더가 오른쪽에서 잘림 | 실제 렌더링과 다르므로 폐기 |
| `--headless`(구 모드), `--force-device-scale-factor=1` 재시도 | 동일하게 잘림 | headless 모드 차이가 아님 |
| `data:` 문서로 `innerWidth` 측정(`--window-size=360,200`) | `iw=500 cw=500` | Windows Chromium의 500px 최소 창 너비가 원인으로 확정 |
| 500px 창 안에 360·390px iframe으로 문서를 그린 뒤 해당 폭만큼 잘라 저장 | 줄바꿈·스택 레이아웃이 정상 렌더링되고 잘림 없음 | 360·390px 캡처 방식으로 채택 |
| 768·1280·1440px은 `--window-size` 직접 사용 | 최소 폭 제약에 걸리지 않아 정상 | 그대로 사용 |

## 6. 최종 해결

- 변경 내용: 공개 화면(`/restaurants`, `/course`) 초기 상태를 5개 폭에서 PC Chrome 실빌드로 캡처해 10장, 교차 브라우저 참고로 PC Edge 1280px 2장을 `docs/08-planning/assets/e3-t12/`에 보존했다. 검증 기록에 3.1절을 추가해 캡처를 연결하고, 캡처 방식과 500px 최소 창 너비 우회를 기록했다. 캡처가 없는 범위(여정 상태, 관리자 화면, 실빌드 여정)를 6절 미검증 항목으로 분리하고 테스트 추적표 5절 상태를 갱신했다.
- 선택 이유: 새 라이브러리 없이 지원 브라우저 실빌드로 캡처할 수 있고, 저장소에 남겨 문서 단독으로 재검증할 수 있다. 잘린 캡처를 그대로 남기면 실제 화면과 다른 증거가 되므로 배제했다.
- 변경 파일:
  - `docs/08-planning/third-expansion-browser-verification.md`
  - `docs/08-planning/third-expansion-test-matrix.md`
  - `docs/08-planning/assets/e3-t12/*.png` (12개)
- 고려한 대안: Playwright를 추가해 여정까지 자동 캡처하는 방안은 devDependency 추가가 [기술 정책](../06-architecture/technology-policy.md)상 버전 고정·합의 대상이라 이번 범위에서 제외했다. PR 코멘트 첨부는 문서 단독 재검증이 불가능해 제외했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| 캡처 12장의 PNG 헤더 크기 확인 | 통과 | 파일 폭이 360·390·768·1280·1440px과 일치 |
| 360·390px 캡처 육안 확인 | 통과 | 헤더 줄바꿈과 폼 스택 배치가 보이고 잘린 영역 없음 |
| 768·1280px 캡처 육안 확인 | 통과 | 2열 배치와 전체 레이아웃이 잘림 없이 보임 |
| 백엔드 `/internal/health/ready`, 프런트 `/course` 응답 | 통과 | 캡처 시점에 두 서버 모두 `200` |
| GitHub Actions `백엔드 빌드·테스트`, `프론트엔드 빌드·타입 검사` | 통과 | 문서·이미지만 변경해 코드 영향 없음 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 검증 기록 8절 재현 절차에 초기 화면 캡처 명령(360·390px의 500px 최소 창 너비 우회 포함)과 여정 캡처의 DevTools Protocol 절차를 모두 남겼다. 다음 검증 Task가 같은 제약에 다시 걸리지 않는다.
- 다음 확인: PC Edge 실빌드의 여정과 모바일 실단말 여정은 검증 기록 6절에 미검증으로 남아 있다. `E3-T13` 활성화 판정 전에 담당자가 채운다. 추적은 이슈 [#165](https://github.com/team-youngkk/masit-on/issues/165)에서 이어간다.
- 비교 지표: 해당 없음. 증거 보존 여부는 존재·부재로 판정하며 수치 비교 대상이 아니다.

## 9. 후속 요청: 여정과 관리자 화면 캡처

첫 대응은 URL 로드로 재현되는 초기 상태 캡처까지였고, 여정과 관리자 화면은 미검증으로 남겼다. 리뷰어가 [r3765411828](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3765411828)에서 그 범위가 완료 조건의 핵심이라고 다시 요청해 추가로 처리했다.

- 제약: 여정 상태는 로그인·입력·외부 응답이 있어야 하고, `--screenshot` 한 방으로는 만들 수 없다. Playwright를 넣으면 되지만 devDependency 추가는 기술 정책상 합의 대상이라 배제했다.
- 해결: 설치된 PC Chrome을 `--remote-debugging-port=9222`로 띄우고 Node 내장 `WebSocket`으로 DevTools Protocol을 직접 호출했다. `Emulation.setDeviceMetricsOverride`로 폭을 지정하고, React 제어 입력은 native setter + `input` 이벤트로 채우고, `Page.captureScreenshot`으로 저장했다. 새 의존성이 없다.
- 부수 효과: `setDeviceMetricsOverride`는 4절의 500px 최소 창 너비 제약을 받지 않는다. 검증 기록 3.1절의 iframe 우회로 만든 초기 화면 캡처는 그대로 두었고, 앞으로 좁은 폭 캡처는 이 경로가 더 간단하다.
- 범위: 여정 캡처 목록은 검증 기록 3.2절 표를 단일 출처로 삼는다. 후속 리뷰 반영분(코스 빈 결과·입력 부족, 관리자 검수 사유 누락 거부)을 포함해 `assets/e3-t12`에 여정 캡처 26장이 있다. 자연어 장애·복구는 백엔드를 내렸다 올려 만들었고, 코스 만료는 생성 5분 뒤에 찍었다.
- harness는 일회성 검증 도구라 저장소에 두지 않고 절차만 검증 기록 8절에 남겼다.

## 10. 후속 리뷰 반영

| 스레드 | 요청 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [r3772142965](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3772142965) | 코스 `빈 결과`·`입력 부족` 캡처 추가 또는 미검증 명시 | 기타 | 수정 필요 | 두 상태를 재현해 캡처 2장 추가 |
| [r3772155697](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3772155697) | `Set-EnvValue`가 대상 줄이 없을 때 실패하거나 줄을 추가하도록 | 애플리케이션 | 수정 필요 | 줄이 없으면 파일 끝에 추가하도록 변경 |
| [r3772155701](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3772155701) | PR 본문을 문서 5.4절 갱신에 맞춰 정정 | 기타 | 수정 필요 | 본문 접수 경로 절과 미검증 목록을 문서와 일치시킴 |
| [r3772155709](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3772155709) | 3.2절 캡처의 실행 기준을 2절·6절과 구분 | 기타 | 수정 필요 | 2절에 기준 커밋 두 행 분리, `verification_date` 갱신, 6절 문구 축소 |
| [r3772155713](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3772155713) | 8절 단계 번호 중복 정정 | 기타 | 수정 필요 | 마지막 단계를 `9.`로 수정 |

`Set-EnvValue`는 중단 대신 추가를 택했다. 중단은 실패를 드러내기만 하고 `.env`를 다시 복사해야 하지만, 추가는 `docker compose`가 읽는 파일을 실제로 채워 원인 자체를 없앤다. `JWT_*` 네 줄만 있는 `.env`로 실행해 네 항목이 추가되는 것과, 재실행 시 중복이 생기지 않는 것을 확인했다.

## 11. 3차 리뷰 반영

| 스레드 | 요청 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [r3772017364](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3772017364) | 4절 여정·관리자 화면 증거 추가 | 기타 | 이미 해결 | 9절 여정 캡처로 충족. 지적 시점이 캡처 커밋 이전이었다 |
| [r3772226586](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3772226586) | PR 본문 잔여 두 줄 정정 | 기타 | 수정 필요 | 실빌드 줄을 `Android Chrome 실단말`로 좁히고 접수 경로 공백 줄을 Webhook 항목으로 대체 |
| [r3772226584](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3772226584) | `모든 여정에 캡처가 하나씩 대응` 서술이 표와 불일치 | 기타 | 수정 필요 | 검수 사유 누락 거부 캡처를 추가하고, 재시도 2장·신규 영상 추가 화면 때문에 일대일이 아님을 명시 |
| [r3772226591](https://github.com/team-youngkk/masit-on/pull/179#discussion_r3772226591) | 트러블슈팅 9절 수치 갱신 | 기타 | 수정 필요 | 수치를 지우고 검증 기록 3.2절 표를 단일 출처로 지정 |

`모든 여정에 캡처가 하나씩 대응한다`는 문장은 코스 누락을 고치면서 넣었지만, 관리자 오류(검수 사유 누락) 여정이 빠져 있어 성립하지 않았다. 그 상태를 재현해 캡처를 추가하고 대응 관계 서술을 사실에 맞게 좁혔다. 6절 미검증 표의 `Android Chrome` 행이 중복이던 것도 함께 정리했다.
