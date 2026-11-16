package com.shoppulse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Models wrap JSON in prose and code fences; this finds the first balanced object and parses it,
 * so one chatty answer does not become a failure.
 */
final class ModelJson {

    private ModelJson() {
    }

    /** Returns the first balanced {...} block in the text, ignoring braces inside strings. */
    static Optional<String> firstObject(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int start = text.indexOf('{');
        while (start >= 0) {
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                } else if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}' && --depth == 0) {
                    return Optional.of(text.substring(start, i + 1));
                }
            }
            start = text.indexOf('{', start + 1);
        }
        return Optional.empty();
    }

    /** Parses the first balanced object, or returns empty if there is none or it is not valid JSON. */
    static Optional<JsonNode> parse(ObjectMapper mapper, String text) {
        Optional<String> raw = firstObject(text);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode node = mapper.readTree(raw.get());
            return node != null && node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
