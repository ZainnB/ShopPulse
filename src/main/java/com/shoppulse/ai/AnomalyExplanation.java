package com.shoppulse.ai;

import java.time.LocalDate;
import java.util.List;

/** An explanation plus the numbers it was grounded in, so the UI can show the evidence. */
public record AnomalyExplanation(LocalDate date, String explanation, List<String> supportingFacts,
                                 boolean aiGenerated) {
}
