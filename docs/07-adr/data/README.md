---
related_documents:
  - ../README.md
  - ../../05-specs/data/README.md
  - data-001-postgresql.md
  - data-002-database-placement.md
  - data-003-spring-data-jpa.md
  - data-004-flyway.md
  - data-005-redis-refresh-token.md
  - data-007-uuid-v4-identifiers.md
  - data-008-publication-lifecycle-soft-delete.md
  - data-009-pre-release-migration-consolidation.md
  - data-010-recent-view-retention-cleanup.md
  - data-011-popular-restaurant-request-time-aggregation.md
  - data-012-second-expansion-retention-cleanup.md
---

# 데이터 ADR

관계형 데이터베이스, 환경별 배치, ORM, 마이그레이션과 데이터 저장소 결정을 관리한다.

| ADR | 제목 |
|---|---|
| [ADR-DATA-001](data-001-postgresql.md) | PostgreSQL 17.10 주 데이터베이스 |
| [ADR-DATA-002](data-002-database-placement.md) | 개발 Docker와 운영 RDS 데이터베이스 분리 |
| [ADR-DATA-003](data-003-spring-data-jpa.md) | Spring Data JPA 기본 데이터 접근 |
| [ADR-DATA-004](data-004-flyway.md) | Flyway 스키마 마이그레이션 |
| [ADR-DATA-005](data-005-redis-refresh-token.md) | Redis 8.8 관리자 Refresh Token 저장소(Superseded) |
| [ADR-DATA-007](data-007-uuid-v4-identifiers.md) | 애플리케이션 생성 UUID v4 내부 식별자 |
| [ADR-DATA-008](data-008-publication-lifecycle-soft-delete.md) | 공개 상태와 논리 삭제 생명주기 분리 |
| [ADR-DATA-009](data-009-pre-release-migration-consolidation.md) | 운영 배포 전 마이그레이션 통합 범위 |
| [ADR-DATA-010](data-010-recent-view-retention-cleanup.md) | 최근 본 맛집 보존 기간 정리 실행 |
| [ADR-DATA-011](data-011-popular-restaurant-request-time-aggregation.md) | 인기 맛집 요청 시점 실시간 집계 |
| [ADR-DATA-012](data-012-second-expansion-retention-cleanup.md) | 2차 확장 보존 정책 정리 실행 |
