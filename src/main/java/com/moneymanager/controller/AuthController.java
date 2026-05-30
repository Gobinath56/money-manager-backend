// src/main/java/com/moneymanager/controller/AuthController.java
package com.moneymanager.controller;

import com.moneymanager.dto.AuthRequest;
import com.moneymanager.dto.AuthResponse;
import com.moneymanager.dto.ChangePasswordRequest;
import com.moneymanager.dto.ForgotPasswordRequest;
import com.moneymanager.dto.ResetPasswordRequest;
import com.moneymanager.service.AuthService;
import com.moneymanager.service.RateLimitService;
import com.moneymanager.util.SecurityUtils;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService       authService;
    private final SecurityUtils     securityUtils;
    private final RateLimitService  rateLimitService;

    // ── Helper: extract real IP even behind a proxy / load balancer ──────
    // X-Forwarded-For is set by Nginx, Cloudflare, Railway, Render etc.
    // Falls back to getRemoteAddr() for direct connections (local dev).
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // first IP in the chain
        }
        return request.getRemoteAddr();
    }

    // ── Helper: build a consistent 429 response ───────────────────────────
    private ResponseEntity<Map<String, String>> tooManyRequests(String message) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("message", message));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  REGISTER  —  3 per hour per IP
    // ─────────────────────────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {

        Bucket bucket = rateLimitService.registerBucket(getClientIp(httpRequest));
        if (!bucket.tryConsume(1)) {
            return tooManyRequests("Too many registration attempts. Please try again in an hour.");
        }

        try {
            return ResponseEntity.ok(authService.register(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  LOGIN  —  5 attempts per 15 minutes per IP
    // ─────────────────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {

        Bucket bucket = rateLimitService.loginBucket(getClientIp(httpRequest));
        if (!bucket.tryConsume(1)) {
            return tooManyRequests("Too many login attempts. Please wait 15 minutes and try again.");
        }

        try {
            return ResponseEntity.ok(authService.login(request));
        } catch (RuntimeException e) {
            // Don't reveal whether email or password was wrong — return the
            // same generic message either way to prevent user enumeration.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  FORGOT PASSWORD  —  3 OTP emails per hour per IP
    // ─────────────────────────────────────────────────────────────────────
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        Bucket bucket = rateLimitService.forgotPasswordBucket(getClientIp(httpRequest));
        if (!bucket.tryConsume(1)) {
            return tooManyRequests("Too many OTP requests. Please try again in an hour.");
        }

        try {
            authService.forgotPassword(request.getEmail());
            return ResponseEntity.ok(
                    Map.of("message", "If this email is registered, an OTP has been sent.")
            );
        } catch (RuntimeException e) {
            // LOG the real error so you can see it in your terminal
            log.error("forgotPassword failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(
                    Map.of("message", "If this email is registered, an OTP has been sent.")
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  RESET PASSWORD  —  10 attempts per 30 minutes per IP
    // ─────────────────────────────────────────────────────────────────────
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        Bucket bucket = rateLimitService.otpVerifyBucket(getClientIp(httpRequest));
        if (!bucket.tryConsume(1)) {
            return tooManyRequests("Too many attempts. Please request a new OTP.");
        }

        try {
            authService.resetPassword(request);
            return ResponseEntity.ok(Map.of("message", "Password reset successful. Please login."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  CHANGE PASSWORD  —  requires JWT, no extra rate limit needed
    //  (already protected by auth; user must be logged in)
    // ─────────────────────────────────────────────────────────────────────
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody ChangePasswordRequest request) {
        try {
            String email = securityUtils.getCurrentUserEmail();
            authService.changePassword(email, request);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}