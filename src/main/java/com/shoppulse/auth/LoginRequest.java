package com.shoppulse.auth;

import jakarta.validation.constraints.NotBlank;

/** Credentials submitted to the login endpoint. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
