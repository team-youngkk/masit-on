package com.masiton.member.infrastructure.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.common.security.MemberCookieSettings;
import com.masiton.member.application.port.out.MemberActionTokenDeliveryPort;
import com.masiton.member.domain.model.MemberActionPurpose;

@Component
public class MemberActionTokenMailAdapter implements MemberActionTokenDeliveryPort {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MemberCookieSettings cookieSettings;

    public MemberActionTokenMailAdapter(ObjectProvider<JavaMailSender> mailSenderProvider, MemberCookieSettings cookieSettings) {
        this.mailSenderProvider = mailSenderProvider;
        this.cookieSettings = cookieSettings;
    }

    @Override
    public void send(String email, MemberActionPurpose purpose, String rawToken) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        String path = purpose == MemberActionPurpose.EMAIL_VERIFICATION ? "/verify-email?token=" : "/password-reset?token=";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(purpose == MemberActionPurpose.EMAIL_VERIFICATION ? "Verify your Masit-on email" : "Reset your Masit-on password");
        message.setText("Open this link to continue: " + cookieSettings.publicBaseUrl() + path + rawToken);
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }
}
