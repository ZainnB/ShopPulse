package com.shoppulse.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for {@code shoppulse.jwt.*} so the signing secret and lifetime are
 * configured in one place instead of scattered {@code @Value} lookups.
 */
@ConfigurationProperties(prefix = "shoppulse.jwt")
public record JwtProperties(String secret, long expiryMinutes) {
}
