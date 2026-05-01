package com.moneymanager.util;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    /**
     * Returns the email of the currently authenticated user.
     * This reads from the SecurityContext — populated by JwtAuthFilter
     * on every request after token validation.
     *
     * SecurityContextHolder stores data per-thread, so it's always
     * scoped to the current HTTP request. Thread-safe by design.
     */
    public String getCurrentUserEmail() {
        return SecurityContextHolder
                .getContext()           // gets this thread's security context
                .getAuthentication()    // the auth object set by JwtAuthFilter
                .getPrincipal()         // the "principal" = email string we stored
                .toString();
    }
}