package com.shoppulse.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed binding for {@code shoppulse.seed.*} so the demo-data switches are validated
 * and discoverable instead of scattered {@code @Value} strings.
 */
@ConfigurationProperties(prefix = "shoppulse.seed")
public record SeedProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("6") int monthsOfHistory,
        @DefaultValue("120") int customers) {
}
