package com.masiton.member.application;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.masiton.member.application.port.out.MemberActionMailOutboxStore;
import com.masiton.test.FullContextIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@DisplayName("회원 Action 메일 outbox 트랜잭션")
class MemberActionMailOutboxTransactionIntegrationTest extends FullContextIntegrationTest {

    @Autowired
    private MemberAuthenticationService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MemberActionMailOutboxStore outbox;

    @Test
    @DisplayName("outbox 기록이 실패하면 생성한 회원과 Action Token도 함께 rollback한다")
    void register_Outbox기록실패_회원과TokenRollback() {
        String email = "outbox-rollback-" + UUID.randomUUID() + "@example.com";
        doThrow(new IllegalStateException("outbox unavailable")).when(outbox).enqueue(any(), any());

        assertThatThrownBy(() -> service.register(email, "correct horse battery staple", "source-" + UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);

        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM member_account WHERE email = ?", Integer.class, email);
        Integer tokenCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM member_action_token token
                JOIN member_account account ON account.id = token.member_id
                WHERE account.email = ?
                """, Integer.class, email);
        assertThat(accountCount).isZero();
        assertThat(tokenCount).isZero();
    }
}
