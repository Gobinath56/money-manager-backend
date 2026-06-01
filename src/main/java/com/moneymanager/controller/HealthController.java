package com.moneymanager.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * FIX #13 — keepAlive.js was hitting /api/auth/forgot-password every 14 min,
 * which created a ghost PasswordResetToken in MongoDB for every logged-in user
 * on every ping. Over a day with 10 users that's 1,000+ wasted documents.
 *
 * This endpoint:
 *   - Is publicly accessible (no JWT required — see SecurityConfig permitAll below)
 *   - Does zero database work
 *   - Returns a tiny JSON payload so the frontend can confirm the server is up
 *
 * Add to SecurityConfig.authorizeHttpRequests:
 *   .requestMatchers("/api/health").permitAll()
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}