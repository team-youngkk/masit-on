Closes #

<!-- 이 PR이 구현한 이슈 번호를 적는다.
     기본 브랜치는 main이다. GitHub는 PR이 기본 브랜치로 병합될 때만 이슈를 자동으로 닫으므로,
     develop으로 병합하면 이슈는 연결만 되고 닫히지 않는다. 그 시점에 닫아야 하면 수동으로 닫는다.
     대상 브랜치도 main이 기본값이니 develop이 맞는지 확인한다.
     여러 이슈를 연결하려면 "Closes #1, Closes #2"처럼 각각 적는다.
     닫을 이슈가 없으면 이 줄을 지우고 아래 관련 문서에 근거를 남긴다. -->

## 메타데이터

<!-- PR 템플릿은 담당자·리뷰어·레이블을 자동으로 지정하지 못한다.
     PR을 만든 직후 오른쪽 사이드바에서 직접 설정하고, 같은 값을 아래에 적는다. -->

- Workstream·Task:
- 담당자:
- 리뷰어: <!-- ownership.md 9장 기본 리뷰 구조를 따른다. 작성자를 제외한 2명 승인이 필요하다 -->
- 레이블: <!-- mvp / size:S·M / sequence:1-foundation~4-verification / area:backend·frontend·database·infra·security·integration·test -->

## 변경 목적

<!-- 무엇을 왜 바꿨는지 두세 문장. 구현 방법이 아니라 목적을 적는다. -->

## 관련 문서

<!-- 근거가 되는 문서를 링크한다. 해당 없으면 "해당 없음"이라고 적는다. -->

- 요구사항 ID:
- PRD:
- API 계약:
- ADR:
- 테이블·마이그레이션:

## 변경 범위

<!-- Workstream, 도메인, 계층을 적는다. 예: WS-01 / restaurant / application·infrastructure -->

- Workstream:
- 도메인·계층:

## 테스트 결과

<!-- 실행한 명령과 결과를 적는다. 통과 여부를 말로만 쓰지 않는다. -->

```
```

- 추가·수정한 테스트:
- 검증한 정상·예외·경계 시나리오:

## 검증하지 못한 항목

<!-- 없으면 "없음". 있으면 무엇을, 왜 검증하지 못했는지 적는다.
     미검증 항목을 비워두고 완료로 보고하지 않는다. -->

## 리뷰 요청

<!-- API·DB·인증 경계 또는 공유 설정을 바꿨다면 해당 소유자를 반드시 포함한다.
     소유자는 docs/03-team/ownership.md 참고. -->

- 필수 리뷰어:
- 사전 합의 여부:

## 완료 점검

<!-- docs/06-architecture/implementation-conventions.md 9절 -->

- [ ] 관련 요구사항·PRD·API·ADR·테이블을 확인했다.
- [ ] NAVER Java 컨벤션과 프로젝트 우선 규칙을 준수했다.
- [ ] 계층, 도메인, 트랜잭션과 외부 Port/Adapter 경계를 지켰다.
- [ ] API·DB 계약 변경을 사전 합의하고 문서에 반영했다.
- [ ] 정상·예외·경계 조건과 필요한 통합 테스트가 통과했다.
- [ ] 실제 외부 API와 운영 비밀정보를 사용하지 않았다.
- [ ] 최소 두 명의 승인을 받았고 대상 브랜치의 병합 방식을 지켰다.

<!-- 병합 방식: feature·fix 등 작업 브랜치 → develop 은 Squash Merge, develop → main 은 Create a merge commit -->
<!-- 정상적인 develop → main Merge 뒤에는 커밋 수 차이만을 이유로 main → develop 역동기화 PR을 만들지 않는다. main 전용 Hotfix가 있을 때만 역동기화한다. -->

<!-- PR 본문과 커밋 메시지에 AI 도구 생성 표기를 남기지 않는다.
     "Generated with Claude Code" 같은 문구와 도구 서명·배지를 넣지 않는다. -->
