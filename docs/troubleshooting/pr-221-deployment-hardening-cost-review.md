---
related_documents:
  - ../08-planning/deployment-hardening-impact-review.md
  - ../08-planning/m2-cost-and-sizing.md
  - ../08-planning/m2-provisioning-record.md
  - ../08-planning/third-expansion-final-gate-result.md
  - ../../deploy/scripts/redis-render-conf.sh
  - ../../deploy/redis/masiton-redis.service
---

# PR #221 리뷰 트러블슈팅: Redis 사설 경로 비용과 배포 게이트 서술 정합화

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#221 배포 고도화 비용·일정 영향 검토](https://github.com/team-youngkk/masit-on/pull/221) |
| 작성자 | `w00lam` |
| 처리 일자 | 2026-08-18 |
| 범위 | `deployment-hardening-impact-review.md`의 미해결 인라인 리뷰 7건 |
| 주 문제 유형 | 기타 — 비용 산정·작업 목록·성능 게이트 문서의 계약 정합성 |
| 기존 기록 | [PR #218 격리 성능 환경·부하 결과 정합성](pr-218-isolated-performance-review.md)을 확인했으며, 성능 증거와 전제 고정 방식은 참고하고 이번 비용 산정 누락은 별도 사건으로 기록한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [Parameter Store 경로](https://github.com/team-youngkk/masit-on/pull/221#discussion_r3795689144) | Redis 기동 시 `ssm` 인터페이스 엔드포인트와 전용 인스턴스 에이전트 전제를 반영하고 비용을 재산정 | 기타 | 수정 필요 | 5.1절에 Parameter Store 열과 `ssm` 월 `$9.49`를 추가하고 5.2~5.5·8.1 판정과 8.2 권고를 갱신 | `redis-render-conf.sh`의 `aws ssm get-parameter`와 문서 내 합계 대조 |
| [E2 S3 게이트웨이 엔드포인트](https://github.com/team-youngkk/masit-on/pull/221#discussion_r3795689156) | E2 이미지 경로에 S3 게이트웨이 엔드포인트를 명시 | 기타 | 수정 필요 | E2 이미지 열에 S3 게이트웨이 엔드포인트를 추가하고 게이트웨이 비용 `$0` 전제를 유지 | E1·E2 이미지 획득 경로와 5.1절 비용 열 대조 |
| [S1 + E3 환산값](https://github.com/team-youngkk/masit-on/pull/221#discussion_r3795689161) | 1,559원 환산값을 181,300원으로 정정 | 기타 | 수정 필요 | 5.5절을 `$116.31 × 1,559 = 181,327원` 기준 181,300원으로 수정 | 환율 계산 재실행 |
| [게이트 4절 3번 범위](https://github.com/team-youngkk/masit-on/pull/221#discussion_r3795689169) | `50/20`은 재실행이 아니라 기존 Verified 증거 재사용으로 좁혀 서술 | 기타 | 수정 필요 | 7절 둘째 사실을 최대 `200/80` 실행·판정과 정상 `50/20` 증거 재사용으로 구분 | `third-expansion-final-gate-result.md` 4절 3번 대조 |
| [판정 요약 비용 범위](https://github.com/team-youngkk/masit-on/pull/221#discussion_r3796886559) | 1절의 비용 범위를 갱신된 81%~114%로 정정 | 기타 | 수정 필요 | 1절 요약의 하한을 72%에서 81%로 수정 | 5.2절·8.1절과 대조 |
| [9절 E1·S2 조건](https://github.com/team-youngkk/masit-on/pull/221#discussion_r3796886565) | E1 `$0`과 S2 조건절을 현재 비용·판정에 맞게 갱신 | 기타 | 수정 필요 | E1 `$9.49`, EIC 요금 미확인 시 S1 + E1 재확인 조건으로 수정 | 5.1절·5.3절·8.1절·8.2절 대조 |
| [S2 + E1 초과 표시](https://github.com/team-youngkk/masit-on/pull/221#discussion_r3796886569) | 5.4절 비교표의 102% 행에 초과 표시 추가 | 기타 | 수정 필요 | `S2 + E1` 행에 `❌` 추가 | 5.3절·5.4절·8.1절 표 대조 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: 없음.
- 발생 환경: PR #221 `docs/deployment-hardening-impact-review.md`의 사설 Redis 비용·일정 영향 검토.
- 재현 조건: 전용 Redis의 systemd `ExecStartPre`와 `redis-render-conf.sh`를 확인한 뒤 5.1절 외부 경로 표 및 5.2~5.5·8.1절의 합계를 대조한다.
- 실제 결과: Redis 기동 필수인 Parameter Store 접근이 외부 경로와 비용 표에서 빠져 E1·E2와 S2 판정이 낮게 계산됐다. E2 이미지 경로에는 S3 레이어 다운로드 경로가 빠졌고, S1 + E3의 최고 환율 환산값은 178,600원으로 재현되지 않았다. 성능 게이트 문장은 정상 `50/20` 재실행을 요구하는 것처럼 읽혔다. 후속 갱신 뒤에는 1절의 72% 요약, 9절의 E1 `$0`·S2 조건, 5.4절의 `S2 + E1` 초과 표시가 이전 전제를 남겼다.
- 기대 결과: 기동 필수 의존성과 전제별 비용을 표에 포함하고, 모든 환산값과 판정이 같은 환율·시간 기준으로 재현되며, 게이트 원문이 요구하는 실행과 증거 재사용을 구분해야 한다.
- 영향 범위: 배포 고도화 착수 시 Redis 네트워크 작업 목록, 월 예산 판정, Blue-Green 도입 여부와 성능 검증 선행 순서.

## 4. 근본 원인

비용 문서를 작성할 때 현재 앱 EC2의 퍼블릭 경로 전제를 전용 Redis의 사설 서브넷 경로에 그대로 축소 적용했다. 그 과정에서 사람이 관리 접속하는 경로만 세고, `redis-render-conf.sh`가 기동 때마다 호출하는 Parameter Store를 별도 런타임 의존성으로 분리하지 않았다. 또한 ECR 레이어 획득에 필요한 S3 게이트웨이 경로를 E1과 달리 E2 표에 기록하지 않았고, 환율 환산 한 칸과 원문 게이트 범위 서술을 후속 변경 때 함께 대조하지 않았다. 후속 수정에서는 본문 표를 갱신한 뒤 요약·미확인 목록·비교표의 표시를 같은 패스로 재검토하지 않아 이전 값과 조건절이 남았다.

SSM Agent·CloudWatch Agent를 전용 Redis 인스턴스에 유지할지는 이번 문서의 비용 기준선에서 확정하지 않았다. 현재 기준선은 두 에이전트를 설치하지 않고 EC2 Instance Connect Endpoint로 관리 접속하며 `ssm` 엔드포인트만 반영한다. 에이전트를 유지하기로 하면 `ssmmessages`·`ec2messages`·`monitoring` 엔드포인트를 추가해 비용을 다시 계산해야 한다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| `deploy/redis/masiton-redis.service`와 `deploy/scripts/redis-render-conf.sh` 대조 | `ExecStartPre`에서 `aws ssm get-parameter`를 매 기동 실행 | `ssm`을 관리 접속과 별도의 런타임 경로로 비용 표에 추가 |
| `m2-cost-and-sizing.md`의 인터페이스 엔드포인트 단가 대조 | `ssm` 단가는 월 `$9.49`, 기존 3종 표는 `$28.47` | E1·E2에 `$9.49`를 추가하고 에이전트 유지 시 추가 재산정 조건을 문서화 |
| E2의 ECR 레이어 획득 경로 대조 | 매니페스트 API 외 실제 레이어용 S3 게이트웨이 경로가 필요하며 게이트웨이 비용은 `$0` | E2 이미지 열에 S3 게이트웨이 엔드포인트 추가 |
| `S1 + E3` 환산 재계산 | `$116.31 × 1,559 = 181,327원` | 181,300원으로 정정 |
| `third-expansion-final-gate-result.md` 4절 3번 대조 | 최대 `200/80`은 실행·판정, 정상 `50/20`은 기존 Verified 증거 재사용 | 7절 문장을 원문 범위로 수정 |
| 1절·5.2절·8.1절 비용 범위 대조 | 5절의 최신 하한은 81%이고 72%는 이전 전제 | 1절 요약을 81%~114%로 수정 |
| 5.1절·5.3절·8.1절·8.2절과 9절 조건 대조 | E1은 `$9.49`, S2 + E1은 102% 초과 | 9절의 E1·S2 조건을 EIC 요금 재확인 조건으로 수정 |
| 5.3절·5.4절·8.1절 표 대조 | `S2 + E1`만 5.4절에서 초과 표시가 누락 | 5.4절 행에 `❌` 추가 |

## 6. 최종 해결

- 변경 내용: Parameter Store 접근 열과 `ssm` 엔드포인트 비용을 추가하고, E2의 S3 게이트웨이 엔드포인트, 모든 영향 절의 비용·판정, 1절 비용 요약, 5.4절 초과 표시, 9절 E1·S2 조건, 1,559원 환산값, 성능 게이트의 실행·증거 재사용 범위를 갱신했다.
- 선택 이유: 비밀값 조달 방식을 임의로 바꾸지 않고 현재 스크립트의 런타임 의존성을 비용 모델에 반영하기 위해서다.
- 변경 파일: `docs/08-planning/deployment-hardening-impact-review.md`, `docs/troubleshooting/pr-221-deployment-hardening-cost-review.md`, `docs/troubleshooting/README.md`
- 고려한 대안: `redis-render-conf.sh`의 비밀값 조달 방식을 바꾸는 대안은 코드·보안 계약 변경이 필요하므로 이번 문서 리뷰 범위에 포함하지 않았다. SSM Agent·CloudWatch Agent 유지안은 별도 엔드포인트 재산정 조건으로 남겼다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 변경 문서에 whitespace 오류 없음 |
| 문서 내 비용·판정 검색 및 계산 대조 | 통과 | 누락된 기존 비용·환산값을 재검색하고 `ssm` 비용, 1,470원·1,559원 표, 5.2~5.5·8.1 판정을 대조 |
| 관련 문서 링크·스크립트 경로 확인 | 통과 | 트러블슈팅 기록의 상대 링크 대상과 `redis-render-conf.sh`의 `aws ssm get-parameter` 호출 위치 확인 |
| 최신 리뷰 3건의 요약·조건·표시 대조 | 통과 | 1절 81%~114%, 9절 E1 `$9.49` 조건, 5.4절 `S2 + E1` `❌`가 5.2·5.3·8.1절과 일치 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: 사설 배포 경로 비용을 계산할 때 이미지 획득·관리 접속·기동 런타임 의존성을 별도 열로 대조하고, 비용 전제 변경 후 합계·환율 민감도·판정·권고를 한 번에 갱신한다.
- 다음 확인: 착수 ADR 작성 전에 전용 Redis에서 SSM Agent·CloudWatch Agent를 유지할지 확정하고, 유지하면 관련 엔드포인트와 비용을 재산정한다. 담당자는 배포 owner 이우람이며 착수 ADR 작성 시 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| 월 비용 산정의 문서·계약 정합성 | `ssm` 런타임 의존성, E2 S3 경로, 환산값 1건, 게이트 범위 1건 누락 | 리뷰 시점에 스크립트·계약 문서·비용표를 대조 | 배포 전이라 청구 실측값 없음 | 정적 문서 정합성은 보완했으나 실제 AWS 청구 비교는 미실행 | 이우람, 착수 ADR·첫 운영 청구 확인 시 |

## 10. 남은 사항

- EC2 Instance Connect Endpoint의 실제 요금은 확인 전이며 E1 총액에 포함하지 않았다.
- 전용 Redis의 SSM Agent·CloudWatch Agent 유지 여부와 그에 따른 추가 엔드포인트 비용은 착수 ADR에서 결정한다.
