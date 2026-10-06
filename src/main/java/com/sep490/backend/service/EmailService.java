package com.sep490.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public boolean sendPasswordResetOtp(String to, String otp) {
        if (!isConfigured()) {
            log.warn("Mail is not configured; password reset OTP for [{}] is [{}]", to, otp);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(to);
        message.setSubject("Hola Vietnamese password reset OTP");
        message.setText("""
                Your Hola Vietnamese password reset OTP is: %s

                This code expires in 15 minutes.
                If you did not request this, please ignore this email.
                """.formatted(otp));

        mailSender.send(message);
        return true;
    }

    private boolean isConfigured() {
        return mailEnabled
                && mailUsername != null
                && !mailUsername.isBlank()
                && mailPassword != null
                && !mailPassword.isBlank();
    }
}
