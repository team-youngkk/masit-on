package com.masiton.test;

/**
 * PostgreSQL 및 Redis 싱글톤 컨테이너를 공유하는 통합 테스트 베이스 클래스다.
 * Spring ApplicationContext 캐시를 단일화하기 위해 FullContextIntegrationTest를 상속한다.
 */
public abstract class PostgreSqlIntegrationTest extends FullContextIntegrationTest {
}
