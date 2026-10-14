---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
---

# 이슈 414 location 지역 검증 계약 제안

> 상태: **승인(2026-10-14)**. 사용자가 두 계약 소유자 합의 확보를 확인하여 제안대로 구현한다.

## 구현 설계

주소가 비어 있을 때만 S3 `registrationUnits`의 같은 순번, 명시적 `candidateUnitId`, 동일 `restaurantName`으로 결속된 `location.value`를 실행 명령에 전달한다. S2 전역 location은 사용하지 않는다. 시도·시군구 두 단어와 세종 단독 행정구역 표현을 정규화하고 기존 도로명주소 지역과 비교한다. 주소가 존재하나 파싱되지 않으면 location으로 대체하지 않는다. 관리자·Worker 경로는 같은 JSON 검증 함수를 사용하며 최종 물리 검증과 단일 후보 기준은 유지한다.

## 결정 요청과 범위

주소가 없는 후보의 `location`을 Kakao 검색을 좁히는 **지역 단서**로만 허용할지 결정한다. location만으로 도로명주소·좌표·Kakao 장소를 추측하거나, 첫 검색 결과를 확정하는 것은 제안 범위에서 제외한다.

## 제안 규칙

- S3의 같은 등록 단위 안 location만 사용한다. S2의 복수 후보 또는 다른 단위 location은 사용하지 않는다.
- 허용 값은 행정구역 계층 중 시/도와 시·군·구가 모두 파싱되는 명시적 텍스트다. 읍·면·동은 검색 보조 조건일 뿐 동일성 확정 단독 근거가 아니다.
- Kakao 결과의 도로명 또는 지번 주소에서 동일 시/도·시군구가 확인되고, 기존 exact-name 우선·필수 필드·단일 qualifying candidate 조건도 모두 충족할 때만 `NAME_AND_DISTRICT`를 유지한다.
- location이 모호하거나 주소 결과의 행정구역과 불일치하면 `PLACE_AMBIGUOUS` 또는 `PLACE_NOT_FOUND`로 보류한다. address와 location이 모두 없으면 기존 `MISSING_REQUIRED_FIELD`를 유지한다.
- location은 정식 Restaurant 주소로 저장하지 않는다. 확정된 Kakao 주소만 저장한다.

## 계약 영향과 의존성

이 규칙은 ‘주소가 없어도 location으로 자동 확정 가능’이라는 해석을 포함하므로 BR-009/011, API의 `placeDecision.matchedBy`, PRD, ADR-AI-001 합의가 필요하다. #417의 단위 결속과 #416의 지역 결합·bounded 페이지 검색이 선행되어야 한다. #415의 전국 주소 진단 결과는 파서 범위와 회귀 fixture를 제공하지만 승인 조건은 아니다.

## 검증 계획

WireMock으로 서울·인천 남동구·강화군의 정상 일치, 동명 식당, location 모호, location/결과 지역 불일치, address/location 모두 없음, 후속 페이지 목표 후보를 검증한다. 자동 확정은 qualifying candidate가 정확히 한 건일 때만이며 외부 API 실제 호출은 하지 않는다.

## 위험과 기본값

지역명은 행정구역 개편·동음 지명·상권명과 다를 수 있다. 따라서 파싱 실패와 애매함은 차단한다. 합의 또는 #417/#416 선행 구현이 없으면 현행 주소 필수 검증을 유지한다.

## 구현 검증 결과

2026-10-14 Gradle Wrapper로 `ResolvePlaceIdentityServiceTest`, `RegistrationUnitExecutionServiceTest`, `RegistrationUnitCommandServiceTest`, `RegistrationUnitAutoExecutionServiceTest`, `RegistrationUnitJsonSupportTest` 및 자동 포함된 ArchUnit을 실행했다. 컴파일 성공, 66건 통과·실패 0건이다. 고정 Kakao 후보와 Mockito를 사용했으며 실제 외부 API를 호출하지 않았다. 같은 단위 위치 결속·다른 식당 결속 거부·legacy 전역 위치 거부·중복 식별자 거부·모호한 상권·지역 불일치·복수 후보·기존 주소 우선·세종 경계를 검증했다. 최종 물리 검증 결과의 지역도 장소 동일성 결과와 다시 비교한다.

#416 지역 결합 검색과 #417 S3 단위 분해는 이 브랜치에 복사하지 않았다. 선행 PR 통합 후 해당 검색·분해 경로의 통합 검증이 필요하다. 독립 리뷰에서 P1/P2 지적은 없었다. 부모 에이전트가 최종 물리 지역 일치 성공·불일치 시 저장 0건 회귀 2건을 추가하여 동일 대상 재검증 68건이 실패 없이 통과했다.
