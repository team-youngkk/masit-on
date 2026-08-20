---
related_documents:
  - ../08-planning/post-cutover-runtime-baseline.md
  - ../08-planning/deployment-hardening-impact-review.md
  - ../08-planning/README.md
  - ../../.codex/skills/troubleshoot-pr-review/SKILL.md
---

# PR #257 리뷰 트러블슈팅: 전환 후 런타임 기준선 정합화

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | https://github.com/team-youngkk/masit-on/pull/257 |
| 작성자 | `w00lam` |
| 처리 일자 | 2026-08-20 |
| 범위 | 미해결 인라인 리뷰 스레드 4건 |
| 주 문제 유형 | 인프라·기타(문서 링크 정확성) |
| 기존 기록 | `docs/troubleshooting/`에서 런타임 기준선·배포 비용·문서 링크 관련 기록을 검색하고 PR #221·#218 기록을 확인했다. 동일 증상에 대한 기존 해결 기록은 없어 새 기록으로 남긴다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 |
|---|---|---|---|---|
| [CPU 크레딧](https://github.com/team-youngkk/masit-on/pull/257#discussion_r3817675430) | t4g.small의 적립률이 medium의 절반이라는 결론을 정정하고 두 타입의 회복량을 대조 | 인프라 | 수정 필요 | 두 타입 모두 2 vCPU·시간당 24 크레딧으로 정정하고 관측된 15분당 약 5 크레딧을 이론값과 대조 |
| [related_documents 링크 1](https://github.com/team-youngkk/masit-on/pull/257#discussion_r3817675444) | 존재하지 않는 운영 트러블슈팅 문서 참조를 제거하거나 교체 | 기타(문서 링크 정확성) | 수정 필요 | PR 리뷰 기록을 새로 작성하고 `pr-257-runtime-baseline-review.md`로 교체 |
| [related_documents 링크 2](https://github.com/team-youngkk/masit-on/pull/257#discussion_r3817793971) | 같은 죽은 링크를 제거하거나 실제 문서로 교체 | 기타(문서 링크 정확성) | 수정 필요 | 위와 동일한 새 리뷰 기록으로 교체 |
| [메모리 결론](https://github.com/team-youngkk/masit-on/pull/257#discussion_r3817793974) | 무효화한 메모리 표에서 파생한 "여유 약 300 MB" 결론도 정정 | 인프라 | 수정 필요 | 표와 파생 결론을 함께 폐기하고 무부하 실측 기준선과 부하 측정 필요성을 명시 |

## 3. 문제 현상과 발생 조건

- CPU 크레딧 문서가 t4g.small은 시간당 12, t4g.medium은 24라고 적어 두 인스턴스 크기의 적립률에 차이가 있는 것처럼 서술했다.
- `docs/08-planning/post-cutover-runtime-baseline.md`의 `related_documents`가 PR 브랜치에 존재하지 않는 `../troubleshooting/ops-2026-08-19-alb-cutover-review.md`를 가리켰다. 운영 전환 기록은 다른 브랜치에 있었지만 PR #257의 현재 base/head 형상에는 포함되지 않았다.
- `deployment-hardening-impact-review.md`는 잘못된 JVM 추정 표를 무효화하면서도 그 표에서 파생한 t4g.small의 "여유 약 300 MB" 결론을 남겼다.
- 기대 결과는 AWS 인스턴스 사양과 실측값이 같은 결론을 가리키고, 현재 PR 형상에서 모든 `related_documents` 링크가 실제 파일로 해석되며, 폐기한 계산에서 파생한 결론이 남지 않는 것이다.

## 4. 근본 원인

CPU 크레딧은 인스턴스 메모리 크기에 비례한다고 잘못 추정해 t4g.small과 t4g.medium을 혼동했다. AWS 공식 표는 두 타입 모두 2 vCPU와 시간당 24 크레딧을 제시한다.

문서 링크는 다른 작업 브랜치에 있는 운영 기록을 현재 PR의 근거 문서처럼 참조한 것이 원인이다. 또한 정정 주석을 표에만 적용하고 표 아래의 파생 결론까지 같은 패스로 갱신하지 않아 무효한 추정이 남았다.

## 5. 확인 및 시도

| 확인 항목 | 결과 | 판단 |
|---|---|---|
| AWS EC2 버스터블 인스턴스 공식 CPU 크레딧 표 | t4g.small·t4g.medium 모두 시간당 24 크레딧, 2 vCPU | CPU 결론 수정 |
| 기준선 표의 `CPUCreditBalance` | 15분 간격 증가량이 약 4.65~5.34로 관측됨 | 15분당 최대 6 크레딧인 24크레딧/시간 가설과 같은 방향 |
| PR head에서 운영 트러블슈팅 경로 확인 | 기존 `ops-2026-08-19-alb-cutover-review.md`는 존재하지 않음 | 현재 PR에 새 리뷰 기록을 추가하고 링크 교체 |
| 실측 기준선 대조 | 호스트 available 787 MB, backend 실사용 398.4 MiB | 기존 "여유 약 300 MB" 추정을 폐기하고 무부하 기준선으로 대체 |

## 6. 최종 해결

- `post-cutover-runtime-baseline.md`의 CPU 크레딧 설명을 두 타입 모두 시간당 24 크레딧으로 정정하고 AWS 공식 문서를 연결했다.
- `deployment-hardening-impact-review.md`의 CPU 크레딧 후속 문장을 정정했다.
- 잘못된 JVM 표와 연결된 "여유 약 300 MB" 결론을 폐기하고 실제 무부하 기준선과 별도 부하 측정 필요성을 명시했다.
- `related_documents`를 현재 PR에서 함께 추가한 `pr-257-runtime-baseline-review.md`로 교체하고 `docs/troubleshooting/README.md`에 등록했다.

선택 이유는 계약 문서의 링크가 현재 병합 가능한 형상에서 추적되어야 하고, 무부하 실측을 최대 부하 용량 판정으로 과장하지 않으면서 기존 계산의 영향 범위를 끝까지 정정해야 하기 때문이다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 변경 문서의 whitespace 오류 없음 |
| 관련 문서 경로 해석 검사 | 통과 | `post-cutover-runtime-baseline.md`와 새 트러블슈팅 기록의 내부 링크 대상 존재 |
| 잘못된 CPU·메모리 문구 검색 | 통과 | `medium(24)의 절반`, `적립률은 medium의 절반`, `여유 약 300 MB`의 현재 결론 문구가 제거됨 |

## 8. 재발 방지 및 다음 확인

- 리뷰 반영 시 정정한 표뿐 아니라 그 표를 인용하거나 요약하는 바로 아래 결론까지 함께 검색한다.
- `related_documents`는 작업 브랜치가 아닌 현재 PR의 base/head 형상에서 상대 경로를 해석해 확인한다.
- 최대 부하 시 메모리 여유와 CPU 크레딧 소진 여부는 기준선 문서의 후속 부하 측정에서 확인한다. 현재 PR에서는 무부하 관측만 다룬다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 도입 후 값 | 비교 결과 |
|---|---|---|---|---|
| 문서 내 사실·링크 정합성 | CPU 크레딧 결론 1건, 파생 메모리 결론 1건, 깨진 내부 링크 1건 | PR #257 리뷰 시 AWS 공식 표·실측 표·현재 Git 형상 대조 | 오류 문구 제거, 내부 링크 대상 존재 | 정적 문서 검증 범위에서 세 지적 모두 해소 |

## 10. 남은 사항

최대 부하 상태의 메모리 여유와 CPU 크레딧 고갈 임계점은 이 PR의 무부하 기준선만으로 확정할 수 없으며, 계획된 별도 부하 측정에서 확인한다.
