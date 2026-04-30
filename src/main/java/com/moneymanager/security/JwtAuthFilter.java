package com.moneymanager.security;

import com.moneymanager.service.JwtService;
import com.moneymanager.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    // OncePerRequestFilter guarantees this runs exactly once per HTTP request

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain          // the remaining filter chain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Every protected request must have: "Authorization: Bearer <token>"
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);  // skip — let Security block it
            return;
        }

        String token = authHeader.substring(7);   // strip "Bearer " (7 chars)

        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtService.extractEmail(token);

        // Only set auth if not already authenticated in this request
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            userRepository.findByEmail(email).ifPresent(user -> {

                // This object tells Spring Security "this user is authenticated"
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                user.getEmail(),   // principal (who)
                                null,              // credentials (not needed after JWT validation)
                                List.of()          // authorities/roles (empty for now)
                        );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Store in SecurityContext — Spring Security reads this for every request
                SecurityContextHolder.getContext().setAuthentication(authToken);
            });
        }

        filterChain.doFilter(request, response);  // pass to next filter/controller
    }
}