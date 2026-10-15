---
related_documents:
  - ../01-requirements/business-rules.md
  - ../04-product/prd/admin/ai-video-information-extraction.md
  - ../07-adr/integration/ai-001-video-extraction-candidate-boundary.md
  - ../troubleshooting/pr-170-ai-video-extraction-review.md
---

# #418 방문 근거 문장부호 정규화

## 문제와 원인

현재 VisitEvidenceConfirmation은 `제가 맛집을 방문했습니다`와 같은 명시적 완료형도 끝의 `!` 때문에 차단한다. 이 고정 문장으로 형식 차이에 따른 false negative를 재현한다. 실제 운영의 방문 근거 차단 원문은 확보되지 않았으므로 운영 차단율이나 개선율을 주장하지 않는다.

## 범위와 설계

완료형 문장의 끝에 붙은 ASCII/전각 느낌표와 기존 마침표만 공통 정규화한다. 문장 중간 느낌표·모든 물음표는 계속 차단한다. 새로운 방문 동사·주어·식당명 추론은 추가하지 않으며 TIMESTAMP·현재 채널 제작자의 직접 방문·대상 식당 일치 검증을 그대로 적용한다. 보완 텍스트만으로 방문을 확정하지 않는다.

Prompt·Schema 변경은 #417에서 소유한다. 여기서는 Provider 문장을 바꾸지 않고 동일 의미의 끝 문장부호만 정규화하므로 P/S 버전이나 판정 계약을 변경하지 않는다. #416·#417과 독립이며 공유 등록 실행 파일을 수정하지 않는다.

## 검증 계획

고정 후보로 완료형과 끝 문장부호 변형을 허용하고 부정·의문·예정·추천·타인 방문·다른 식당·TEXT_RANGE·UNKNOWN·중간 느낌표·빈 값·잘못된 시간 범위를 계속 거부하는지 검증한다. 기존 Worker/관리자 공유 검증 회귀, Java 컴파일, ArchUnit을 실행한다. 실제 Kakao·YouTube·Gemini 호출과 운영 재처리는 없다.

## 검증 결과

독립 리뷰에서 P1/P2 지적은 없었다. 리뷰자가 문장부호 회귀·ArchUnit과 diff 검사를 다시 실행해 통과했다.

`.\gradlew.bat test --tests '*VisitEvidenceConfirmationTest' --tests '*VerifyAiContentCandidateServiceTest' --tests '*RegistrationUnitExecutionServiceTest' --no-daemon --console=plain`은 Java 컴파일과 총 87건(신규 문장부호 18건·기존 콘텐츠 검증 51건·등록 실행 8건·ArchUnit 10건) 모두 통과했다. 최초 신규 fixture의 TEXT_RANGE 오프셋을 int로 작성해 컴파일이 실패했고 계약 타입 Long에 맞춰 수정한 뒤 위 전체 대상 검증을 통과했다. `git diff --check` 통과. 운영 차단 샘플·실제 개선율은 미확인이며 독립 리뷰 결과는 완료 후 반영한다.
