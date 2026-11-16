package com.shoppulse.ai;

import java.time.Instant;
import java.time.LocalDate;

/** API view of a stored weekly briefing. */
public record BriefingResponse(Long id, LocalDate weekStart, String content, String model,
                               Instant createdAt, boolean aiGenerated) {

    static BriefingResponse from(AiBriefing b) {
        return new BriefingResponse(b.getId(), b.getWeekStart(), b.getContent(), b.getModel(),
                b.getCreatedAt(), !WeeklyBriefingService.RULE_BASED.equals(b.getModel()));
    }
}
