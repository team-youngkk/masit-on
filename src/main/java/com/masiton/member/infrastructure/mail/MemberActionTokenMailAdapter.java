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
            message.setSubject("Verify your Masit-on email");
            message.setText("Enter this 8-character verification code in the Masit-on verification screen:\n" + rawToken);
        } else {
            message.setSubject("Reset your Masit-on password");
            message.setText("Enter this password reset token in the Masit-on password reset screen:\n" + rawToken);
        }
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }
}
