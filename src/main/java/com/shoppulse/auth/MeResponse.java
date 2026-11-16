package com.shoppulse.auth;

/** The caller's own profile; never includes the password hash. */
public record MeResponse(Long id, String email, String displayName, String role) {
}
