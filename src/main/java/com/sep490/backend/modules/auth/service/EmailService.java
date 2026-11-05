package com.sep490.backend.modules.auth.service;

import com.sep490.backend.common.notification.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
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

        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.error("Could not send password reset OTP email to [{}]: {}", to, e.getMessage());
            return false;
        }
    }

    public boolean sendRegistrationVerificationOtp(String to, String otp, long expiryMinutes) {
        if (!isConfigured()) {
            log.warn("Mail is not configured; registration verification OTP for [{}] was not sent", to);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(to);
        message.setSubject("Verify your Hola Vietnamese account");
        message.setText("""
                Welcome to Hola Vietnamese!

                Your email verification code is: %s

                This code expires in %d minutes.
                If you did not create this account, please ignore this email.
                """.formatted(otp, expiryMinutes));

        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.error("Could not send registration verification OTP to [{}]: {}", to, e.getMessage());
            return false;
        }
    }

    public boolean sendNotification(String to, String recipientName, NotificationMessage notification) {
        if (!isConfigured()) {
            log.debug("Mail is not configured; notification email to [{}] was not sent", to);
            return false;
        }
        if (to == null || to.isBlank()) {
            return false;
        }

        String greeting = recipientName == null || recipientName.isBlank()
                ? "Hello,"
                : "Hello " + recipientName.trim() + ",";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(to.trim());
        message.setSubject("Hola Vietnamese: " + notification.title());
        message.setText("""
                %s

                %s

                Open Hola Vietnamese to continue learning.
                """.formatted(greeting, notification.message()));

        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("Could not send notification email to [{}]: {}", to, e.getMessage());
            return false;
        }
    }

    public boolean isConfigured() {
        return mailEnabled
                && mailUsername != null
                && !mailUsername.isBlank()
                && mailPassword != null
                && !mailPassword.isBlank();
    }
}
