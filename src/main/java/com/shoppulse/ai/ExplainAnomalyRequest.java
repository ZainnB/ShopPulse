package com.shoppulse.ai;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** The day a merchant wants explained. */
public record ExplainAnomalyRequest(@NotNull LocalDate date) {
}
