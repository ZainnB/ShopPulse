package com.shoppulse.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single answer to "is a language model actually usable?", so every AI feature degrades to
 * deterministic output the same way when the app runs without credentials.
 */
@Component
public class AiAvailability {

    static final String NO_KEY = "not-set";

    private final boolean enabled;
    private final String apiKey;

    public AiAvailability(AiProperties properties,
                          @Value("${spring.ai.anthropic.api-key:not-set}") String apiKey) {
        this.enabled = properties.enabled();
        this.apiKey = apiKey;
    }

    /** True only when AI is switched on and a real API key has been supplied. */
    public boolean isLive() {
        return enabled && apiKey != null && !apiKey.isBlank() && !NO_KEY.equals(apiKey.trim());
    }
}
