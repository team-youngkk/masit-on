---
related_documents:
  - ../01-requirements/non-functional-requirements.md
  - ../04-product/prd/discovery/restaurant-course-recommendation.md
  - ../05-specs/api/discovery/restaurant-course-recommendation-api.md
  - ../07-adr/integration/route-001-kakao-mobility-course-routing.md
  - ../../docker-compose.yml
  - ../../deploy/scripts/app-run.sh
---

# PR #171 리뷰 트러블슈팅: 코스 경로 외부 연동·quota 경계

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#171 맛집 코스 추천 경로 조회](https://github.com/team-youngkk/masit-on/pull/171) |
| 작성자 | w00lam |
| 처리 일자 | 2026-08-11 |
| 범위 | Compose·운영 설정 전달, provider quota 오류 계약, 요청 입력 경계, Kakao 요청 payload, Redis 동시성·quota 관측성 리뷰 7건 |
| 주 문제 유형 | 애플리케이션 / 배포 / 인프라 |
| 기존 기록 | [PR #129 배포 전환·rate limit 리뷰](pr-129-deploy-cutover-and-rate-limit-review.md), [PR #123 검증 세션 리뷰](pr-123-verification-session-review.md)를 확인했으며, 이번 Mobility 경계에 맞춘 기록을 새로 남긴다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [3755133980](https://github.com/team-youngkk/masit-on/pull/171#discussion_r3755133980) | Compose app에 Mobility WireMock 주소 전달 | 배포 | 수정 필요 | `KAKAO_MOBILITY_BASE_URL` 추가 | Compose 설정 확인 및 WireMock 계약 테스트 |
| [3755133983](https://github.com/team-youngkk/masit-on/pull/171#discussion_r3755133983) | 운영 gate 값을 SSM에서 읽어 컨테이너에 전달 | 배포 | 수정 필요 | 두 gate 파라미터를 읽고 `docker run -e`로 전달 | `app-run.sh` 경로 정적 대조 |
| [3755133990](https://github.com/team-youngkk/masit-on/pull/171#discussion_r3755133990) | 월 quota·Redis 실패를 429가 아닌 provider unavailable로 분류 | 애플리케이션 | 수정 필요 | 월 quota 차단은 `PROVIDER_BLOCKED`, Redis 실패는 fail-closed 지표와 함께 외부 호출 0회 | Adapter/API 회귀 테스트 |
| [3755133994](https://github.com/team-youngkk/masit-on/pull/171#discussion_r3755133994) | 정의하지 않은 요청 필드 거부 | 애플리케이션 | 수정 필요 | controller에서 `restaurantIds` 외 필드를 400 `INVALID_REQUEST`로 거부 | API 회귀 테스트 |
| [3755133999](https://github.com/team-youngkk/masit-on/pull/171#discussion_r3755133999) | Kakao 요청에 `summary=true` 명시 | 애플리케이션 | 수정 필요 | query parameter 추가 | WireMock 요청 journal 검증 |
| [3755368863](https://github.com/team-youngkk/masit-on/pull/171#discussion_r3755368863) | 성공적인 in-flight permit마다 TTL 갱신 | 인프라 | 수정 필요 | Lua가 매 성공 획득마다 TTL을 갱신하고 rate·concurrency 실패를 구분 | 실제 Redis TTL 회귀 테스트 |
| [3755368870](https://github.com/team-youngkk/masit-on/pull/171#discussion_r3755368870) | 80% 경보와 호출·차단·잔여 quota 계측 | 인프라 | 수정 필요 | Lua 사용량 반환, Micrometer counter/gauge, 월별 1회 warning 추가 | quota 경계·지표·로그 회귀 테스트 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: Compose에서는 Mobility base URL이 `localhost:8081`로 남고, 운영에서는 gate 환경 변수가 비어 `PROVIDER_BLOCKED`가 된다. 월 quota 거부는 `SERVICE_RATE_LIMIT`으로 매핑됐다.
- 발생 환경: `feature/ws-16-course-route-recommendation`, Docker Compose local, SSM 값을 `app-run.sh`에서 읽는 prod 배포, Redis 8.8.
- 재현 조건: Compose app에서 Mobility base URL을 전달하지 않거나, 운영 gate를 전달하지 않거나, quota를 소진하거나, in-flight 키가 최초 획득 후 10초를 넘긴다.
- 실제 결과: 정상 local 경로가 WireMock 대신 app 자신에게 연결되고, 운영 경로가 항상 차단되며, quota hard stop이 429로 노출되고, in-flight 상한과 사용량을 안정적으로 관측할 수 없다. unknown 필드는 유효한 요청으로 진행됐다.
- 기대 결과: 컨테이너 간 주소가 분리되고 운영 gate가 전달되며, provider 비용 차단은 502 `COURSE_ROUTE_PROVIDER_UNAVAILABLE`, 서비스 요청률·동시성만 429, unknown 필드는 400, quota 경계는 관측 가능해야 한다.
- 영향 범위: 코스 추천 외부 호출과 운영 비용·가용성에만 영향이 있고 기존 맛집 탐색·저장 데이터에는 영향을 주지 않는다.

## 4. 근본 원인

배포 설정은 `application-prod.yml`의 fail-closed 기본값을 전제로 하지만 Compose와 `app-run.sh`가 Mobility 관련 비밀이 아닌 설정을 전달하지 않았다. Adapter는 월 quota permit과 서비스 요청 permit을 같은 `false` 값으로 받아 월 비용 차단을 서비스 rate limit으로 분류했다. Redis Lua는 최초 in-flight 증가 때만 TTL을 설정했고, quota Lua의 사용량 반환값과 계측 계층이 없었다. 또한 Jackson 3 경로에서는 DTO에 추가한 Jackson 2 annotation이 unknown field를 거부하지 않아 요청 DTO 경계가 실제로 보장되지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `git status --short --branch`, PR diff와 계약 문서 대조 | 작업 브랜치와 기존 변경은 보존됐고, API·ADR은 provider hard stop을 502로 정의 | 기존 계약에 맞춰 최소 변경 |
| DTO에 `@JsonIgnoreProperties(ignoreUnknown = false)` 추가 후 MockMvc 실행 | Jackson 3 역직렬화에서 unknown field가 무시되어 404까지 진행 | controller에서 허용 필드를 직접 검사하도록 변경 |
| WireMock 요청 journal 확인 | 기존 요청에 `summary`가 없었고 좌표 순서·호출 1회는 유지됨 | `summary=true`를 query contract로 고정 |
| Redis Lua와 TTL 동작 확인 | in-flight TTL이 최초 획득 때만 설정됨 | 모든 성공 획득에서 TTL 갱신, 실제 Redis TTL 테스트 추가 |

## 6. 최종 해결

- 변경 내용: Compose app에 WireMock Mobility 주소를 추가하고, 운영 실행 스크립트에서 Mobility gate 두 값을 읽어 전달한다. 월 quota 거부는 provider 차단으로 매핑하고, 요청 DTO의 허용 필드를 명시적으로 검사한다. Kakao 요청에 `summary=true`를 추가한다. Redis Lua는 사용량·차단 원인을 반환하고 성공 permit마다 in-flight TTL을 갱신하며, Micrometer 지표와 월 80% warning을 기록한다.
- 선택 이유: API 오류 계약과 NFR-COST-001을 바꾸지 않고 현재 Port/Adapter·Redis 경계에서 문제를 해결하며, provider 호출 전 차단과 기존 기능 격리를 유지한다.
- 변경 파일: `docker-compose.yml`, `deploy/scripts/app-run.sh`, `src/main/java/com/masiton/restaurant/presentation/rest/RestaurantCourseRouteController.java`, `src/main/java/com/masiton/restaurant/infrastructure/external/config/KakaoMobilityCourseRouteAdapter.java`, `src/main/java/com/masiton/restaurant/infrastructure/external/config/KakaoMobilityConfiguration.java`, `src/main/java/com/masiton/restaurant/infrastructure/redis/RedisCourseRouteQuota.java`, 관련 WireMock·API·Redis 테스트.
- 고려한 대안: provider quota 전용 새 오류 범주를 추가하는 방법도 있었지만 기존 `PROVIDER_BLOCKED`가 무료 quota 미확인·유료 호출 차단을 이미 표현하고 Application의 502 매핑도 갖고 있어 범위를 늘리지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `./gradlew.bat test --tests ...KakaoMobilityCourseRouteAdapterWireMockIntegrationTest --tests ...RestaurantCourseRouteApiTest --tests ...RedisCourseRouteQuotaIntegrationTest --no-daemon` | 통과 | Adapter 21건, API 19건, Redis quota 2건. summary, 502 분류, unknown field, TTL, quota 지표·경보를 확인했다. |
| `./gradlew.bat test --tests ...RestaurantCourseRouteApiTest --no-daemon` | 통과 | API 19건 재검증. |
| `docker compose config` 및 `rg`로 Compose·운영 전달 경로 대조 | 통과 | app의 `http://wiremock:8080`, SSM gate 읽기, `docker run -e` 전달을 확인했다. |

## 8. 재발 방지 및 다음 확인

- 재발 방지: WireMock query contract, API unknown-field, provider quota category, Redis TTL, quota 경계·Micrometer 지표·warning 테스트를 추가했다.
- 다음 확인: Mobility 실제 Free Tier quota와 운영 SSM 값은 기능 활성화 직전 소유자가 확인한다. 실제 quota 대조·부하 측정은 3차 확장 최종 게이트에서 수행한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 월 quota 사용·잔여량 | 애플리케이션 지표 없음 | Micrometer quota gauge와 provider console 대조 | 실제 운영 확인 예정 | 관측 가능 경로 확보 | WS-16 담당자, 활성화 전 |
| 코스 호출·차단 건수 | 애플리케이션 지표 없음 | Micrometer counter를 운영 기간별 집계 | 실제 운영 확인 예정 | 호출/차단 추세 확인 가능 | WS-16 담당자, 활성화 후 |
| quota 80% warning | 로그·경보 없음 | warning 로그와 quota 사용량 경계 대조 | 실제 운영 확인 예정 | 경계 알림 경로 확보 | 운영 담당자, 활성화 전 |

## 10. 남은 사항

- 미해결 스레드: 없음. 모든 요청사항을 반영하고 원격 브랜치 검증 후 인라인 답글로 처리한다.
- 필요한 결정: 없음.
- 검증 제약: 실제 Kakao Mobility 계정 quota와 AWS SSM은 로컬 테스트에서 접근하지 않으며, 운영 활성화 전 별도 확인한다.
