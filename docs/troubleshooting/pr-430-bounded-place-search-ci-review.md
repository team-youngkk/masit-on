---
related_documents:
  - ../08-planning/issue-416-bounded-place-search.md
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../05-specs/api/admin/ai-video-extraction-api.md
  - pr-278-kakao-place-name-matching-review.md
  - pr-279-ai-place-identity-review.md
---

# PR #430 리뷰 트러블슈팅: 지역 결합 후보 검색 전환 후 CI 회귀

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#430 Kakao 지역 검색과 제한 페이지 후보 수집](https://github.com/team-youngkk/masit-on/pull/430) |
| 작성자 | `tjdgns0618` |
| 처리 일자 | 2026-10-15 |
| 범위 | 미해결 인라인 리뷰 두 스레드와 백엔드 CI의 장소 동일성 테스트 네 실패 |
| 주 문제 유형 | 애플리케이션 |
| 기존 기록 | [PR #278](pr-278-kakao-place-name-matching-review.md) 및 [PR #279](pr-279-ai-place-identity-review.md)의 장소 동일성·정규화 회귀 기준을 확인했다. 이번 실패는 검색 포트 교체 후 테스트 대역이 갱신되지 않은 문제이며, 기존 exact-name·지역·유일성 계약은 그대로 유지했다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [검색 수집 경계 확인](https://github.com/team-youngkk/masit-on/pull/430#discussion_r4278168080) | meta 불완전·노출 상한 초과·후속 페이지 오류에서 부분 후보를 확정하지 않는 정책 확인 | 애플리케이션 | 이미 해결 | 최신 코드가 전체 수집 완료와 제공자 metadata 유효성을 확인한 경우에만 후보를 반환한다. 이 스레드는 결함 요청이 아닌 검토 메모다. | `KakaoPlaceSearchAdapterWireMockIntegrationTest`의 meta 누락·상한·후속 오류 사례 통과 |
| [지역 검색 단위 테스트 회귀](https://github.com/team-youngkk/masit-on/pull/430#discussion_r4278934183) | `searchCandidates` 전환과 지역 기반 단위 테스트 스텁을 함께 정정하고 전체 테스트 통과 | 애플리케이션 | 수정 필요 | 기존 네 시나리오의 Mockito 설정을 `searchCandidates(name, normalizedDistrict)`로 바꾸고 실제 지역 인수를 검증했다. | 세 관련 테스트 클래스 71건 통과; [최신 PR CI](https://github.com/team-youngkk/masit-on/actions/runs/38691691260) 전체 성공 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: `ResolvePlaceIdentityServiceTest`에서 강화군 동명 식당 한 건과 인천 세 사례가 실패했다.
- 발생 환경: GitHub Actions, Java 21, PR #430의 `4c0618f3` 및 최신 develop 반영 커밋 `a8425d99`.
- 재현 조건: 지역 기반 장소 검색 경로가 실행되는 중 기존 단위 테스트가 `SearchPlacesByNameUseCase.search(name)`만 stub 한다.
- 실제 결과: [GitHub Actions run 38690238572](https://github.com/team-youngkk/masit-on/actions/runs/38690238572)에서 1,778건 완료, 네 건 실패, 두 건 건너뜀으로 끝났다. Mockito 기본 응답인 빈 목록을 받아 장소가 `PLACE_NOT_FOUND` 또는 기대와 다른 상태가 됐다.
- 기대 결과: 자동 동일성 경로는 상호명과 정규화된 시·구를 함께 검색하고, 기존 exact-name·지역·후보 유일성 판정을 유지한다.
- 영향 범위: 기능 코드 결함은 아니지만, 핵심 장소 동일성 회귀 테스트가 새 검색 계약과 연결되지 않아 PR 통합 CI를 막았다.

## 4. 근본 원인

`ResolvePlaceIdentityService`는 이름 단독 `search(name)` 대신 이슈 #416에서 도입한 `searchCandidates(name, district)`를 호출한다. 그러나 네 기존 테스트의 응답 대역은 이전 메서드에 남아 있어, 변경된 경로에서 후보 목록을 공급하지 못했다. 실패 로그의 네 테스트(강화군 동명 후보 1건, 인천 매개변수 사례 3건)와 현행 서비스 호출·테스트 stub을 대조해 원인을 확정했다. 프로덕션 검색/판정 로직을 변경할 필요는 없었다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| 미해결 PR 리뷰 스레드와 대상 diff 조회 | 한 스레드는 수집 완전성 경계를 확인했고, 다른 스레드는 네 테스트 실패와 오래된 stub을 지적 | 두 스레드를 각각 처리 표에 기록하고, 실제 실패 경로를 확인 |
| GitHub Actions 실패 로그 조회 | 1,778건 완료, 4건 실패, 2건 건너뜀. 실패는 모두 `ResolvePlaceIdentityServiceTest`의 네 사례 | 같은 테스트와 서비스 호출을 대조해 stub 불일치를 확정 |
| PR #278·#279 장소 동일성 트러블슈팅 기록 확인 | 정규화, 지역 일치, 모호 후보 차단 기준은 현재에도 유효 | 판정 로직은 그대로 두고 검색 포트 인자만 테스트에 반영 |
| `ResolvePlaceIdentityServiceTest` 실행 | 30건 통과, 실패 0건 | 원래 실패 시나리오와 지역 인수 전달을 검증 |
| 장소 검색 Adapter·기존 Kakao 검증 Adapter 회귀 실행 | 합계 71건 통과, 실패 0건 | WireMock 후보 수집 경계와 수동 물리 검증 경로까지 함께 확인 |
| 전체 로컬 `test` 실행 | 1,778건 완료, 3건 실패, 2건 건너뜀. 실패는 `DependencyHealthContractTest` | 테스트가 시작한 `bash`와 `python`이 WindowsApps 별칭으로 연결되어 실제 인터프리터가 실행되지 않은 환경 문제로 분리 |
| 실제 Git Bash·bundled Python 경로를 앞세운 `DependencyHealthContractTest` 재실행 | 4건 통과, 실패 0건 | 전체 실행에서 실패했던 배포 스크립트 테스트를 유효한 실행환경에서 재검증 |

## 6. 최종 해결

- 변경 내용: 인천 세 사례의 parameterized test에 기대 정규화 지역을 추가하고, 강화군 동명·다중 후보 테스트를 지역 포함 후보 검색 stub으로 변경했다. 인천 사례는 `searchCandidates(name, district)` 호출을 명시적으로 검증한다.
- 선택 이유: 테스트가 현행 Application Port 계약을 사용하도록 바로잡으면 검색 동작을 바꾸지 않고 의도된 지역 경계와 판정 회귀를 검증할 수 있다.
- 변경 파일: `src/test/java/com/masiton/orchestration/application/ResolvePlaceIdentityServiceTest.java`, 이슈 #416 계획 문서, 이 기록과 troubleshooting 인덱스.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `.\gradlew.bat test --tests com.masiton.orchestration.application.ResolvePlaceIdentityServiceTest --no-daemon --console=plain` | 통과 | 장소 동일성 테스트 30건, 실패·오류 0건 |
| `.\gradlew.bat test --tests com.masiton.orchestration.application.ResolvePlaceIdentityServiceTest --tests com.masiton.restaurant.infrastructure.external.KakaoPlaceSearchAdapterWireMockIntegrationTest --tests com.masiton.restaurant.infrastructure.external.KakaoPlaceVerificationAdapterTest --no-daemon --console=plain` | 통과 | 세 클래스 합계 71건, 실패·오류 0건. 컴파일 포함 |
| `git diff --check` | 통과 | 문서·테스트 패치 공백 오류 없음 |
| GitHub Actions PR CI | 통과 | 수정 전 head `a8425d99`의 1,778건 완료·4건 실패·2건 건너뜀을 수정 커밋 `b62c8766`으로 해결. [run 38691691260](https://github.com/team-youngkk/masit-on/actions/runs/38691691260) 전체 성공 |

전체 로컬 `test`의 세 실패는 이번 변경과 무관한 Windows 실행환경 문제였다. `bash.exe`·`python.exe`가 `WindowsApps` 실행 별칭을 가리켜 실제 shell/Python 인터프리터가 실행되지 않았다(출력 종료 코드 `9009`). 실제 Git Bash와 bundled Python 경로를 우선해 해당 클래스 네 건이 통과하는 것을 확인했다. 전체 suite는 환경을 교정한 뒤 다시 통째로 실행하지 않았으며, 나머지 1,775건은 전체 실행에서 통과했다.

모든 검증은 고정 후보와 WireMock으로 수행했으며 실제 Kakao API 또는 운영 데이터를 사용하지 않았다.

## 8. 재발 방지 및 다음 확인

- 재발 방지: 단위 테스트가 변경된 Port 시그니처를 통해 stub 할 뿐 아니라 정규화된 시·구 인수를 검증하도록 했다.
- 확인 결과: 수정 커밋 `b62c8766`의 [GitHub Actions PR CI](https://github.com/team-youngkk/masit-on/actions/runs/38691691260)가 전체 성공했다. 리뷰 스레드 답글과 해결 상태를 동기화한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 장소 동일성 CI 실패 건수 | 1,778건 중 4건 실패 | 수정 커밋 이후 새 PR head에서 workflow 실행 | 로컬 관련 회귀 71건 중 0건 실패; 원격 전체 CI 통과 | 네 경계 시나리오와 전체 backend CI가 통과 | PR #430 run 38691691260, 2026-10-15 |

## 10. 남은 사항

- 회귀 수정 요청 스레드 두 건에 동일 원인·수정·검증 결과를 답글로 남겼다.
- 남은 절차: 답글이 표시되는지 확인하고 세 스레드를 해결 처리한다. 새 head CI 전체 성공을 확인했다.
