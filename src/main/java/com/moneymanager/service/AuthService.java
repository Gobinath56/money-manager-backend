package com.moneymanager.service;

import com.moneymanager.dto.AuthRequest;
import com.moneymanager.dto.AuthResponse;
import com.moneymanager.model.User;
import com.moneymanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.moneymanager.dto.ChangePasswordRequest;
import com.moneymanager.dto.ForgotPasswordRequest;
import com.moneymanager.dto.ResetPasswordRequest;
import com.moneymanager.model.PasswordResetToken;
import com.moneymanager.repository.PasswordResetTokenRepository;
import com.moneymanager.service.EmailService;

import java.time.LocalDateTime;
import java.util.Random;
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    // @Lazy breaks the circular dependency.
    // Spring won't try to build UserCategoryService at startup —
    // it creates a proxy and only resolves it on the first actual call.
    private final @Lazy UserCategoryService userCategoryService;

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        // Called lazily — UserCategoryService is resolved here, not at startup
        userCategoryService.seedDefaultsForUser(user.getEmail());

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }
    public void forgotPassword(String email) {
        // Check user exists
        userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        // Delete any existing OTP for this email
        passwordResetTokenRepository.deleteByEmail(email);

        // Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));

        // Save OTP to DB with 10 min expiry
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setOtp(otp);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);
        passwordResetTokenRepository.save(token);

        // Send OTP email
        emailService.sendOtpEmail(email, otp);
    }

    // ── Method 2: Reset Password ──────────────────────────────────────────────
    public void resetPassword(ResetPasswordRequest request) {
        // Find OTP record
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

        // Check OTP matches
        if (!token.getOtp().equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP. Please try again");
        }

        // Update password
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Mark token as used and delete
        passwordResetTokenRepository.deleteByEmail(request.getEmail());
    }

    // ── Method 3: Change Password (logged in user) ────────────────────────────
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Check new password is different
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("New password must be different from current password");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

}