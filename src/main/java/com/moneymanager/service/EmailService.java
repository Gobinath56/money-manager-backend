package com.moneymanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class EmailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            String body = String.format("""
                {
                  "sender": {"email": "%s"},
                  "to": [{"email": "%s"}],
                  "subject": "Money Manager — Password Reset OTP",
                  "textContent": "Hello,\\n\\nYour OTP for password reset is: %s\\n\\nThis OTP is valid for 10 minutes.\\n\\n— Money Manager"
                }
                """, fromEmail, toEmail, otp);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.brevo.com/v3/smtp/email", request, String.class);

            log.info("OTP email sent successfully to {} status {}", toEmail, response.getStatusCode());
        } catch (Exception e) {
            log.error("MAIL SEND FAILED to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send OTP email");
        }
    }
}