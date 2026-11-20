package com.shoppulse.common;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;

/**
 * Accepts a Postgres connection string in either form and normalises it for Spring.
 *
 * <p>Hosted Postgres providers (Neon, Supabase, Railway, Heroku) hand out a single URI like
 * {@code postgresql://user:pass@host/db?sslmode=require}, while Spring's datasource wants a
 * {@code jdbc:postgresql://...} URL with the username and password supplied separately.
 * Pasting the provider's string straight into {@code DATABASE_URL} is the single most common
 * way a first deployment fails, so the app converts it instead of refusing it.
 *
 * <p>A value that already starts with {@code jdbc:} is passed through untouched.
 */
public final class DatabaseUrl {

    private DatabaseUrl() {
    }

    /** The three values Spring needs. Username and password are null when the URI had none. */
    public record Parsed(String jdbcUrl, String username, String password) {
    }

    /** True for a provider-style URI that needs converting. */
    public static boolean isUri(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim();
        return u.startsWith("postgresql://") || u.startsWith("postgres://");
    }

    /**
     * Converts {@code postgresql://user:pass@host:port/db?params} into its JDBC equivalent,
     * keeping any query parameters (such as {@code sslmode=require}, which Neon requires).
     */
    public static Parsed parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL is blank");
        }
        String trimmed = url.trim();
        if (!isUri(trimmed)) {
            return new Parsed(trimmed, null, null);
        }
        URI uri = URI.create(trimmed);
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("DATABASE_URL has no host: " + redact(trimmed));
        }
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host);
        if (uri.getPort() != -1) {
            jdbc.append(':').append(uri.getPort());
        }
        String path = uri.getPath();
        jdbc.append(path == null || path.isBlank() ? "/" : path);
        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            jdbc.append('?').append(uri.getRawQuery());
        }

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int colon = userInfo.indexOf(':');
            if (colon < 0) {
                username = decode(userInfo);
            } else {
                username = decode(userInfo.substring(0, colon));
                password = decode(userInfo.substring(colon + 1));
            }
        }
        return new Parsed(jdbc.toString(), username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** Never log a connection string with its password in it. */
    public static String redact(String url) {
        return url == null ? "null" : url.replaceAll("://[^@/]*@", "://****@");
    }
}
