package com.moneymanager.service;

import com.moneymanager.dto.AuthRequest;
import com.moneymanager.dto.AuthResponse;
import com.moneymanager.model.User;
import com.moneymanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;    // injected from SecurityConfig
    private final JwtService jwtService;

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // encode() → BCrypt hash. NEVER store raw passwords.

        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // matches(rawPassword, encodedHash) — BCrypt handles the comparison
            throw new RuntimeException("Invalid credentials");
        }
        // Same error message for both "user not found" and "wrong password"
        // so attackers can't tell which one failed (security best practice)

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }
}