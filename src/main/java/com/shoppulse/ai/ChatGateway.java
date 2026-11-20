package com.shoppulse.ai;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The one place that talks to Spring AI. Everything else sends two strings and gets one string
 * back, so a change in the Spring AI API only ever touches this class.
 */
@Component
public class ChatGateway {

    private final ObjectProvider<AnthropicChatModel> anthropic;
    private final ObjectProvider<OpenAiChatModel> openAi;
    private final AiAvailability availability;

    /**
     * Both starters are on the classpath, so each contributes its own ChatModel bean. They are
     * injected as concrete types rather than as {@code ChatModel} to avoid an ambiguous-bean
     * failure, and the active one is chosen by {@link AiAvailability}.
     */
    public ChatGateway(ObjectProvider<AnthropicChatModel> anthropic,
                       ObjectProvider<OpenAiChatModel> openAi,
                       AiAvailability availability) {
        this.anthropic = anthropic;
        this.openAi = openAi;
        this.availability = availability;
    }

    private ChatModel activeModel() {
        return switch (availability.provider()) {
            case ANTHROPIC -> anthropic.getIfAvailable();
            case OPENAI -> openAi.getIfAvailable();
            case NONE -> null;
        };
    }

    /** Raised for any model failure so callers can fall back without caring about the cause. */
    public static class ModelUnavailableException extends RuntimeException {
        public ModelUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Sends a system and a user message and returns the model's text. */
    public String call(String system, String user) {
        try {
            ChatModel model = activeModel();
            if (model == null) {
                throw new IllegalStateException("no ChatModel bean is configured");
            }
            ChatResponse response = model.call(new Prompt(List.of(new SystemMessage(system), new UserMessage(user))));
            String text = response == null || response.getResult() == null
                    ? null : response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("the model returned an empty response");
            }
            return text;
        } catch (RuntimeException e) {
            throw new ModelUnavailableException("The language model call failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
