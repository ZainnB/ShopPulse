package com.shoppulse.auth;

/** Token plus just enough profile for the UI to render without a second call. */
public record AuthResponse(String token, String tokenType, long expiresInSeconds,
                           String email, String displayName, String role) {
}
