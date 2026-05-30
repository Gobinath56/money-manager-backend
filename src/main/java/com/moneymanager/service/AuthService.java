package com.moneymanager.service;

import com.moneymanager.dto.AuthRequest;
import com.moneymanager.dto.AuthResponse;
import com.moneymanager.model.User;
import com.moneymanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.moneymanager.dto.ChangePasswordRequest;
import com.moneymanager.dto.ResetPasswordRequest;
import com.moneymanager.model.PasswordResetToken;
import com.moneymanager.repository.PasswordResetTokenRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // FIX: max wrong OTP attempts before the token is invalidated
    private static final int MAX_OTP_ATTEMPTS = 3;

    private final @Lazy UserCategoryService userCategoryService;

    // ═══════════════════════════════════════════
    //  REGISTER
    // ═══════════════════════════════════════════

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        userCategoryService.seedDefaultsForUser(user.getEmail());

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }

    // ═══════════════════════════════════════════
    //  LOGIN
    // ═══════════════════════════════════════════

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }

    // ═══════════════════════════════════════════
    //  FORGOT PASSWORD — send OTP
    // ═══════════════════════════════════════════

    public void forgotPassword(String email) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        // Delete any existing token for this email before creating a new one
        passwordResetTokenRepository.deleteByEmail(email);

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(999999));

        log.info(">>> OTP for {} is: {}", email, otp);

        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setOtp(otp);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);
        // FIX: track failed attempts — starts at 0
        token.setFailedAttempts(0);
        passwordResetTokenRepository.save(token);

        emailService.sendOtpEmail(email, otp);
    }

    // ═══════════════════════════════════════════
    //  RESET PASSWORD — verify OTP + set new password
    //
    //  FIX: After MAX_OTP_ATTEMPTS (3) wrong guesses, the token is deleted.
    //  The user must request a fresh OTP. This stops brute-forcing even if
    //  the per-IP rate limiter is somehow bypassed.
    // ═══════════════════════════════════════════

    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("OTP not found. Please request a new one"));

        // Check if already used
        if (token.isUsed()) {
            throw new RuntimeException("OTP already used. Please request a new one");
        }

        // Check expiry
        if (token.isExpired()) {
            passwordResetTokenRepository.deleteByEmail(request.getEmail());
            throw new RuntimeException("OTP expired. Please request a new one");
        }

        // FIX: Check if too many failed attempts
        if (token.getFailedAttempts() >= MAX_OTP_ATTEMPTS) {
            passwordResetTokenRepository.deleteByEmail(request.getEmail());
            throw new RuntimeException(
                    "Too many incorrect attempts. Please request a new OTP"
            );
        }

        // Check OTP matches
        if (!token.getOtp().equals(request.getOtp())) {
            // FIX: increment failed attempts and save
            token.setFailedAttempts(token.getFailedAttempts() + 1);
            int remaining = MAX_OTP_ATTEMPTS - token.getFailedAttempts();

            if (remaining <= 0) {
                // Delete immediately on last failed attempt
                passwordResetTokenRepository.deleteByEmail(request.getEmail());
                throw new RuntimeException(
                        "Invalid OTP. Too many attempts — please request a new OTP"
                );
            }

            passwordResetTokenRepository.save(token);
            throw new RuntimeException(
                    "Invalid OTP. " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining"
            );
        }

        // OTP is correct — update the password
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Mark token as used and delete
        passwordResetTokenRepository.deleteByEmail(request.getEmail());
    }

    // ═══════════════════════════════════════════
    //  CHANGE PASSWORD (logged-in user)
    // ═══════════════════════════════════════════

    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}