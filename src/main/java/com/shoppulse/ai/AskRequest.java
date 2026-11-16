package com.shoppulse.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A merchant's plain-English question about the store. */
public record AskRequest(@NotBlank @Size(max = 300) String question) {
}
