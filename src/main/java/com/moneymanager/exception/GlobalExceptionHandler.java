package com.moneymanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EditNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleEditNotAllowed(EditNotAllowedException ex) {
        return buildError(HttpStatus.FORBIDDEN, "Edit Not Allowed", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage());
    }

    // ── BUG FIX: IllegalArgumentException was falling into the generic 500 handler ──
    //
    // THE BUG:
    //   Errors like "Insufficient balance in account", "Transaction date cannot be
    //   in the future", and "Invalid category for INCOME transaction" are all thrown
    //   as IllegalArgumentException — a client error (bad input), not a server error.
    //   Without this handler they were caught by handleGenericException() and returned
    //   as HTTP 500 Internal Server Error, which is wrong and confusing to the frontend.
    //
    // THE FIX:
    //   Add a dedicated handler that maps IllegalArgumentException → 400 Bad Request.
    //   The frontend receives a clear error code and can display the message correctly.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    // Generic catch-all — only fires for truly unexpected server-side failures
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage());
    }

    // ── Shared helper to build the error response map ───────────────────────
    private ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status, String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}