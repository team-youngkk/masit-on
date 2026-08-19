package com.masiton.orchestration.infrastructure.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.masiton.orchestration.application.port.out.DuplicateRegistrationCheckPort;

/**
 * {@link DuplicateRegistrationCheckPort}의 읽기 전용 Adapter다. 승인된 읽기 전용 DB Projection으로
 * 맛집·유튜버·영상·방문 4개 테이블을 조합한다(dependency-rules.md 3절).
 */
@Repository
class DuplicateRegistrationCheckQueryAdapter implements DuplicateRegistrationCheckPort {

    private final JdbcTemplate jdbcTemplate;

    DuplicateRegistrationCheckQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean restaurantExists(String kakaoPlaceId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM restaurant WHERE kakao_place_id = ?)", Boolean.class, kakaoPlaceId);
        return Boolean.TRUE.equals(exists);
    }
}
