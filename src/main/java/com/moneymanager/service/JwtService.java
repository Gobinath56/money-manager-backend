package com.moneymanager.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")               // reads from application.properties
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expirationMs;

    // Build the signing key from our secret string
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
        // HMAC-SHA256 — symmetric key, same key signs and verifies
    }

    // Called after login — creates and returns a JWT string
    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)                          // "sub" claim = user identity
                .setIssuedAt(new Date())                    // "iat" = issued at
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs)) // "exp"
                .signWith(getSigningKey())                  // signs with HMAC-SHA256
                .compact();                                 // serializes to "xxxxx.yyyyy.zzzzz"
    }

    // Called in the filter — extracts email from an incoming token
    public String extractEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)         // parses AND verifies signature
                .getBody()
                .getSubject();                 // returns the "sub" claim we set above
    }

    // Returns true if token is valid (signature ok + not expired)
    public boolean isTokenValid(String token) {
        try {
            extractEmail(token);   // if this throws, token is bad
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}