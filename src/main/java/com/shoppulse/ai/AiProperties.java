package com.shoppulse.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed view of the {@code shoppulse.ai.*} settings so the AI module never reads raw strings.
 */
@ConfigurationProperties(prefix = "shoppulse.ai")
public record AiProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue Sql sql,
        @DefaultValue Briefing briefing) {

    /** Limits and credentials for the read-only SQL path used by "ask your store". */
    public record Sql(
            @DefaultValue("200") int maxRows,
            @DefaultValue("5") int timeoutSeconds,
            @DefaultValue("") String readonlyUrl,
            @DefaultValue("shoppulse_ai_ro") String readonlyUser,
            @DefaultValue("shoppulse_ai_ro") String readonlyPassword) {
    }

    /** Scheduling of the weekly briefing. */
    public record Briefing(@DefaultValue("0 0 7 * * MON") String cron) {
    }
}
