---
related_documents:
  - ../00-overview/README.md
  - ../08-planning/deployment-hardening-cutover-record.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# 트러블슈팅 기록

구현과 리뷰 과정에서 재현·검증한 문제의 현상, 원인, 처리 결과를 보존한다.

## 기록 분류와 파일명 규약

문제를 발견한 경계에 따라 기록을 구분한다. PR 리뷰에서 발견·해결한 문제는 `pr-<번호>-<짧은-이름>.md`를 사용하고, PR 리뷰가 아닌 실제 운영 작업 중 발견·해결한 문제는 `ops-<YYYY-MM-DD>-<짧은-이름>.md`를 사용한다. 운영 전환 기록도 리뷰 기반 기록과 같은 트러블슈팅 구조로 작성하되, `운영 전환` 목록에서 별도로 찾을 수 있게 한다. 이 분류와 `ops-<날짜>-` 확장은 PR #256 리뷰에서 운영 사건과 리뷰 사건의 추적 단위를 분리하기 위한 팀 문서 규칙으로 정리했다.

## 최신 기록

- [PR #289 Redis local fallback의 SSM port 오염](pr-289-redis-recovery-observability-review.md)
- [Issue #284 Redis 복구 모드의 ALB 보호 선행 조건](pr-284-redis-recovery-observability-review.md)
- [이슈 #282 dependency health 진단과 롤백 안전성](pr-282-dependency-health-diagnostics-review.md)
- [PR #280 페이지네이션 예외 정책 문서 정합성](pr-280-pagination-policy-documentation-review.md)
- [운영 작업: AI 보충 입력 검증 실패 사유가 화면에서 사라졌다](ops-2026-08-22-ai-supplement-validation-reason.md)
- [PR #279 장소명 정규화·감사 값 호환·운영 기본값 리뷰](pr-279-ai-place-identity-review.md)
- [PR #278 Kakao 장소명 완화 판정의 감사와 활성화 게이트](pr-278-kakao-place-name-matching-review.md)
- [PR #275 프론트 Dockerfile EOF 빈 줄](pr-275-frontend-public-assets-review.md)
- [PR #273 화면 동기화 후 이메일 재발송·상태 배지·원본 자산 정리](pr-273-frontend-ui-sync-review.md)
- [PR #272 공유 테스트 컨테이너의 Redis·관리자 계정 격리](pr-272-test-context-consolidation-review.md)
- [PR #269 회원 메일 발신 주소 주입 경로와 렌더러 중복](pr-269-member-mail-from-address-review.md)
- [운영 작업: 격리 성능 환경 첫 apply에서 부트스트랩이 전혀 실행되지 않았다](ops-2026-08-20-perf-env-bootstrap-failure.md)
- [PR #261 의존 인스턴스 분리가 만든 Redis 연결 거부와 무인증 노출](pr-261-performance-deps-separation-review.md)
- [PR #259 등록 단위 일괄 폐기의 상태·감사 이력 불일치](pr-259-registration-unit-discard-all-review.md)
- [PR #257 전환 후 런타임 기준선 리뷰 반영](pr-257-runtime-baseline-review.md)
- [운영 전환: ALB·Blue-Green 트래픽 전환에서 드러난 결함 3건](ops-2026-08-19-alb-cutover-review.md)
- [PR #253 CodeDeploy 단일 경로 전환과 배포 계약 테스트 CI 회귀](pr-253-codedeploy-only-contract-test-review.md)
- [PR #251 오류 응답 details의 배열 정규화 누락](pr-251-details-array-normalization-review.md)
- [PR #244 등록 단위 실행의 동시성·CONFIRM 원자성과 중복 판정 죽은 코드](pr-244-registration-unit-atomicity-review.md)
- [PR #238 통합 인증 구현과 CI 회귀](pr-238-unified-auth-implementation-review.md)
- [PR #235 통합 인증·라우팅·관리자 계정 전환 계약 리뷰 반영](pr-235-unified-auth-contract-review.md)
- [PR #236 코스 지도 구현의 형상 Schema·만료 선택·좌표 검증 결함](pr-236-course-route-map-implementation-review.md)
- [PR #232 코스 경로 형상·실패 계약 정합화](pr-232-course-route-map-contract-review.md)
- [PR #227 YouTube 채널 감시 상태·관리 화면 후속 반영](pr-227-youtube-channel-watch-review.md)

## 운영 전환

- [AI 보충 입력 검증 실패 사유가 화면에서 사라졌다](ops-2026-08-22-ai-supplement-validation-reason.md)

리뷰가 아니라 실제 운영 작업 중에 드러난 문제의 기록이다. 파일명은 `ops-<날짜>-` 규약을 쓴다.

- [운영 작업: 격리 성능 환경 첫 apply에서 부트스트랩이 전혀 실행되지 않았다](ops-2026-08-20-perf-env-bootstrap-failure.md)
- [ALB·Blue-Green 트래픽 전환에서 드러난 결함 3건](ops-2026-08-19-alb-cutover-review.md)

## PR 리뷰

- [PR #289 Redis local fallback의 SSM port 오염](pr-289-redis-recovery-observability-review.md)
- [PR #280 페이지네이션 예외 정책 문서 정합성](pr-280-pagination-policy-documentation-review.md)
- [PR #279 장소명 정규화·감사 값 호환·운영 기본값 리뷰](pr-279-ai-place-identity-review.md)
- [PR #278 Kakao 장소명 완화 판정의 감사와 활성화 게이트](pr-278-kakao-place-name-matching-review.md)
- [PR #275 프론트 Dockerfile EOF 빈 줄](pr-275-frontend-public-assets-review.md)
- [PR #273 화면 동기화 후 이메일 재발송·상태 배지·원본 자산 정리](pr-273-frontend-ui-sync-review.md)
- [PR #272 공유 테스트 컨테이너의 Redis·관리자 계정 격리](pr-272-test-context-consolidation-review.md)
- [PR #269 회원 메일 발신 주소 주입 경로와 렌더러 중복](pr-269-member-mail-from-address-review.md)
- [PR #261 의존 인스턴스 분리가 만든 Redis 연결 거부와 무인증 노출](pr-261-performance-deps-separation-review.md)
- [PR #259 등록 단위 일괄 폐기의 상태·감사 이력 불일치](pr-259-registration-unit-discard-all-review.md)
- [PR #257 전환 후 런타임 기준선 리뷰 반영](pr-257-runtime-baseline-review.md)
- [PR #251 오류 응답 details의 배열 정규화 누락](pr-251-details-array-normalization-review.md)
- [PR #244 등록 단위 실행의 동시성·CONFIRM 원자성과 중복 판정 죽은 코드](pr-244-registration-unit-atomicity-review.md)
- [PR #238 통합 인증 구현과 CI 회귀](pr-238-unified-auth-implementation-review.md)
- [PR #228 ASG replacement 배포의 상태 보존·중단 제어·권한·네트워크 계약](pr-228-asg-replacement-deployment-review.md)
- [PR #235 통합 인증·라우팅·관리자 계정 전환 계약 리뷰 반영](pr-235-unified-auth-contract-review.md)
- [PR #236 코스 지도 구현의 형상 Schema·만료 선택·좌표 검증 결함](pr-236-course-route-map-implementation-review.md)
- [PR #232 코스 경로 형상·실패 계약 정합화](pr-232-course-route-map-contract-review.md)
- [PR #226 AI 자동 등록 계약의 미완결 경로와 합의 상태 표기](pr-226-ai-auto-registration-contract-review.md)
- [PR #221 Redis 사설 경로 비용과 배포 게이트 서술 정합화](pr-221-deployment-hardening-cost-review.md)

- [PR #218 격리 성능 환경·부하 결과 정합성](pr-218-isolated-performance-review.md)
- [PR #220 Prompt 버전 상향의 문서 전파 누락 재발과 검사 자동화](pr-220-ai-prompt-version-propagation-review.md)
- [PR #217 사용자·관리자 프론트 공통 템플릿 리뷰 반영](pr-217-ui-template-review.md)
- [PR #214 인기 맛집 쿼리 측정과 스케줄러 격리](pr-214-popular-restaurant-query-count-review.md)
- [PR #213 자연어 검색 URL 필터 변경 초기화와 빈 결과 조건 제거 계약](pr-213-natural-language-filter-reset-review.md)
- [PR #212 지도 429 query별 대기·타이머 상한 리뷰 반영](pr-212-map-rate-limit-review.md)
- [PR #210 운영 애플리케이션 포트 loopback 바인딩과 보강 ADR 분리](pr-210-application-port-binding-review.md)
 - [PR #211 Refresh·Logout Origin 방어](pr-211-admin-refresh-logout-origin-review.md)
- [PR #209 AI 후보 등록 입력·비동기·외부 연동 경계](pr-209-ai-candidate-registration-review.md)
- [PR #206 Nginx 공개 API smoke·Accepted ADR 정합화](pr-206-nginx-public-api-gate-review.md)
- [PR #208 운영 fixture cleanup 참조 보호와 성능 추적성](pr-208-operational-performance-review.md)
- [PR #205 관리자 로그인 trusted proxy 출처 해석 리뷰 반영](pr-205-admin-login-trusted-proxy-review.md)
- [PR #204 Prompt P2 계약 동기화와 후보 결과 불변성](pr-204-ai-prompt-contract-review.md)
- [PR #192 V8 통합 범위와 최종 Gemini 모델 계약](pr-192-flyway-model-contract-review.md)
- [PR #191 Gemini 모델 전환 리뷰 반영](pr-191-gemini-model-transition-review.md)
- [PR #184 YouTube 채널 감시 상태·동시성 경계 리뷰 반영](pr-184-youtube-channel-watch-review.md)
- [PR #182 관리자 AI 영상 접수 리뷰 반영](pr-182-admin-ai-video-intake-review.md)
- [PR #179 브라우저 인수 캡처 증거 보존](pr-179-browser-capture-evidence-review.md)
- [PR #178 3차 확장 통합 회귀 테스트 리뷰 반영](pr-178-third-expansion-integration-review.md)

- [PR #177 AI 평가 자산의 증거 범위·분할·Critical 경계](pr-177-ai-evaluation-review.md)
- [PR #175 관리자 AI 검수 동시성·태그 감사 후속](pr-175-ai-admin-review-follow-up.md)

- [PR #173 AI 후보 자동 등록 리뷰 반영](pr-173-ai-candidate-auto-registration-review.md)
- [PR #174 코스 공개 진입점·실패 식별·검색 상태 리뷰 반영](pr-174-course-public-screen-review.md)
- [PR #172 AI Worker 운영·복구 경계](pr-172-ai-worker-key-rotation-review.md)
- [PR #171 코스 경로 외부 연동·quota 경계 리뷰 반영](pr-171-course-route-review.md)
- [PR #170 AI 영상 추출 Provider·Webhook 리뷰와 CI 실패 반영](pr-170-ai-video-extraction-review.md)
- [PR #169 자연어 검색 입력·조건·요청 출처 경계](pr-169-natural-language-search-review.md)
- [PR #168 AI V4 인덱스 검증 회귀와 테스트 형식](pr-168-ai-schema-verification-review.md)
- [PR #146 제보·신고 접수 버튼 type 수정 PR 본문 정정](pr-146-participation-submit-button-type-review.md)
- [PR #142 공개 큐레이션 조회 계약과 화면 상태 보완](pr-142-public-curation-review.md)
- [PR #140 제보·신고 알림 연결 및 원자성 롤백 테스트 보완](pr-140-participation-notification-review.md)
- [PR #141 관리자 큐레이션 입력·조회 경계와 계약 불일치](pr-141-admin-curation-review.md)
- [PR #135 개인 컬렉션 완료 조건과 저장 오류](pr-135-personal-collection-review.md)
- [PR #139 인기 맛집 공개 조회의 회원 인증·세션 경계 오분류](pr-139-popular-restaurant-security-boundary.md)
- [PR #134 사용자 제보·신고 접수 리뷰 반영](pr-134-participation-request-review.md)
- [PR #131 2차 확장 식별 제거와 착수 게이트 리뷰](pr-131-expansion-foundation-review.md)
- [PR #129 Basic Auth 전환 안전장치와 이메일 인증 rate limit 우회](pr-129-deploy-cutover-and-rate-limit-review.md)
- [PR #126 E2-T01 완료 참조 리뷰 반영](pr-126-e2-t01-completion-reference-review.md)
- [PR #128 트러블슈팅 기록의 권위 등급과 기록 생성 단위](pr-128-skill-troubleshooting-authority-review.md)
- [PR #127 트러블슈팅 기록의 related_documents 누락 보완](pr-127-troubleshooting-record-related-docs-review.md)
- [PR #126 E2-T01 완료 참조 리뷰 반영](pr-126-e2-t01-completion-reference-review.md)
- [PR #125 develop→main 승격 PR의 역동기화 계획 정정](pr-125-develop-to-main-sync-policy-review.md)
- [PR #124 가입 이메일 인증 코드 리뷰 반영](pr-124-email-verification-code-review.md)
- [PR #123 검증 참여자 세션 리뷰 반영](pr-123-verification-session-review.md)
- [PR #122 지도 뷰포트 비종속 조회 문서·테스트 반영](pr-122-map-viewport-independent-query-review.md)
- [PR #100 이메일 인증 후속 흐름 리뷰 반영](pr-100-email-verification-review.md)
- [PR #99 반복 지도 필터 리뷰 판단](pr-99-repeated-map-filter.md)
