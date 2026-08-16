---
related_documents:
  - ../07-adr/quality/perf-001-k6-load-testing.md
  - ../07-adr/quality/perf-003-isolated-performance-terraform.md
  - ../08-planning/issue-207-isolated-performance-result.md
  - ../08-planning/second-expansion-performance-verification.md
  - ../../infra/performance/README.md
  - ../../perf/k6/third-expansion-load.js
---

# PR #218 리뷰 트러블슈팅: 격리 성능 환경·부하 결과 정합성

## 1. 개요

| 항목 | 내용 |
|---|---|
| PR | [#218](https://github.com/team-youngkk/masit-on/pull/218) |
| 작성자 | `w00lam` |
| 처리 일자 | 2026-08-16 |
| 범위 | Terraform 격리 환경, k6 자연어 부하 모델, 성능 결과 문서와 실행 절차 리뷰 반영 |
| 주 문제 유형 | 인프라·배포·기타(문서 계약 정합성) |
| 기존 기록 | [PR #208](pr-208-operational-performance-review.md)의 성능 추적성 기록과 [PR #214](pr-214-popular-restaurant-query-count-review.md)의 측정 근거 기록을 확인했다. 이번 PR은 Terraform backend·격리 실행 식별자·정본 상태 불일치가 새로 발생한 사건이므로 별도 기록을 추가한다. |

## 2. 리뷰 스레드 처리 결과

| 스레드 | 요청 요약 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| [3790288687](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288687) | loadgen `templatefile` 미사용 `aws_region` 제거 | 인프라 | 수정 필요 | vars map에서 제거 | `git diff --check`; Terraform 실행 파일 부재로 `validate` 미실행 |
| [3790288689](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288689) | public subnet postcondition을 `true`로 수정 | 인프라 | 수정 필요 | public/private 조건을 구분 | 코드 대조; Terraform `validate` 미실행 |
| [3790288692](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288692) | arm64 호환 WireMock 기본 이미지 사용 | 인프라 | 수정 필요 | 기본값을 `wiremock/wiremock:3.13.2`로 변경 | 격리 결과의 arm64 실행 기록과 기본 tfvars 대조 |
| [3790288693](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288693) | loadgen 사양을 `t4g.small`로 정렬 | 인프라 | 수정 필요 | Terraform을 결과 문서 기준으로 변경 | Terraform·결과 문서 대조 |
| [3790288694](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288694) | 항상 참인 RDS precondition 제거 | 인프라 | 수정 필요 | dead code 제거; `run_id` 형식 검증은 유지 | `locals.tf`·`variables.tf` 대조 |
| [3790288696](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288696) | throughput 자연어 p95·실패율 threshold 공백 | 기타 | 이미 해결 | 의도된 trade-off를 결과·부하 모델 문서에 명시하고 throughput을 성능 인증으로 표시하지 않음 | [자연어 부하 모델](../08-planning/issue-207-natural-language-load-model.md) 3절과 k6 요약 경고 |
| [3790288698](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288698) | Terraform 도입 근거 ADR 필요 | 인프라 | 결정 필요 | 담당자 결정으로 Proposed ADR과 backend 설계를 추가했으나 팀 승인 전이므로 해결하지 않음 | [ADR-PERF-003](../07-adr/quality/perf-003-isolated-performance-terraform.md) |
| [3790288700](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288700) | SG egress 축소 검토 | 인프라 | 결정 필요 | 사용자 선택 없이 egress 정책을 바꾸지 않음 | 현재 `security.tf` 유지; 보안 담당·팀 결정 필요 |
| [3790288701](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288701) | 운영 rate-limit 상수와 k6 상수 drift 위험 | 애플리케이션 | 수정 불필요 | Java 구현과 k6 실행 자산 사이 공유 설정원 도입은 별도 설계 결정이며 이번 PR의 rate-limit 누락 수정 범위를 넘음 | 현재 상수·주석·기존 Mobility quota 복제 패턴 대조 |
| [3790288703](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288703) | rate-limit 계측 헬퍼 공통화 | 애플리케이션 | 수정 필요 | 자연어·코스 rate-limit 판정을 공통 helper로 통합 | `node --check perf/k6/third-expansion-load.js` 통과 |
| [3790707012](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790707012) | 성능 정본 상태·결과 갱신 | 기타 | 수정 필요 | 정본 frontmatter·6절·실행 증거와 추적 문서를 `Verified`로 동기화 | 정상 20 RPS 결과표와 issue #207 증거 대조 |
| [3790707013](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790707013) | 공개 저장소 AWS 실행 식별자 마스킹 | 인프라 | 수정 필요 | SSM command ID·EC2 instance ID를 의미 있는 placeholder로 변경 | UUID·EC2 ID 정규식 재검색 |
| [3790707014](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790707014) | 실제 remote backend 구성 제공 | 인프라 | 수정 필요 | S3 backend·DynamoDB locking 선언, 예시 설정, bootstrap 보안 조건과 init 절차 추가 | backend 선언·README·ADR 대조; 실제 AWS profile 부재로 리소스 존재 확인은 미실행 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: Terraform `templatefile`의 vars map 미사용 키 오류, arm64 WireMock pull 실패 위험, local backend state 노출 위험.
- 발생 환경: PR #218의 `test/nl-search-rate-limit-load-model`, Terraform 1.6 이상을 전제로 한 AWS arm64 격리 성능 환경.
- 재현 조건: loadgen 템플릿에 사용하지 않는 변수를 전달하거나, public subnet의 `map_public_ip_on_launch`가 true인 환경을 false로 검증하거나, backend 없이 `terraform init`을 실행한다.
- 실제 결과: apply 전에 Terraform 계획 단계에서 실패할 수 있고, 기본 WireMock 이미지가 arm64에서 pull되지 않을 수 있으며, local state에 RDS 비밀번호가 남을 수 있었다.
- 기대 결과: 입력 환경 검증이 실제 역할과 일치하고 arm64 기본 이미지가 실행되며, state는 암호화·locking된 remote backend에만 저장돼야 한다.
- 영향 범위: 이슈 #207 성능 검증 환경의 생성 재현성·비용·state 비밀정보 보호. 제품 API·운영 데이터에는 직접 영향이 없다.

## 4. 근본 원인

Terraform 구성과 성능 결과 문서가 서로 다른 실행 기준을 가리켰다. 템플릿 변수는 본문 사용 여부를 확인하지 않고 복사됐고, public/private subnet postcondition은 복사 과정에서 boolean이 뒤집히지 않았다. loadgen 사양과 WireMock 이미지 기본값은 실제 arm64 실행 결과와 동기화되지 않았다. RDS 이름 precondition은 실행별 prefix 규칙을 고려하지 않아 항상 참이었다.

성능 문서는 격리 실행 결과를 기록하면서 정본 문서와 상태를 갱신하지 않아 `통과`와 `Not Measured`가 동시에 남았다. 공개 저장소 증적 보호 규칙과 실행 식별자 마스킹도 결과 문서에 적용되지 않았다.

Terraform 도입과 egress 축소는 코드 오류가 아니라 팀 운영·보안 선택이다. 담당자 결정으로 Terraform은 `Proposed` ADR에 기록했지만, 팀 승인 전에는 Accepted로 표시하지 않았다. egress는 사용자의 선택이 없으므로 변경하지 않았다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR review thread와 PR diff 대조 | 미해결 13개, outdated 아님 | 11개는 코드·문서로 처리, Terraform ADR·egress는 결정 필요로 분리 |
| 기존 `docs/troubleshooting` 검색 | PR #208·#214의 성능 추적성 기록 확인 | 기존 기록의 증거·지표 기록 방식을 재사용하고 새 사건으로 기록 |
| `rg`로 AWS command/instance ID 검색 | 결과 문서와 README에 원문 식별자 확인 | 공개 문서의 식별자를 placeholder로 교체 |
| `git diff --check` | 통과 | whitespace 오류 없음 |
| `node --check perf/k6/third-expansion-load.js` | 통과 | k6 스크립트 구문 확인 |
| `terraform fmt -check`, `terraform validate` | 미실행 | 로컬 환경에 `terraform` 실행 파일이 없어 실행 불가; Terraform 설치 후 수행 필요 |
| AWS `sts/list-buckets/list-tables` 조회 | 실패 | 로컬 AWS config에 `masiton` profile이 없어 backend 리소스 존재를 확인하지 못함; 임의의 existing state로 보고하지 않음 |

## 6. 최종 해결

- 변경 내용: Terraform 입력·이미지·사양·dead code 수정, rate-limit helper 공통화, 성능 정본과 추적 문서 갱신, AWS 실행 식별자 마스킹, S3+DynamoDB backend 설계와 Proposed ADR 추가.
- 선택 이유: 확인 가능한 실행 결과와 현재 계약에 맞추고, 팀 결정이 필요한 Terraform 도입·egress를 사실과 승인 상태대로 분리하기 위해서다.
- 변경 파일: `infra/performance/terraform/`, `infra/performance/README.md`, `perf/k6/third-expansion-load.js`, `docs/08-planning/`, `docs/07-adr/`, `docs/troubleshooting/`, `.gitignore`
- 고려한 대안: backend가 팀에 이미 존재한다고 가정하지 않고 bucket/table 제안 이름과 bootstrap 조건을 명시했다. egress는 보안상 축소안을 검토했지만 사용자·보안 담당의 결정 없이 적용하지 않았다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 변경 파일 whitespace 오류 없음 |
| `node --check perf/k6/third-expansion-load.js` | 통과 | rate-limit helper 변경 후 JavaScript 구문 정상 |
| `terraform fmt -check` | 미실행 | Terraform 실행 파일 부재 |
| `terraform validate` | 미실행 | Terraform 실행 파일 부재 및 AWS backend 리소스 미확인 |
| 전체 Gradle build | 미실행 | 이번 변경은 Java·Gradle 코드가 아니며 Terraform/k6 검증 환경이 별도로 필요 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: Terraform backend 선언·예시 설정·state 보안 조건을 문서화하고, 결과 문서의 정본 상태·실행 증거·공개 식별자 보호 규칙을 함께 갱신했다.
- 다음 확인: 이우람이 AWS role로 backend bucket/table을 bootstrap한 뒤 `terraform init -backend-config=backend.hcl`, `terraform fmt -check`, `terraform validate`, `terraform plan`을 실행한다. 팀 리뷰에서 ADR-PERF-003과 egress 정책을 승인하거나 수정한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| Terraform 계획·적용 성공률 | 측정 전; 현재 구성은 미사용 template var·subnet 조건으로 실패 가능 | backend bootstrap 후 동일 tfvars로 `fmt/validate/plan` 실행 | 미측정 | Terraform 설치·AWS 자격 증명 후 확인 | 이우람, 다음 격리 실행 |
| 공개 문서의 원문 AWS 실행 식별자 수 | 12개 이상 확인 | `rg`로 UUID·EC2 ID 패턴 검색 | 0개(현재 변경 범위 확인) | 마스킹 규칙 적용 | PR #218 반영 시점 |
| 정상 부하 공개 조회 p95 | 기존 정본 미측정 | 2026-08-15 격리 실행, 20 RPS | popular 19.9ms, curation list 12.2ms, detail 10.1ms | 기준 500ms 이하 통과 | [정본 결과](../08-planning/second-expansion-performance-verification.md) |

## 10. 남은 사항

- [Terraform ADR 스레드](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288698): Terraform 도입은 담당자 결정으로 Proposed ADR에 기록했지만 팀 Accepted 승인이 필요하다.
- [보안그룹 egress 스레드](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288700): egress 축소 여부는 보안·운영 정책 결정 전이며, 현재 코드는 변경하지 않았다.
- Terraform 검증은 `terraform` 실행 파일과 AWS 자격 증명 부재로 미실행이다.
