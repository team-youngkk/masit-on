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
| 처리 일자 | 2026-08-17 |
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
| [3790288698](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288698) | Terraform 도입 근거 ADR 필요 | 인프라 | 팀 승인 완료 | Terraform `1.6.6`·AWS provider `5.100.0`, S3+DynamoDB backend를 ADR-PERF-003 Accepted로 확정 | [ADR-PERF-003](../07-adr/quality/perf-003-isolated-performance-terraform.md), 팀 결정 댓글 |
| [3790288700](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288700) | SG egress 축소 검토 | 인프라·보안 | 팀 승인 완료 | app·loadgen은 HTTPS·VPC DNS·필요한 내부 통신만 허용하고 db egress 제거 | `security.tf`와 ADR-PERF-003의 축소 정책 대조 |
| [3790288701](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288701) | 운영 rate-limit 상수와 k6 상수 drift 위험 | 애플리케이션 | 수정 불필요 | Java 구현과 k6 실행 자산 사이 공유 설정원 도입은 별도 설계 결정이며 이번 PR의 rate-limit 누락 수정 범위를 넘음 | 현재 상수·주석·기존 Mobility quota 복제 패턴 대조 |
| [3790288703](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790288703) | rate-limit 계측 헬퍼 공통화 | 애플리케이션 | 수정 필요 | 자연어·코스 rate-limit 판정을 공통 helper로 통합 | `node --check perf/k6/third-expansion-load.js` 통과 |
| [3790707012](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790707012) | 성능 정본 상태·결과 갱신 | 기타 | 수정 필요 | 정본 frontmatter·6절·실행 증거와 추적 문서를 `Verified`로 동기화 | 정상 20 RPS 결과표와 issue #207 증거 대조 |
| [3790707013](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790707013) | 공개 저장소 AWS 실행 식별자 마스킹 | 인프라 | 수정 필요 | SSM command ID·EC2 instance ID를 의미 있는 placeholder로 변경 | UUID·EC2 ID 정규식 재검색 |
| [3790707014](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790707014) | 실제 remote backend 구성 제공 | 인프라 | 수정 필요 | S3 backend·DynamoDB locking 선언, 예시 설정, bootstrap 보안 조건과 init 절차 추가 | backend 선언·README·ADR 대조; 실제 AWS profile 부재로 리소스 존재 확인은 미실행 |
| [3790832415](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790832415) | WireMock을 loopback에만 바인딩 | 보안·인프라 | 수정 필요 | `--bind-address 127.0.0.1` 추가 | `git diff --check`; Redis와 동일한 loopback 바인딩 정책으로 코드 대조 |
| [3790933943](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790933943) | Terraform·AWS provider 버전 정확 고정 | 인프라 | 수정 필요 | Terraform `1.6.6`, AWS provider `5.100.0`으로 소스·lock·ADR을 일치시킴 | 버전 선언·lock constraint·ADR 대조; Terraform 실행 파일 부재로 재검증 미실행 |
| [3790982442](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790982442) | WireMock fixture 배포와 매핑 로드 확인 | 인프라·검증 | 수정 필요 | 커밋 고정 GitHub archive를 SHA-256 검증 후 배포하고 SSM 매핑 확인 절차 추가 | archive SHA-256과 fixture 파일 추출을 로컬 확인; EC2 SSM 실행은 미실행 |
| [3790982443](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3790982443) | KMS alias ARN을 실제 key ARN으로 수정 | 보안·인프라 | 수정 필요 | `aws_kms_alias.ssm.target_key_arn`을 IAM Resource로 사용 | Terraform 소스 대조; Terraform 실행 파일 부재로 validate 미실행 |
| [3791400740](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3791400740) | 정확히 고정한 Terraform/provider 버전의 팀 사용 경로 | 인프라 | 수정 필요 | `infra/performance/terraform/.terraform-version`에 Terraform `1.6.6`을 기록 | 버전 선언·`.terraform-version`·README 대조 |
| [3791400742](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3791400742) | WireMock fixture 전체 파일 무결성 확인 | 인프라·검증 | 수정 필요 | JSON 매핑 23개·응답 17개 개수를 검증 | 저장소 fixture 개수와 user-data 검증값 대조 |
| [3791400743](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3791400743) | 이전 커밋 fixture 기본값의 의도 명시 | 인프라·문서 | 수정 필요 | 최신 HEAD와 독립된 검토 기준점임을 변수 설명·README에 명시 | fixture commit/hash와 문서 대조 |
| [3791495613](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3791495613) | `terraform fmt` object vars map 정렬 | 인프라·형식 | 수정 필요 | 가장 긴 키 기준으로 `ec2.tf` 정렬 | `git diff --check`; Terraform 실행 파일 부재로 `fmt -check` 미실행 |
| [3793578354](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3793578354) | `Accepted` ADR의 `reviewers` 공란 | 문서 계약 | 수정 필요 | frontmatter에 박진영(`jinyp01`)을 승인자로 기록하고 팀 결정 댓글 근거 링크 추가 | ADR frontmatter·[팀 결정 댓글](https://github.com/team-youngkk/masit-on/pull/218#issuecomment-5307378530) 대조 |
| [3793683606](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3793683606) | public subnet postcondition이 유효한 subnet 거부 | 인프라·계약 정합성 | 수정 필요 | `map_public_ip_on_launch` 필수 조건을 제거하고 지정 VPC 소속만 검증 | `data.tf`와 EC2의 `associate_public_ip_address = true` 대조; Terraform 실행 파일 부재로 validate 미실행 |
| [3793683608](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3793683608) | 정상 20 RPS 완료 후 문서의 미측정 상태 잔존 | 문서 계약 정합성 | 수정 필요 | 정상 부하를 `Verified`로 통일하고 최대 80 RPS만 정식 판정 보류로 분리 | 정본 결과·2차/3차 테스트 문서·ADR 추적표 대조 |
| [3793783777](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3793783777) | public subnet 검증이 private subnet을 허용 | 인프라·네트워크 정합성 | 수정 필요 | subnet별 route table을 조회해 public은 IGW 기본 경로를, private은 IGW 기본 경로 부재를 검증 | `data.tf`·README 대조; Terraform 실행 파일 부재로 validate 미실행 |
| [3793818107](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3793818107) | subnet과 VPC 소속 관계 검증 누락 | 인프라·네트워크 경계 | 수정 필요 | public/private `aws_subnet` data source에 입력 VPC 소속 postcondition 추가 | `data.tf`·VPC 변수 대조; Terraform 실행 파일 부재로 validate 미실행 |
| [3793953578](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3793953578) | 증적 manifest의 15개 파일 SHA-256·aggregate 불일치 | 검증·문서 계약 | 수정 필요 | PR HEAD에서 PowerShell/.NET SHA-256으로 15개 항목과 aggregate를 재계산하고 최종 게이트 fingerprint를 동기화 | aggregate `2b15e9c4cb7a2fca3773c3a61279bad9d98405eeda3454d688b7d6c63c0afa24`; `verify-third-expansion-evidence.ps1` 통과 |
| [3793953582](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3793953582) | route의 nullable `gateway_id`에 `coalesce(..., "")` 사용 | 인프라·Terraform 표현식 | 수정 필요 | null-safe conditional로 교체해 NAT·peering·TGW 등 gateway_id가 비어 있는 route도 검증 단계에서 오류 없이 처리 | Terraform 1.6.6 + AWS provider 5.100.0 `fmt/init/validate` 통과 |
| [3794171070](https://github.com/team-youngkk/masit-on/pull/218#discussion_r3794171070) | `subnet_id` 조회가 명시적 route table 연결이 없는 main route table 사용 subnet에서 실패 | 인프라·네트워크 정합성 | 수정 필요 | `association.subnet-id`로 명시적 연결을 먼저 조회하고 없으면 지정 VPC의 `main_route_table_id`를 사용하도록 fallback | AWS provider `aws_route_tables`·`aws_vpc` data source 계약 대조; Terraform 1.6.6 + AWS provider 5.100.0 `fmt/init/validate` 통과 |

## 3. 문제 현상과 발생 조건

- 오류 메시지: Terraform `templatefile`의 vars map 미사용 키 오류, 검증 범위가 넓은 Terraform/provider 버전, 팀 실행 버전 기준 부재, WireMock fixture 부분 검증·미배포·loopback 미지정, arm64 WireMock pull 실패 위험, local backend state 노출 위험, KMS alias ARN 권한 오류.
- 발생 환경: PR #218의 `test/nl-search-rate-limit-load-model`, Terraform 1.6 이상을 전제로 한 AWS arm64 격리 성능 환경.
- 재현 조건: loadgen 템플릿에 사용하지 않는 변수를 전달하거나, public subnet의 `map_public_ip_on_launch`가 true인 환경을 false로 검증하거나, 명시적 route table 연결이 없는 subnet에 `subnet_id` 기반 route table data source를 사용하거나, backend 없이 `terraform init`을 실행한다.
- 실제 결과: apply 전에 Terraform 계획 단계에서 실패할 수 있고, 검증하지 않은 버전으로 plan 결과가 달라질 수 있었으며, 팀원이 다른 Terraform patch 버전으로 실행할 기준이 없었다. 새 인스턴스의 WireMock이 일부 fixture만 가진 채 시작하거나 모든 인터페이스에 바인딩될 수 있었다. 기본 이미지가 arm64에서 pull되지 않을 수 있고, local state에 RDS 비밀번호가 남거나 KMS 복호화 권한이 동작하지 않을 수 있었다.
- 기대 결과: 입력 환경 검증이 실제 역할과 일치하고 검증 버전과 실행 기준 파일이 고정되며, fixture 전체 파일 개수·무결성 검증 후 배포되고 매핑 로드가 확인돼야 한다. arm64 기본 이미지가 실행되고 WireMock은 앱 EC2 내부 loopback에서만 수신하며, state는 암호화·locking된 remote backend에만 저장돼야 한다.
- 영향 범위: 이슈 #207 성능 검증 환경의 생성 재현성·비용·state 비밀정보 보호. 제품 API·운영 데이터에는 직접 영향이 없다.

## 4. 근본 원인

Terraform 구성과 성능 결과 문서가 서로 다른 실행 기준을 가리켰다. 증적 manifest는 파일별 해시와 aggregate를 수동으로 수정하는 과정에서 실제 HEAD와 어긋났고, route table 검증은 nullable `gateway_id`에 빈 문자열을 넣기 위해 `coalesce`를 사용했다. 템플릿 변수는 본문 사용 여부를 확인하지 않고 복사됐고, public/private subnet postcondition은 복사 과정에서 boolean이 뒤집히지 않았다. Terraform/provider 제약은 검증 버전보다 넓었고 팀이 사용할 실행 기준 파일도 없었다. loadgen 사양과 WireMock 이미지 기본값은 실제 arm64 실행 결과와 동기화되지 않았다. WireMock fixture 전달·로드 확인 단계가 없었고, 전체 파일 검증 없이 대표 파일 두 개만 확인했으며, 실행 옵션에는 애플리케이션의 loopback 접근 의도가 반영되지 않았다. RDS 이름 precondition은 실행별 prefix 규칙을 고려하지 않아 항상 참이었으며, KMS 정책은 alias ARN을 key ARN 위치에 사용했다.

성능 문서는 격리 실행 결과를 기록하면서 정본 문서와 상태를 갱신하지 않아 `통과`와 `Not Measured`가 동시에 남았다. 공개 저장소 증적 보호 규칙과 실행 식별자 마스킹도 결과 문서에 적용되지 않았다.

Terraform 도입과 egress 축소는 코드 오류가 아니라 팀 운영·보안 선택이었다. public subnet의 route table 검증을 보강하는 과정에서 public/private subnet data source의 VPC 소속 postcondition을 함께 유지하지 못한 것이 추가 원인이었다. `subnet_id` 필터가 명시적 association만 찾는다는 provider 동작을 고려하지 않아 VPC main route table fallback이 빠져 있었다. 검증 과정에서는 AWS provider 5.100.0의 route table export 이름이 `routes`인데 기존 코드가 `route`를 사용한 오류와 Terraform 1.6.6 lock constraint의 비정규 표기도 함께 확인해 수정했다. 정상 부하 완료 뒤 stale 상태 문장을 남긴 문서 동기화 누락도 함께 확인했다. Accepted ADR의 `reviewers`가 비어 있어 승인자와 결정 근거가 문서에서 끊긴 것도 별도 문서 계약 결함으로 확인했다. 2026-08-16 팀 리뷰에서 Terraform 도입과 축소 egress 정책을 승인해 ADR-PERF-003을 `Accepted`로 전환하고 구현에 반영한다. 실제 backend bucket·table bootstrap과 접근 role은 실행 전 운영 작업으로 남긴다.

## 5. 확인 및 시도

| 확인하거나 시도한 방법 | 결과 | 판단과 다음 단계 |
|---|---|---|
| PR review thread와 PR diff 대조 | 초기 13개와 후속 16개 확인 | 26개는 코드·문서로 처리하고, 후속 팀 결정 2건을 ADR·보안 그룹·문서에 반영 |
| 기존 `docs/troubleshooting` 검색 | PR #208·#214의 성능 추적성 기록 확인 | 기존 기록의 증거·지표 기록 방식을 재사용하고 새 사건으로 기록 |
| `rg`로 AWS command/instance ID 검색 | 결과 문서와 README에 원문 식별자 확인 | 공개 문서의 식별자를 placeholder로 교체 |
| `git diff --check` | 통과 | whitespace 오류 없음 |
| `node --check perf/k6/third-expansion-load.js` | 통과 | k6 스크립트 구문 확인 |
| `terraform fmt -check`, `terraform init -backend=false`, `terraform validate` | 통과 | Terraform 1.6.6과 AWS provider 5.100.0을 임시 검증 디렉터리에 구성해 실행; lock constraint를 `5.100.0` 정규 형식으로 보정한 뒤 성공 |
| AWS `sts/list-buckets/list-tables` 조회 | 실패 | 로컬 AWS config에 `masiton` profile이 없어 backend 리소스 존재를 확인하지 못함; 임의의 existing state로 보고하지 않음 |

## 6. 최종 해결

- 변경 내용: Terraform 입력·버전·이미지·사양·provider lock constraint 정규화·public/private subnet의 VPC 소속·명시적 route table association 및 VPC main route table fallback·`routes` export 기반 route 검증·nullable route gateway 처리·dead code 수정, KMS key ARN 권한 수정, 커밋 고정·체크섬 검증 WireMock fixture 배포와 loopback 바인딩, 제한된 egress 규칙, rate-limit helper 공통화, 정상 부하 `Verified`와 최대 부하 보류 상태로 성능 추적 문서 동기화, AWS 실행 식별자 마스킹, S3+DynamoDB backend 설계와 Accepted ADR 반영, ADR 승인자와 팀 결정 근거 링크 기록.
- 선택 이유: 확인 가능한 실행 결과와 현재 계약에 맞추고, 팀이 승인한 Terraform 도입·egress 정책을 코드·ADR·실행 문서에 일치시키기 위해서다.
- 변경 파일: `infra/performance/terraform/`, `infra/performance/README.md`, `perf/k6/third-expansion-load.js`, `docs/08-planning/`, `docs/07-adr/`, `docs/troubleshooting/`, `.gitignore`
- 고려한 대안: backend가 팀에 이미 존재한다고 가정하지 않고 bucket/table 제안 이름과 bootstrap 조건을 명시했다. egress는 전체 outbound 유지안과 비교한 뒤 팀 승인에 따라 축소안을 적용했다.

## 7. 검증

| 검증 | 결과 | 확인한 내용 |
|---|---|---|
| `git diff --check` | 통과 | 변경 파일 whitespace 오류 없음 |
| `node --check perf/k6/third-expansion-load.js` | 통과 | rate-limit helper 변경 후 JavaScript 구문 정상 |
| WireMock archive SHA-256·fixture 추출 확인 | 통과 | `414cf7e...` archive의 SHA-256과 `mappings`·`__files` 파일 존재 확인 |
| WireMock fixture 전체 개수 대조 | 통과 | mappings JSON 23개·`__files` JSON 17개를 저장소와 user-data에 동일하게 반영 |
| Terraform 실행 버전 기준 대조 | 통과 | `versions.tf`, `.terraform.lock.hcl`, `.terraform-version`, ADR, README가 각각 `1.6.6`·`5.100.0`을 가리킴 |
| `terraform fmt -check` | 통과 | Terraform 1.6.6으로 전체 Terraform 구성 확인 |
| `terraform init -backend=false`·`terraform validate` | 통과 | AWS provider 5.100.0을 lock 파일에서 재사용해 backend 없이 초기화·구성 검증; 실제 AWS backend bootstrap·plan은 미실행 |
| PR HEAD 증적 manifest 재계산·검증 스크립트 | 통과 | 15개 파일 SHA-256과 LF-joined aggregate를 PowerShell/.NET으로 재계산한 뒤 `scripts/verify-third-expansion-evidence.ps1` 실행; aggregate `2b15e9c4cb7a2fca3773c3a61279bad9d98405eeda3454d688b7d6c63c0afa24`와 최종 게이트 fingerprint가 일치 |
| 전체 Gradle build | 미실행 | 이번 변경은 Java·Gradle 코드가 아니며 Terraform/k6 검증 환경이 별도로 필요 |

## 8. 재발 방지 및 다음 확인

- 재발 방지: Terraform backend 선언·예시 설정·state 보안 조건을 문서화하고, 결과 문서의 정본 상태·실행 증거·공개 식별자 보호 규칙을 함께 갱신했다. Accepted ADR 전환 시 `status`, `reviewers`, 승인 근거 링크를 함께 확인하고, 성능 측정 상태를 갱신할 때 2차·3차 추적표와 정본 결과의 정상/최대 부하 상태를 함께 대조하고, subnet 입력을 받을 때 VPC 소속과 route table의 IGW 기본 경로를 함께 검증하고, 명시적 association이 없으면 VPC main route table을 사용한다. 증적 manifest는 수동 해시를 편집하지 않고 HEAD의 실제 파일에서 재생성하며, Terraform route 검증에서는 nullable provider attribute에 `coalesce(..., "")`를 사용하지 않는다.
- 다음 확인: 이우람이 AWS role로 backend bucket/table을 bootstrap한 뒤 `terraform init -backend-config=backend.hcl`, `terraform fmt -check`, `terraform validate`, `terraform plan`을 실행한다. egress 변경 후 app·loadgen의 HTTPS/DNS/내부 통신과 RDS egress 없음이 plan에서 일치하는지 확인한다.

## 9. 도입 전후 비교 지표

| 지표 | 도입 전 기준값 | 측정 방법·기간 | 배포 확장 후 값 | 비교 결과 | 담당자·확인 시점/이슈 |
|---|---|---|---|---|---|
| Terraform 계획·적용 성공률 | 측정 전; 현재 구성은 미사용 template var·subnet 조건으로 실패 가능 | backend bootstrap 후 동일 tfvars로 `fmt/validate/plan` 실행 | 미측정 | Terraform 설치·AWS 자격 증명 후 확인 | 이우람, 다음 격리 실행 |
| 공개 문서의 원문 AWS 실행 식별자 수 | 12개 이상 확인 | `rg`로 UUID·EC2 ID 패턴 검색 | 0개(현재 변경 범위 확인) | 마스킹 규칙 적용 | PR #218 반영 시점 |
| 정상 부하 공개 조회 p95 | 기존 정본 미측정 | 2026-08-15 격리 실행, 20 RPS | popular 19.9ms, curation list 12.2ms, detail 10.1ms | 기준 500ms 이하 통과 | [정본 결과](../08-planning/second-expansion-performance-verification.md) |

## 10. 남은 사항

- Terraform 검증과 실제 AWS backend bootstrap은 아직 실행하지 않았다. AWS role과 비용·리소스 생성 조건을 확인한 뒤 실행한다.
- 팀 승인된 Terraform 도입·egress 정책은 ADR, Terraform, README에 반영했고, ADR frontmatter에 박진영(`jinyp01`)의 승인자 기록과 팀 결정 댓글 근거를 추가했다.
