---
related_documents:
  - README.md
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../07-adr/integration/ext-003-ai-extraction-async-reliability.md
  - issue-414-ai-contract-proposal.md
  - issue-417-ai-contract-proposal.md
  - issue-420-ai-failure-diagnostics.md
---

# 이슈 #421 실패 사유별 자동 보완·제한 재검증 설계

## 문제와 원인

AI Provider가 성공해 후보 Snapshot과 등록 단위가 생성된 뒤에도 주소·지역 단서 누락, 장소 후보 검색 실패, 후보와 장소의 연결 불명확, 방문 근거 부족으로 일부 단위가 `AUTO_BLOCKED`에 남을 수 있다. 현재 재시도 API는 관리자가 새 작업을 명시적으로 요청하는 경로이고, Worker의 Provider 재시도는 일시 오류 복구를 위한 최대 시도 정책이다. 두 경로는 성공 결과 뒤의 단위별 검증 실패를 자동 보완하지 않는다.

## 승인된 범위와 계약

사용자가 2026-10-15 전달한 소유자 합의에 따라 다음 경계 안에서 구현한다.

- 추가 입력은 공개 영상 제목·설명과 승인된 위치 표현만 허용한다. 위치 표현은 #417의 `registrationUnits[].location` 후보 중 같은 등록 단위에 결속된 값만 사용한다.
- 추가 입력은 기존 암호화 임시 입력 경계로 암호화해 최대 24시간 보존하고 종료·만료 정리를 적용한다.
- 한 등록 단위는 자동 보완을 한 번만 수행한다. 재시작·동시 Worker·관리자 처리와 경합해도 한 번만 획득되어야 한다.
- 한 작업의 Provider 호출은 최초 호출과 기존 Provider 재시도를 포함해 최대 4회다. 기존 비용·quota 정책, Provider·모델·Prompt·Schema 버전은 바꾸지 않으며 quota hard stop을 호출 전에 적용한다.
- 신규 시도와 Snapshot은 감사 이력으로 추가한다. 기존 후보, 판정, 등록 및 관리자 보정 이력을 덮어쓰지 않는다.
- 후속 Snapshot은 기존 전체 후보를 복사하고 실패 단위의 후보만 교체한다. 이전에 통과한 단위는 결과·판정을 그대로 carry-forward하며 재검증하지 않는다. 해결되지 않은 단위는 기존 차단 사유를 보존해 관리자 보류한다.
- 실제 Kakao·YouTube·Gemini 호출 없이 고정 후보와 WireMock으로 검증한다.

## 사유별 처리 경계

| 기존 결과 | 자동 처리 | 종결 |
|---|---|---|
| 주소 누락 또는 승인된 지역 단서가 있는 위치 누락 | 공개 제목·설명 및 같은 S3 등록 단위의 위치 표현을 보완 입력으로 사용하고 기존 #414/#416 장소 검색을 재사용 | 기존 Kakao 동일 장소 판정을 통과한 경우만 해결 |
| 장소 검색 실패 | 이미 수행한 것과 중복되지 않는 #416 지역 결합 검색·기본 상호명·제한 페이지 후보 수집을 재사용 | 단일 유효 후보를 확인하지 못하면 기존 사유로 보류 |
| 후보와 장소 연결 불명확 | #417 식당별 단위·해당 단위의 입력만 사용해 제한 재추출 및 재검증 | 동일성 기준을 통과하지 못하면 보류 |
| 방문 근거 부족 | 같은 YouTube 제작자·영상·식당에 결속된 후보 근거만 다시 검증 | 유효 TIMESTAMP 또는 TEXT_RANGE 근거가 없으면 보류 |
| 이미 등록·관리자 종결, 중복, 정책/입력 거부, quota hard stop, 내부 결함 | 자동 재추출하지 않음 | 기존 정식 처리·거부·오류 경계 유지 |

자동 보완 1회 제한과 Provider 재시도는 별개로 카운트하지 않는다. Provider 호출마다 기존 작업의 공통 호출 예산과 quota 확인을 먼저 통과시킨다. 호출 상한이나 quota가 먼저 닿으면 추가 호출을 시작하지 않는다.

## 계약·데이터 영향

- 현행 BR-AIEXTRACT-004의 관리자 명시 재처리 문구 및 BR-AIEXTRACT-007의 관리자 보완 입력 전용 문구는 자동 보완 승인을 반영하도록 구체화해야 한다.
- 기존 임시 입력 테이블·trigger는 `ADMIN/ADMIN_TEXT` 작업만 허용하므로 승인된 자동 입력 출처의 암호문도 제한적으로 수용하도록 전진 migration이 필요하다. 기존 migration은 수정하지 않는다.
- Provider 시도 테이블은 `(job_id, attempt_no)` 단위로 이력과 호출 수를 기록한다. Snapshot과 등록 단위의 원본 연결, 단위별 1회 획득과 상태 전이를 위한 추가 감사/제약 설계는 새 Snapshot의 범위 결정에 종속된다.
- 관리자 API는 기존 상세·목록에서 최신 Snapshot과 시도 상태를 읽는 계약을 확인한다. 새 Enum과 자동 재처리용 공개 Route는 추가하지 않는다.
- 비용 한도, quota 계산·집계, 평가 판정 기준, Provider·모델·버전은 변경하지 않는다.

## 의존성

- #420: 외부 검증 실패 단계·분류와 관리자 통계를 재사용한다.
- #417: P9/S3 식당 단위와 `candidateUnitId` 결속을 유지한다. flat 후보나 S2 위치로 복구하지 않는다.
- #414/#416: 동일 등록 단위 위치 단서와 제한된 Kakao 검색만 재사용한다.
- #419: 기존 Restaurant·Visit 재사용과 원자 사후 조치 의미를 바꾸지 않는다.
- #412: 최신 Snapshot 기반 관리자 단위 집계와의 호환을 유지한다.

## 검증 계획

- 실패 사유별 자동 보완 허용·제외, 이미 등록/관리자 종료 단위 제외, 단위별 자동 보완 1회, 재시작·동시 획득을 검증한다.
- 최초 호출·기존 Provider 재시도·자동 보완을 합쳐 4회 이하인지, quota hard stop 및 호출 예산 소진 전 Provider 호출이 0회인지 검증한다.
- 제목·설명·같은 단위 location만 입력에 포함되고 다른 단위 위치, 원문 URL 이외의 비공개 입력, 전체 영상/자막은 포함되지 않는지 검증한다.
- 새 시도·Snapshot이 원본 실패와 등록 단위에 연결되고 기존 Snapshot·판정·등록·감사 값이 유지되는지 검증한다.
- 자동 보완 후 실패 단위만 재검증하고 부분 성공 및 미해결 사유별 관리자 보류를 검증한다.
- 고정 후보, WireMock, PostgreSQL Testcontainers, 컴파일과 관련 Gradle 회귀를 사용한다. 외부 실제 API를 호출하지 않는다.

## Snapshot 결정 (2026-10-15)

사용자가 기존 전체 후보를 복사하고 실패 단위만 교체하는 방식을 선택했다. 기존 상세·통계의 최신 Snapshot 기준 동작을 유지하면서 성공 단위의 후보·판정·등록 식별자는 이전 Snapshot에서 그대로 carry-forward한다. 실패 단위 후보만 새 값으로 바꾸고 제한 재검증하며, 변경 전 후보와 모든 판정은 과거 Snapshot에 남긴다.
