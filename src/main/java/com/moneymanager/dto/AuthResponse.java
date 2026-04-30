package com.moneymanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    // Simple wrapper — frontend only needs the token and who logged in
}