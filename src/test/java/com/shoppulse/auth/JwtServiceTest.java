package com.shoppulse.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long";

    private static User user() {
        return new User("jane@example.com", "hash", "Jane", Role.MERCHANT);
    }

    @Test
    void roundTripsToken() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));
        String token = service.generateToken(user());

        assertTrue(service.isValid(token));
        assertEquals("jane@example.com", service.extractEmail(token));
        assertEquals(3600, service.getExpirySeconds());
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));
        String token = service.generateToken(user());
        String[] parts = token.split("\\.");
        int mid = parts[1].length() / 2;
        char replacement = parts[1].charAt(mid) == 'A' ? 'B' : 'A';
        String tamperedPayload = parts[1].substring(0, mid) + replacement + parts[1].substring(mid + 1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertFalse(service.isValid(tampered));
        assertFalse(service.isValid("not-a-jwt"));
        assertFalse(service.isValid(""));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService(new JwtProperties("another-secret-that-is-also-32-bytes-long!!", 60));
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));

        assertFalse(service.isValid(other.generateToken(user())));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = new JwtService(new JwtProperties(SECRET, -5));

        assertFalse(service.isValid(service.generateToken(user())));
    }

    @Test
    void rejectsTooShortSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties("short", 60)));
        assertTrue(ex.getMessage().contains("32"));
    }
}
