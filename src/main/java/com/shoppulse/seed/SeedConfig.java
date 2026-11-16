package com.shoppulse.seed;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link SeedProperties}; kept here so the seed package is self-contained. */
@Configuration
@EnableConfigurationProperties(SeedProperties.class)
public class SeedConfig {
}
