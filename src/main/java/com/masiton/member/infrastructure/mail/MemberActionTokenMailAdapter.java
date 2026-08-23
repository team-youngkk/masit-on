package com.masiton.member.infrastructure.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.member.application.port.out.MemberActionTokenDeliveryPort;
import com.masiton.member.domain.model.MemberActionPurpose;
import com.masiton.member.infrastructure.configuration.MemberActionMailProperties;

@Component
public class MemberActionTokenMailAdapter implements MemberActionTokenDeliveryPort {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MemberActionMailProperties mailProperties;

    public MemberActionTokenMailAdapter(ObjectProvider<JavaMailSender> mailSenderProvider,
            MemberActionMailProperties mailProperties) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailProperties = mailProperties;
    }

    @Override
    public void send(String email, MemberActionPurpose purpose, String rawToken) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setFrom(mailProperties.getFromAddress());
        if (purpose == MemberActionPurpose.EMAIL_VERIFICATION) {
            message.setSubject("맛잇온 이메일 인증 코드");
            message.setText("맛잇온 이메일 인증을 위해 아래 8자리 인증 코드를 입력해 주세요.\n\n인증 코드: " + rawToken);
        } else {
            message.setSubject("맛잇온 비밀번호 재설정");
            message.setText("맛잇온 비밀번호를 재설정하려면 아래 재설정 토큰을 입력해 주세요.\n\n재설정 토큰: " + rawToken);
        }
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }
}
