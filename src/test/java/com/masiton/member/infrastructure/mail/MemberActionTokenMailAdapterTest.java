package com.masiton.member.infrastructure.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.infrastructure.configuration.MemberActionMailProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("회원 인증 메일 문구")
class MemberActionTokenMailAdapterTest {

    @Test
    @DisplayName("이메일 인증 메일은 verification code 문구를 사용한다")
    void send_이메일인증_검증코드문구사용() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MemberActionTokenMailAdapter adapter = adapter(mailSender);

        adapter.send("member@example.com", MemberActionPurpose.EMAIL_VERIFICATION, "AB7K9M2Q");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getFrom()).isEqualTo("no-reply@masiton.click");
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("Verify your Masit-on email");
        assertThat(messageCaptor.getValue().getText()).contains("verification code").contains("AB7K9M2Q");
    }

    @Test
    @DisplayName("비밀번호 재설정 메일은 password reset token 문구를 사용한다")
    void send_비밀번호재설정_재설정토큰문구사용() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MemberActionTokenMailAdapter adapter = adapter(mailSender);

        adapter.send("member@example.com", MemberActionPurpose.PASSWORD_RESET, "opaque-reset-token");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getFrom()).isEqualTo("no-reply@masiton.click");
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("Reset your Masit-on password");
        assertThat(messageCaptor.getValue().getText()).contains("password reset token").contains("opaque-reset-token");
    }

    private MemberActionTokenMailAdapter adapter(JavaMailSender mailSender) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("mailSender", mailSender);
        MemberActionMailProperties properties = new MemberActionMailProperties();
        properties.setFromAddress("no-reply@masiton.click");
        return new MemberActionTokenMailAdapter(beanFactory.getBeanProvider(JavaMailSender.class), properties);
    }
}
