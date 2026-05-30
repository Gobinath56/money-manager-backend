package com.moneymanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);          // ← REQUIRED for Gmail
            message.setTo(toEmail);
            message.setSubject("Money Manager — Password Reset OTP");
            message.setText(
                    "Hello,\n\n" +
                            "Your OTP for password reset is:\n\n" +
                            "  " + otp + "\n\n" +
                            "This OTP is valid for 10 minutes.\n" +
                            "If you did not request this, please ignore this email.\n\n" +
                            "— Money Manager"
            );
            mailSender.send(message);
            log.info("OTP email sent successfully to {}", toEmail);
        } catch (MailException e) {
            log.error("MAIL SEND FAILED to {}: {}", toEmail, e.getMessage(), e);
            throw e;   // re-throw so AuthService knows it failed
        }
    }
}