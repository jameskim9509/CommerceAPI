package com.zerobase.userApi.service;

import com.zerobase.userApi.dto.SendMailDto;
import com.zerobase.userApi.exception.CustomException;
import com.zerobase.userApi.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * ADR-007: 이메일 발송을 MailGun(Feign REST) 에서 Gmail SMTP(App Password) 로 전환.
 *
 * <p>자격증명(MAIL_USERNAME / MAIL_PASSWORD)은 환경변수로만 주입하며 저장소에 커밋하지 않는다.
 * 로컬은 {@code .vscode/.env.local}(VS Code launch.json envFile, gitignore 대상)에서 주입한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmailClient {

    private final JavaMailSender mailSender;

    // Gmail SMTP 는 인증 계정과 발신자(From)가 일치해야 하므로 spring.mail.username 을 그대로 발신자로 사용한다.
    @Value("${spring.mail.username:}")
    private String from;

    public void sendEmail(SendMailDto form) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(form.getTo());
            message.setSubject(form.getSubject());
            message.setText(form.getText());

            mailSender.send(message);
        } catch (MailException e) {
            log.error("이메일 발송 실패 - to: {}, subject: {}", form.getTo(), form.getSubject(), e);
            throw new CustomException(ErrorCode.SEND_EMAIL_ERROR);
        }
    }
}
