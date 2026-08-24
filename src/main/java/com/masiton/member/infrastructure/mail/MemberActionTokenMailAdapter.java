package com.masiton.member.infrastructure.mail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.MimeMessageHelper;
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
        try {
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(email);
            helper.setFrom(mailProperties.getFromAddress());
            if (purpose == MemberActionPurpose.EMAIL_VERIFICATION) {
                helper.setSubject("맛잇온 이메일 인증 코드");
                helper.setText("맛잇온 이메일 인증을 위해 아래 8자리 인증 코드를 입력해 주세요.\n\n인증 코드: " + rawToken);
            } else {
                helper.setSubject("맛잇온 비밀번호 재설정");
                String link = mailProperties.getPasswordResetUrl() + "#token="
                        + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
                String plainText = "맛잇온 비밀번호를 재설정하려면 아래 링크를 눌러 주세요.\n\n" + link;
                String htmlText = "<p>맛잇온 비밀번호를 재설정하려면 아래 버튼을 눌러 주세요.</p>"
                        + "<p><a href=\"" + escapeHtml(link) + "\">비밀번호 재설정</a></p>"
                        + "<p>이 링크는 한 번만 사용할 수 있으며 30분 동안 유효합니다.</p>";
                helper.setText(plainText, htmlText);
            }
            mailSender.send(message);
        } catch (RuntimeException | jakarta.mail.MessagingException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
