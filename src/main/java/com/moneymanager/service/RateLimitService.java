// src/main/java/com/moneymanager/service/RateLimitService.java
package com.moneymanager.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    // One bucket per IP address — stored in memory
    // ConcurrentHashMap is thread-safe for concurrent requests
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    // ── Login: 5 attempts per 15 minutes per IP ──────────────────────────
    // Stops brute-force password attacks.
    // After 5 wrong tries the attacker must wait 15 minutes.
    public Bucket loginBucket(String ip) {
        return buckets.computeIfAbsent(ip + ":login", k -> {
            Bandwidth limit = Bandwidth.classic(
                    5,                                  // 5 tokens total
                    Refill.intervally(5, Duration.ofMinutes(15))  // refill all 5 every 15 min
            );
            return Bucket.builder().addLimit(limit).build();
        });
    }

    // ── Register: 3 accounts per hour per IP ────────────────────────────
    // Stops bulk account creation / spam.
    public Bucket registerBucket(String ip) {
        return buckets.computeIfAbsent(ip + ":register", k -> {
            Bandwidth limit = Bandwidth.classic(
                    3,
                    Refill.intervally(3, Duration.ofHours(1))
            );
            return Bucket.builder().addLimit(limit).build();
        });
    }

    // ── Forgot password: 3 OTP emails per hour per IP ───────────────────
    // Stops email bombing — each OTP request sends a real email.
    public Bucket forgotPasswordBucket(String ip) {
        return buckets.computeIfAbsent(ip + ":forgot", k -> {
            Bandwidth limit = Bandwidth.classic(
                    3,
                    Refill.intervally(20, Duration.ofHours(1))
            );
            return Bucket.builder().addLimit(limit).build();
        });
    }

    // ── OTP verify: 10 attempts per 30 minutes per IP ───────────────────
    // Stops OTP brute-forcing (10000 possible 6-digit codes).
    public Bucket otpVerifyBucket(String ip) {
        return buckets.computeIfAbsent(ip + ":otp", k -> {
            Bandwidth limit = Bandwidth.classic(
                    10,
                    Refill.intervally(10, Duration.ofMinutes(30))
            );
            return Bucket.builder().addLimit(limit).build();
        });
    }
}