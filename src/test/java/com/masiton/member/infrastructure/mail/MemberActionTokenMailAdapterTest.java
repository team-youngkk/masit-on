package com.masiton.member.infrastructure.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import java.util.Properties;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;

import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.infrastructure.configuration.MemberActionMailProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("회원 인증 메일 문구")
class MemberActionTokenMailAdapterTest {

    @Test
    @DisplayName("이메일 인증 메일은 한국어 인증 코드 문구를 사용한다")
    void send_이메일인증_검증코드문구사용() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MemberActionTokenMailAdapter adapter = adapter(mailSender);
        MimeMessage message = mimeMessage();
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(message);

        adapter.send("member@example.com", MemberActionPurpose.EMAIL_VERIFICATION, "AB7K9M2Q");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getFrom()[0].toString()).isEqualTo("no-reply@masiton.click");
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("맛잇온 이메일 인증 코드");
        assertThat(textContent(messageCaptor.getValue())).contains("인증 코드:").contains("AB7K9M2Q");
    }

    @Test
    @DisplayName("비밀번호 재설정 메일은 한국어 링크 문구와 하이퍼링크를 사용한다")
    void send_비밀번호재설정_한국어링크문구사용() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MemberActionTokenMailAdapter adapter = adapter(mailSender);
        MimeMessage message = mimeMessage();
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(message);

        adapter.send("member@example.com", MemberActionPurpose.PASSWORD_RESET, "opaque-reset-token");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getFrom()[0].toString()).isEqualTo("no-reply@masiton.click");
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("맛잇온 비밀번호 재설정");
        assertThat(textContent(messageCaptor.getValue())).contains("비밀번호 재설정").contains("opaque-reset-token");
        assertThat(textContent(messageCaptor.getValue())).contains("http://localhost:3000/password-reset#token=opaque-reset-token");
    }

    private MemberActionTokenMailAdapter adapter(JavaMailSender mailSender) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("mailSender", mailSender);
        MemberActionMailProperties properties = new MemberActionMailProperties();
        properties.setFromAddress("no-reply@masiton.click");
        return new MemberActionTokenMailAdapter(beanFactory.getBeanProvider(JavaMailSender.class), properties);
    }

    private MimeMessage mimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private String textContent(MimeMessage message) throws Exception {
        return textContent(message.getContent());
    }

    private String textContent(Object content) throws Exception {
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart part = multipart.getBodyPart(index);
                text.append(textContent(part.getContent()));
            }
            return text.toString();
        }
        return String.valueOf(content);
    }
}
