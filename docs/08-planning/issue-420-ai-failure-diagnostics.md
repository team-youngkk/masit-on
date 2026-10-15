---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../troubleshooting/pr-244-registration-unit-atomicity-review.md
  - ../troubleshooting/pr-279-ai-place-identity-review.md
---

# 이슈 #420 등록 실행 실패 진단

## 문제와 원인

등록 실행 서비스가 장소·카테고리·영상 단계의 모든 RuntimeException을 EXTERNAL_SERVICE_ERROR로 변환한다. DB 조회 오류·서버 결함이 Kakao·YouTube 장애와 구분되지 않으며 장소 검색과 물리 검증 실패 위치도 남지 않는다.

## 범위와 설계

기존 BR-AIEXTRACT-011의 차단 사유, API·DB·Prompt·Schema는 유지한다. 기존 PlaceSearchFailedException, PlaceVerificationFailedException, BusinessException(EXTERNAL_SERVICE_ERROR)만 외부 오류로 취급한다. 그 밖의 런타임 예외는 INTERNAL_FAILURE로 기록한 뒤 원래 예외를 전파한다. 서버 결함을 정상적인 AUTO_BLOCKED로 저장하지 않는다.

내부 구조화 로그는 PLACE_SEARCH, PLACE_PHYSICAL_VERIFICATION, CATEGORY, YOUTUBE 단계, EXTERNAL_TIMEOUT·EXTERNAL_RESPONSE_FAILURE·INTERNAL_FAILURE 유형과 서버 traceId를 기록한다. 예외 메시지·원문·주소·URL·토큰·stack trace는 기록하지 않는다. 기존 서버 MDC traceId가 없으면 해당 실행의 서버 UUID를 생성한다. 장소 검색/물리 검증은 각각의 호출 경계에서 기록해 단계 혼동을 피한다.

외부 호출 중 트랜잭션을 열지 않고 실패 전에 핵심 Entity 등록 호출이 없음을 유지한다. PR #244의 CAS·보상 경계를 변경하지 않으며 PR #279의 legacy 감사 값도 변경하지 않는다.

## 의존성과 남은 범위

진단 기반은 독립 구현한다. 작업/등록 단위 분모 구분, 기간·유입경로·모델/Prompt/Schema별 통계는 최종 통합 후 별도 마무리한다. YouTube application 변환은 안전한 기존 BusinessException 코드·메시지를 유지하면서 cause를 보존해 timeout 세부 분류를 지원한다. 운영 6개 확정/4개 차단 사례는 이번 작업에서 재실행하지 않는다.

## 검증 계획

정상 등록·기존 차단 회귀, 검색/물리/카테고리/YouTube 예외 단계, 외부 timeout 원인 분류, 내부 결함 전파, 등록 호출 0건, 로그 원문 미포함·traceId 확인을 고정 Mockito 후보로 검증한다. 컴파일·단위 테스트·ArchUnit을 실행한다. 실제 외부 API는 호출하지 않는다.

## 검증 결과

컴파일과 등록 실행 11건·ArchUnit 10건은 통과했다. 최초 ArchUnit에서 Application의 HTTP 예외 직접 참조를 검출했고 common observability 원인 분류기로 분리해 해결했다. YouTube cause 보존 신규 회귀를 포함한 최종 재검증 결과는 아래에 추가한다. 독립 리뷰는 주 에이전트가 통합 전에 수행한다.

## 전체 통계 계약 — 소유자 합의 확보

사용자 확인으로 AI/API/데이터 소유자 합의를 확보했다. from/to는 필수 ISO-8601 offset 시각이며 UTC instant로 비교한다. from < to, 최대 365일을 허용한다. source/modelVersion/promptVersion/schemaVersion 필터와 해당 네 차원의 고정 그룹을 제공한다. 실행 시도 누적 지표는 포함하지 않고 작업의 저장된 버전만 사용한다.

- API: 관리자 전용 `GET /api/admin/ai/video-extractions/statistics`. 기존 작업 API namespace에 추가한다. `from` 포함·`to` 제외 UTC 작업 생성 시각 범위를 사용하고, 유입경로·모델·Prompt 버전·Schema 버전으로 필터하고 네 차원의 고정 그룹을 반환한다. 기간은 작업 created_at 기준이며 마지막 수정 시각 기준과 혼용하지 않는다.
- 작업 분모: 해당 범위의 고유 작업 수. 상태별 작업 수는 작업 테이블에서 별도로 집계한다. 등록 단위 join으로 작업 수를 중복 계산하지 않는다.
- 단위 분모: 해당 작업의 최신 Snapshot에 속한 등록 단위 총수. 현재 상태별 전체 수와 판정 완료 분모를 모두 반환한다. 판정 완료 분모는 AUTO_CONFIRMED + AUTO_BLOCKED + AUTO_REJECTED이고, MANUAL_OVERRIDE는 별도 수로 노출하며 롤백·폐기 단위도 그 상태 개수에 포함한다. 실제 등록 여부는 registration_result 존재와 현재 lifecycle을 별도 지표로 집계해 AUTO_CONFIRMED를 누적 등록 성공 횟수로 해석하지 않는다.
- 차단율: AUTO_BLOCKED / 판정 완료 단위 수. 사유 비율은 사유별 AUTO_BLOCKED / 전체 AUTO_BLOCKED이며 전체 단위 대비 비율과 필드명을 구분한다. 분모 0이면 비율 null, count는 0으로 반환한다. 6개 확정/4개 차단 고정 사례는 totalUnits=10, evaluatedUnits=10, blockedUnits=4, blockedRate=0.4로 검증한다.
- 이행: 재시도한 단위의 현재 상태만 집계한다. 실행 시도별 누적 감사 집계는 이번 범위에서 제외한다. 차원은 작업의 저장된 버전이며 없는 과거 값을 추정해 채우지 않는다.
- 로그의 실패 단계·유형은 내부 진단이며 DB 통계 차원이 아니다. 영속 단계 집계가 필요하면 nullable 진단 필드·감사 이벤트 스키마·보존기간·migration을 별도 합의한다. 원문·비밀값은 저장하지 않는다.

## Worker 경계 확인

독립 리뷰에서 P1/P2 지적 없이 대상 테스트·ArchUnit과 diff 검사가 다시 통과했다. MDC가 없는 경우 생성 UUID 형식의 전용 회귀는 추가 보강 가능 항목으로 남는다. 전체 통계는 별도 승인·최종 통합 범위다.

AiExtractionWorkerService는 일반 RuntimeException을 INFRASTRUCTURE로 기록하고 lease 복구 재시도를 맡긴다. EXTERNAL_SERVICE_ERROR 차단으로 재분류하지 않는다. 관리자 경로는 원래 서버 예외가 기존 전역 오류 경계로 전달된다. 등록 실행의 외부 검증 단계 이전 오류는 정식 등록 호출 0건이다. Worker 통합 전체 회귀는 최종 통합 후 검증한다.

최종 검증: `.\gradlew.bat test --tests com.masiton.orchestration.application.RegistrationUnitExecutionServiceTest --tests com.masiton.video.application.ResolveVerifiedVideoServiceTest --tests com.masiton.architecture.ArchitectureTest --no-daemon --console=plain` 통과(22건, 실패 0건). `git diff --check` 통과. 실제 외부 API·운영 데이터는 호출/변경하지 않았다. 전체 통계·Worker 통합 전체 테스트·독립 리뷰는 후속 통합 범위로 남긴다.


## 전체 통계 구현과 검증

별도 Statistics Port/Application Service/Controller/JDBC Adapter로 구현해 기존 작업 목록·상세 Controller와 등록 실행 공통 파일은 수정하지 않았다. 고유 작업의 상태별 집계와 최신 Snapshot의 단위별 집계를 별도로 만든 뒤 단일 SQL UNION 결과를 합쳐 조회 시점 불일치와 작업 분모 중복을 방지한다. SQL 필터는 바인딩 파라미터를 사용한다. 기존 PostgreSQL 모델 고정 제약은 유지한다.

검증은 실제 PostgreSQL Testcontainers와 MockMvc를 사용한다. 6개 확정/4개 차단, 전체 확정/차단/빈 분모, 최신 Snapshot, 수동 등록/롤백/폐기, 자동 거절 분모, 시작 포함/끝 제외, source/모델/Prompt/Schema 필터 및 그룹, ADMIN·미인증·비관리자와 400·traceId를 확인한다. 초기 테스트에서 잘못된 fixture 모델/URL/입력 모드/리소스 ID 길이와 SQL GROUP BY 위치 오류를 확인하고 수정했다. 최종 결과는 재검증 후 기록한다. 운영 데이터·실제 외부 API·DB migration은 변경하지 않는다.

최종 통계 검증: `.\gradlew.bat test --tests '*AiExtractionStatistics*' --tests com.masiton.architecture.ArchitectureTest --no-daemon --console=plain` 성공(통계 10건 + ArchUnit 10건, 실패 0건). `compileJava`와 `git diff --check` 성공. 독립 리뷰 및 전체 통합 검증은 주 에이전트의 후속 검증 범위다.

## 독립 리뷰 입력 경계 보완

버전 필터 NUL 및 제어 문자를 저장소 호출 전에 400과 traceId로 거부한다. 기존 SafeTextPolicy와 원문 제어 문자 검사를 사용하며 집계 SQL과 null 비율은 유지한다.

리뷰 보완 검증: 컴파일과 관련 테스트 17건(ArchUnit 포함)이 통과했다. 실제 Provider 호출 없이 고정 후보·Mockito 및 통계 MockMvc/Testcontainers로 검증했다.
