---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - ../06-architecture/external-integration.md
  - ../07-adr/integration/ext-001-reference-verification.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../troubleshooting/pr-278-kakao-place-name-matching-review.md
  - ../troubleshooting/pr-279-ai-place-identity-review.md
---

# 이슈 #416 지역 결합 검색과 제한된 후보 수집

## 문제와 원인

현재 클라이언트는 이름만 검색하고 첫 페이지를 반환한다. 지역 필터는 이후 orchestration에서 적용하므로 동명 식당이 많고 목표가 후속 페이지에 있으면 후보 누락이 생긴다. 이 위험은 코드로 확인했지만 운영 사례의 원인으로 확정하지 않는다.

## 변경 범위와 설계

자동 장소 동일성 경로에 지역 결합 후보 검색을 추가하고 기존 수동 물리 검증의 단일 페이지 search(name)는 유지한다. 공개 HTTP API·DB·Prompt·Schema·판정 문자열은 변경하지 않는다. 내부 공개 Port에 후보 검색 메서드를 추가한다.

정규화된 시·구 지역과 상호명을 query에 결합한다. 최대 3페이지를 수집하고 Kakao 장소 URL(ID)로 중복 제거한다. meta.is_end가 true인 완전한 수집만 판정에 전달한다. meta.is_end·total_count·pageable_count 누락, total_count > pageable_count, 잘못된 문서·페이지 상한 도달은 외부 검색 실패로 반환하여 일부 후보를 유일한 후보로 확정하지 않는다. exact-name 우선, 지역 일치, category fallback, 유일 후보 조건과 운영 flag는 유지한다.

연결 timeout 2초·요청 응답 timeout 최대 5초를 유지한다. 한 검색 query 전체 시간 예산도 5초로 제한해 후속 요청의 timeout을 잔여 예산으로 줄인다. 원래 이름과 명시적 지점 접미사 제거 이름의 최대 2 query, 최대 6 요청·총 응답 예산 10초다. 재시도·새 서비스·유료 전환·분산 rate limiter는 추가하지 않는다. 기존 무료 제공자 쿼터 오류는 즉시 실패한다. 이 호출 상한은 이슈에서 요청한 bounded 수집 구현이며 비용 정책 자체를 변경하지 않는다.

## 의존성과 회귀 위험

#415·#420 기반 독립 리뷰 완료 뒤 구현한다. #415 Adapter production 변경은 없어 본 작업이 해당 경계를 소유한다. #417 입력 분해는 별도 담당이며 등록 실행 서비스·방문 근거 판정은 수정하지 않는다. PR #278·#279의 검색 감사 문자열·legacy 값·완화 매칭 flag 기본값은 유지한다. 정확한 검색 query 영속 추가는 데이터 계약 합의가 필요하므로 이번 범위에 포함하지 않는다.

## 검증 계획

고정 WireMock으로 동명식당·후속 목표·동일 ID 중복·다중 적합 후보·500·timeout·3페이지 상한·meta 누락을 검증한다. 기존 수동 search의 query와 물리 검증 회귀를 유지한다. ResolvePlaceIdentityService의 exact/지역/category/unique/flag 테스트와 컴파일·ArchUnit을 실행한다. 실제 Kakao 호출과 운영 데이터 변경은 하지 않는다.

## 검증 결과

독립 리뷰에서 total_count > pageable_count가 정상 Kakao metadata라는 점을 재대조했다. 이 경우를 허용해 노출된 후보만으로 확정하면 사용자 요구의 전체 수집 완전성을 위반하므로 차단을 유지한다. 형식 오류(total < pageable 등)와 정상 metadata의 제공자 노출 상한을 코드 분기로 구분했고, 기존 통계상 EXTERNAL_SERVICE_ERROR 분류는 유지한다. 이 판단은 전체 후보가 보이지 않은 상황에서 오확정을 피하기 위한 제한이다.

독립 리뷰에서 P1/P2 지적 없이 위 판정과 diff 검사를 확인했다. 리뷰자가 같은 작업 디렉터리에서 테스트를 동시에 실행해 결과 파일 잠금이 발생한 1회 시도는 환경 충돌이며, 실행을 직렬화한 최종 검증 결과를 사용한다.

## PR #430 후속 검증

CI에서 기존 `ResolvePlaceIdentityServiceTest` 네 건이 실패해 원인을 분석하고 [리뷰 반영 기록](../troubleshooting/pr-430-bounded-place-search-ci-review.md)에 수정·재검증 내용을 남겼다. 지역 후보 검색 포트 전환 뒤 테스트 스텁이 이전 `search(name)`에 남은 것이 원인이었다. 수정 후 `ResolvePlaceIdentityServiceTest`, `KakaoPlaceSearchAdapterWireMockIntegrationTest`, `KakaoPlaceVerificationAdapterTest` 71건이 통과했다.

## 제공자 계약 확인

[Kakao Local 공식 문서](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword)의 meta.total_count(전체 결과), pageable_count(노출 가능 결과), is_end(후속 페이지 여부)를 확인했다. 전체 결과가 노출 가능 결과보다 많으면 마지막 페이지를 받더라도 유일성 증명이 불가능하므로 검색 실패한다. 조회 예산은 제공자의 전체 쿼터를 변경하는 설정이 아니라 단위 실행의 최대 호출 수다. 유입 증가에 대한 전역 rate limit은 기존 ADR의 실측 후 결정 정책을 유지한다.

초기 PR 검증 (기준 커밋 `a730ce17`): `.\gradlew.bat test --tests '*KakaoPlaceSearchAdapterWireMockIntegrationTest' --tests '*ResolvePlaceIdentityServiceTest' --tests '*KakaoPlaceVerificationAdapterTest' --no-daemon --console=plain` 통과. 장소 판정 25건·후보 WireMock 22건·기존 물리 검증 15건·ArchUnit 10건, 총 72건 실패·오류 0건이다. compileJava·compileTestJava 포함 통과했고 `git diff --check`도 통과했다. 고정 후보·WireMock만 사용했으며 실제 Kakao 요청·운영 데이터 작업은 수행하지 않았다. 최종 이슈 간 통합 회귀는 아직 남아 있다.
