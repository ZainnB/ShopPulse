package com.shoppulse.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Single answer to "is a language model actually usable, and which one?", so every AI feature
 * degrades to deterministic output the same way when the app runs without credentials.
 *
 * <p>Two providers are supported. {@code anthropic} is the default when a key is present.
 * {@code openai} means any OpenAI-compatible endpoint, which is how ShopPulse reaches free
 * tiers: Groq, Google Gemini's compatibility layer, OpenRouter, Cerebras, or a local Ollama.
 * Only the base URL, key and model name differ, so no application code changes between them.
 *
 * <p>{@code shoppulse.ai.provider=auto} (the default) picks whichever provider has a key,
 * preferring Anthropic. With no key at all the app runs rule-based, which is a supported
 * mode rather than an error: the demo must work for someone who just cloned the repo.
 */
@Component
public class AiAvailability {

    private static final Logger log = LoggerFactory.getLogger(AiAvailability.class);

    static final String NO_KEY = "not-set";

    /** Which model backend the AI features should use. */
    public enum Provider {
        ANTHROPIC,
        OPENAI,
        /** No usable credentials: every feature answers from its rule-based path. */
        NONE
    }

    private final Provider provider;

    public AiAvailability(AiProperties properties,
                          @Value("${spring.ai.anthropic.api-key:not-set}") String anthropicKey,
                          @Value("${spring.ai.openai.api-key:not-set}") String openAiKey) {
        this.provider = resolve(properties.enabled(), properties.provider(), anthropicKey, openAiKey);
        log.info("AI provider resolved to {}", provider);
    }

    static Provider resolve(boolean enabled, String configured, String anthropicKey, String openAiKey) {
        if (!enabled) {
            return Provider.NONE;
        }
        boolean hasAnthropic = usable(anthropicKey);
        boolean hasOpenAi = usable(openAiKey);
        String choice = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (choice) {
            case "anthropic" -> hasAnthropic ? Provider.ANTHROPIC : Provider.NONE;
            case "openai", "groq", "gemini", "openrouter", "ollama" -> hasOpenAi ? Provider.OPENAI : Provider.NONE;
            case "none" -> Provider.NONE;
            // auto, or anything unrecognised: use whatever has a key, Anthropic first.
            default -> hasAnthropic ? Provider.ANTHROPIC : (hasOpenAi ? Provider.OPENAI : Provider.NONE);
        };
    }

    /** A key counts as supplied only if it is non-blank and not the {@code not-set} placeholder. */
    static boolean usable(String key) {
        return key != null && !key.isBlank() && !NO_KEY.equals(key.trim());
    }

    /** True only when AI is switched on and a real API key has been supplied. */
    public boolean isLive() {
        return provider != Provider.NONE;
    }

    public Provider provider() {
        return provider;
    }
}
