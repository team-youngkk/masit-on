---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../troubleshooting/pr-279-ai-place-identity-review.md
---

# #415 전국 주소와 인천 장소 검색 진단

## 문제와 확인 근거

손선장(인천 남동구 소래역로 12), 대풍식당·교동다방(인천 강화군 교동면 대룡리)의 과거 결과는 PLACE_NOT_FOUND다. 원 응답과 당시 실행 SHA가 없어 검색 후보 누락과 물리 검증 실패를 직접 원인으로 확정할 수 없다.

2026-10-14 작업 기준 origin/develop은 `8fef6f89`다. 전국 주소 정규화는 `4de14264`(#395)에서 이미 반영되었다. 원 작업 디렉터리 `e72a8268`의 서울 한정 설명은 현재 원격 코드와 다르다. GitHub production deployment 최신 기록은 `58962dfc9a99de8752ad5db2f2b4b9400c182265`(2026-10-14 14:07:41 KST)이며 이 SHA에도 전국 정규화 코드가 존재한다. 배포 기록은 현재 프로세스의 실행 SHA·런타임 설정과 과거 작업 실행 SHA를 증명하지 않는다.

최신 deployment 기록의 최종 상태는 `failure`(2026-10-14 14:11:29 KST)다. 이 SHA가 실제 가동 중이라는 근거로 사용하지 않는다.

## 원인 분리와 변경 설계

현재 코드는 주소 시도·시군구 해석 → 상호명 검색 → 이름·지역 동일성 → 장소 URL 물리 검증 순서다. 지번 후보도 시도·시군구 단서로 사용할 수 있지만 Kakao 결과에는 도로명주소가 있어야 한다. 교동면·대룡리만 있어 도로명 결과가 없는 경우 후보를 제외하는 기존 계약을 유지한다. 전국 구현을 다시 도입하거나 판정 기준을 완화하지 않고 세 사례의 정상·지역 불일치·도로명 누락·동명 다중 후보를 회귀 테스트로 고정한다.

검색 Adapter는 WireMock의 고정 응답으로 인천 도로명주소 정규화를 검증한다. 동일성 서비스는 고정 후보로 검증하며 실제 Kakao·YouTube·Gemini를 호출하지 않는다. 실제 원 응답을 가정한 수치는 만들지 않는다. #416이 지역 결합·페이지 수집을, #420이 검색과 물리 검증 실패 단계를 담당하므로 여기서는 해당 production 파일을 변경하지 않는다.

## 의존성과 회귀 제약

BR-AIEXTRACT-009의 exact-name 우선·시도/시군구 일치·유일 후보 조건, legacy matchedBy 호환, BR-AIEXTRACT-011의 외부 검증 및 등록 원자성을 유지한다. 과거 Snapshot과 운영 데이터를 수정하지 않는다. #279 기록의 입력 정규화와 과거 감사 값 보존 제약은 현재 계약에도 유효하다.

## 검증 계획과 결과

`.\gradlew.bat test --tests '*RoadAddressNormalizerTest' --tests '*ResolvePlaceIdentityServiceTest' --tests '*KakaoPlaceSearchAdapterWireMockIntegrationTest' --no-daemon --console=plain`은 BUILD SUCCESSFUL이다. Java production/test 컴파일 및 전국 주소 24건·기존 서울 정규화 2건·장소 동일성 28건·WireMock 검색 16건·자동 포함된 ArchUnit 10건, 총 80건이 실패 없이 통과했다. `git diff --check`도 통과했다. 세 사례의 동일 지역 도로명 후보는 확정되지만 다른 군·복수 후보·도로명 누락은 여전히 보류한다. 이는 고정 응답의 회귀 결과이며 실제 사례의 수정 효과나 원 응답 복구를 의미하지 않는다.

독립 리뷰에서 확인된 P1/P2 지적은 없었으며 리뷰자가 같은 대상 테스트와 diff 검사를 다시 실행해 BUILD SUCCESSFUL을 확인했다. 운영 프로세스 SHA·설정, 과거 Kakao 원 응답, 실측 차단율은 미확인이다. 운영 재추출·등록·폐기·롤백·배포는 범위 밖이다.
