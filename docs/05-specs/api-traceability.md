# 맛잇온 API 추적성

## 1. 문서 목적

1차 MVP의 PRD, 기능 요구사항, 비즈니스 규칙, NFR, Workstream과 담당자를 외부 API 계약에 연결한다. API로 노출되는 모든 MVP 기능 요구사항은 하나의 주 API를 가진다.

## 2. PRD → API 매핑

| PRD ID | 기능 PRD | 주 API 문서 | 관련 API ID | Workstream | 담당자 |
|---|---|---|---|---|---|
| PRD-DISCOVERY-001 | 맛집 탐색 | `api/discovery/restaurant-discovery-api.md` | API-DISCOVERY-001 | WS-01 | 양성훈 |
| PRD-DISCOVERY-002 | 유튜버 기반 탐색 | `api/discovery/creator-discovery-api.md` | API-CREATOR-DISCOVERY-001, API-DISCOVERY-001 | WS-03 | 이우람 |
| PRD-DETAIL-001 | 맛집 상세 및 콘텐츠 조회 | `api/detail/restaurant-detail-api.md` | API-DETAIL-001 | WS-02 | 박진영 |
| PRD-ADMIN-001 | 관리자 데이터 등록 | `api/admin/authentication-api.md`, `api/admin/reference-data-api.md`, `api/admin/visit-registration-api.md` | API-ADMIN-AUTH-001~003, 기본 데이터 미리보기·생성 API, API-ADMIN-VISIT-001 | WS-04 | 김인안 |

PRD-PRODUCT-001은 전체 제품 범위를 제공하며 하나의 주 API에만 매핑하지 않는다.

## 3. 기능 요구사항 → API 매핑

| 요구사항 ID | 기능 | 주 API | 보조 API | 검증 방식 | 담당자 |
|---|---|---|---|---|---|
| FR-RESTAURANT-001 | 맛집 목록 조회 | API-DISCOVERY-001 | 없음 | 공개·영상 없음·빈 목록 계약 테스트 | 양성훈 |
| FR-RESTAURANT-002 | 맛집 이름 검색 | API-DISCOVERY-001 | 없음 | 부분 일치·공백·영문 대소문자 테스트 | 양성훈 |
| FR-RESTAURANT-003 | 지역별 필터 | API-DISCOVERY-001 | 없음 | 서울 자치구 허용·거부 테스트 | 양성훈 |
| FR-RESTAURANT-004 | 음식 카테고리 필터 | API-DISCOVERY-001 | 없음 | 10개 허용값·복수 거부 테스트 | 양성훈 |
| FR-CREATOR-001 | 유튜버 기준 방문 맛집 | API-DISCOVERY-001 | API-CREATOR-DISCOVERY-001 | 유효 관계·공개 상태·고유 결과 통합 테스트 | 이우람 |
| FR-CREATOR-003 | 유튜버 필터 선택 목록 | API-CREATOR-DISCOVERY-001 | 없음 | 최소 필드·채널명 정렬·빈 목록 테스트 | 이우람 |
| FR-RESTAURANT-005 | 검색·필터 조합 | API-DISCOVERY-001 | 없음 | 모든 허용 조건 AND 조합 테스트 | 양성훈 |
| FR-RESTAURANT-006 | 페이지 단위 조회 | API-DISCOVERY-001 | 없음 | 허용 크기·범위 밖·메타데이터 테스트 | 양성훈 |
| FR-RESTAURANT-007 | 기본 정렬 | API-DISCOVERY-001 | 없음 | 이름·주소 순서와 페이지 안정성 테스트 | 양성훈 |
| FR-RESTAURANT-008 | 맛집 기본 정보 | API-DETAIL-001 | 없음 | 공개·404·외부 링크 장애 테스트 | 박진영 |
| FR-RESTAURANT-009 | 지역 정보 | API-DETAIL-001 | 없음 | 전체 도로명주소·선택 상세 위치 테스트 | 박진영 |
| FR-RESTAURANT-010 | 음식 카테고리 확인 | API-DETAIL-001 | 없음 | 대표 카테고리 정확히 1개 테스트 | 박진영 |
| FR-RESTAURANT-011 | 영상 없는 맛집 상세 | API-DETAIL-001 | 없음 | 기본 정보와 빈 콘텐츠 목록 테스트 | 박진영 |
| FR-CREATOR-002 | 방문 유튜버 정보 | API-DETAIL-001 | API-DISCOVERY-001 | 중복 제거·공개 관계·부분 실패 테스트 | 박진영 |
| FR-VIDEO-001 | 관련 영상 정보 | API-DETAIL-001 | 없음 | 필드·중복·외부 링크 격리 테스트 | 박진영 |
| FR-ADMIN-001 | 관리자 등록 접근 | API-ADMIN-AUTH-001 | API-ADMIN-AUTH-002·003, 나머지 모든 `/admin` API | 로그인·JWT 검증·Refresh 회전·권한 테스트 | 김인안 |
| FR-ADMIN-002 | 맛집 등록 | API-ADMIN-RESTAURANT-001 | API-ADMIN-RESTAURANT-PREVIEW-001, API-DISCOVERY-001, API-DETAIL-001 | 외부 확인·관리자 확정·중복·서울·조회 반영 테스트 | 김인안 |
| FR-ADMIN-003 | 유튜버 등록 | API-ADMIN-CREATOR-001 | API-ADMIN-CREATOR-PREVIEW-001, API-CREATOR-DISCOVERY-001 | 외부 확인·관리자 확정·동일 채널·조회 반영 테스트 | 김인안 |
| FR-ADMIN-004 | 영상 등록 | API-ADMIN-VIDEO-001 | API-ADMIN-VIDEO-PREVIEW-001, API-DETAIL-001 | 외부 확인·관리자 확정·동일 영상·원본 미저장 테스트 | 김인안 |
| FR-VISIT-001 | 방문 관계 등록 | API-ADMIN-VISIT-001 | API-DISCOVERY-001, API-DETAIL-001 | 참조·채널 일치·근거·중복·원자성 통합 테스트 | 김인안 |

API 직접 노출 없음: BR-ADMIN-006의 정정 구현, 공개 상태 저장 구조, 동시성 보장 방식과 외부 동일성 식별값 저장 방식은 외부 API 기능이 아니라 후속 내부 설계다. 다만 그 결과는 공개 제외·중복 오류 계약으로 관찰된다.

## 4. 비즈니스 규칙 → API 매핑

| 규칙 ID | 규칙 | 적용 API | 요청 검증 | 응답 영향 | 담당자 |
|---|---|---|---|---|---|
| BR-RESTAURANT-002·008 | 영상 독립성과 공개 조건 | API-DISCOVERY-001, API-DETAIL-001 | 없음 | 영상 없어도 노출, 비공개 제외 | 양성훈·박진영 |
| BR-RESTAURANT-003~007 | 최소 정보·카테고리·지역·중복·지점 | API-ADMIN-RESTAURANT-PREVIEW-001, API-ADMIN-RESTAURANT-001 | 필수값, 서울 주소, 단일 카테고리, 카카오 동일성 | 미리보기 판정, 201 또는 400·409 | 김인안 |
| BR-CREATOR-001~005 | 채널 관리 단위·최소 정보·중복·표시·일치 | API-ADMIN-CREATOR-001, API-CREATOR-DISCOVERY-001, API-DISCOVERY-001, API-DETAIL-001, API-ADMIN-VISIT-001 | 채널 URL·동일성·게시 채널 일치 | 현재 채널명, 중복 제거, 409·422 | 이우람·김인안·박진영 |
| BR-CREATOR-007 | 이용 불가 채널 제외 | API-CREATOR-DISCOVERY-001, API-DISCOVERY-001, API-DETAIL-001 | 없음 | 유튜버·관계 제외, 맛집 기본 유지 | 이우람·박진영 |
| BR-VIDEO-001~006 | 영상 최소 정보·동일성·관계·실제 방문·날짜 구분 | API-ADMIN-VIDEO-001, API-ADMIN-VISIT-001, API-DETAIL-001 | 원본 URL, 중복, 실제 방문, 게시 채널 | 영상 필드, 409·422, 방문일 미노출 | 김인안·박진영 |
| BR-VIDEO-007~009 | 링크 장애·표시 변경·이용 불가 | API-DETAIL-001 | 없음 | 기본 상세 유지, 무효 영상 제외 | 박진영 |
| BR-VISIT-001~007 | 세 대상 관계·근거·중복·유효성·날짜·검증 | API-ADMIN-VISIT-001, API-DISCOVERY-001, API-DETAIL-001 | 세 참조·조합·근거·채널 일치 | 201·404·409·422, 공개 관계만 조회 | 김인안·이우람·박진영 |
| BR-SEARCH-001~009 | 검색·필터·고유성·빈 결과·페이지·정렬 | API-DISCOVERY-001 | 쿼리 허용값과 단일 값 | AND 결과, 빈 목록, 안정 페이지 | 양성훈·이우람 |
| BR-ADMIN-001~005·007·008 | 권한·검증·정합성·반영·MVP 경계·동시성·보류 | 인증 및 모든 관리자 등록 API | JWT·ADMIN 권한, 필수값, 미리보기, 확인 토큰, 중복 | 401·403·409 및 공개 조회 반영 | 김인안 |
| BR-ADMIN-006 | 잘못된 데이터 정정 | API 직접 노출 없음 | 수정·삭제 API 없음 | 비공개된 대상은 조회 제외 | 김인안 |
| BR-PUBLICATION-001~008 | 공개·비공개·삭제와 일관성 | 모든 공개 조회, 모든 등록 결과 | 관리자 등록 공개 정책 | 비공개·삭제 제외, 기본 맛집 유지 | 전체 Workstream |

## 5. NFR → API 검증 매핑

| NFR ID | 품질 요구사항 | 적용 API | 검증 방법 | 검증 책임 |
|---|---|---|---|---|
| NFR-PERFORMANCE-001·002·004 | 조회·조합 성능과 페이지 제한 | API-DISCOVERY-001, API-DETAIL-001 | 대표 데이터 부하, 경계값·응답 크기 검사 | WS-01·WS-02·WS-03 |
| NFR-PERFORMANCE-003 | 관리자 등록 응답 | 모든 관리자 API | 외부 시간 분리 부하 테스트 | WS-04 |
| NFR-SECURITY-001 | 공개 조회·관리자 통제 | 모든 API, API-ADMIN-AUTH-001~003 | JWT 없음·만료·서명 오류·Refresh 재사용·권한 없음·정상 테스트 | WS-04 및 공통 인증 담당 |
| NFR-SECURITY-002·003 | 입력·비밀·오류 보호 | 모든 API | 악성 입력, 비밀·스택 노출 검사 | 각 담당자 |
| NFR-INTEGRITY-001~004 | 참조·중복·원자성·외부 링크 분리 | 관리자 API, API-DISCOVERY-001, API-DETAIL-001 | 통합·동시성·실패 주입 테스트 | WS-04, WS-02 |
| NFR-RELIABILITY-001·003 | 공통 오류와 부분 실패 격리 | 모든 API, 특히 API-DETAIL-001 | 오류 계약·제공자 장애 테스트 | 각 담당자, WS-02 |
| NFR-EXTERNAL-001~003 | 원본·외부 호출·링크 검증 분리 | API-DETAIL-001, 기본 데이터 등록 API | 외부 장애 모의·저장 자료·URL 검사 | WS-02·WS-04 |
| NFR-OBSERVABILITY-001~003 | 요청 추적·분류·민감정보 차단 | 모든 API | 로그 상관관계·표본 검사 | 공통 운영 담당·WS-04 |
| NFR-COMPATIBILITY-002·003 | UTF-8·일관 형식·모바일 크기 | 공개 조회 API | 계약·한글 왕복·최대 응답 검사 | WS-01·WS-02 |
| NFR-TEST-001~003 | 자동화·변경·품질 게이트 | 모든 API | 요구사항 추적 계약·통합 테스트 | 전체 Workstream |
| NFR-MAINTAINABILITY-001·002 | 책임·공통 정책 경계 | 탐색 API와 공통 계약 | 의존성·계약 중복 검사 | WS-01·WS-03 |
| NFR-PRIVACY-001·002 | 개인정보 최소화·비밀 보호 | 관리자 API | 필드·로그·설정 검사 | WS-04 |

## 6. Workstream → API 매핑

| Workstream | 소유 API | 협업 경계 |
|---|---|---|
| WS-01 | API-DISCOVERY-001 | WS-03의 유튜버 유효 맛집 판정을 최종 목록과 조합 |
| WS-02 | API-DETAIL-001 | WS-03과 관계 유효성 정책 공유, WS-04 등록 결과 소비 |
| WS-03 | API-CREATOR-DISCOVERY-001, API-DISCOVERY-001의 `creatorId` 의미 | WS-01이 정렬·페이지·다른 조건 조합 |
| WS-04 | API-ADMIN-AUTH-001~003, 기본 데이터 미리보기·생성 API, API-ADMIN-VISIT-001 | 등록 결과를 WS-01~03이 인수 검증 |

## 7. 담당자 → API 매핑

| 담당자 | 최종 책임 API | 기본 리뷰 관계 |
|---|---|---|
| 양성훈 | API-DISCOVERY-001 | 이우람 리뷰 |
| 박진영 | API-DETAIL-001 | 김인안 리뷰 |
| 이우람 | API-CREATOR-DISCOVERY-001과 `creatorId` 판정 계약 | 양성훈 리뷰 |
| 김인안 | 관리자 인증·검증 미리보기·생성·방문 관계 API | 인증은 이우람, 등록은 박진영 리뷰 |

## 8. 미매핑 항목

- 기능 요구사항 20개는 모두 주 API에 매핑됐다.
- MVP 제외 기능은 API에 매핑하지 않았다.
- PRD-PRODUCT-001은 전체 범위 문서라 개별 API 주 매핑이 없다.
- Critical 차단 항목이었던 식별자 타입, 인증 전달, 방문 관계 경로와 외부 확인 흐름은 확정돼 매핑에 반영됐다.

## 9. 변경 영향 추적

요구사항·규칙 ID가 추가·삭제·변경되면 이 문서의 주 API, 보조 API, 검증 방식과 담당자를 함께 갱신한다. API 필드·경로·상태 코드 변경은 해당 PRD와 프론트엔드, 소비·제공 Workstream, 계약 테스트와 후속 데이터 모델 영향을 검토한다.
