package com.moneymanager.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@Document(collection = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    private String id;

    private String email;
    private String otp;
    private LocalDateTime expiresAt;
    private boolean used;

    // FIX: track how many times the user has entered a wrong OTP
    // After MAX_OTP_ATTEMPTS (3) failures the token is deleted and
    // the user must request a fresh OTP — stops brute-force attacks
    private int failedAttempts = 0;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}