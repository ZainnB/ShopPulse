package com.shoppulse.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Generates one briefing at startup if none exists yet.
 *
 * <p>Without this, a freshly seeded database has no briefing until the weekly cron fires, so the
 * dashboard's briefing card would open empty — and empty is a poor first impression of a headline
 * feature.
 *
 * <p>It listens for {@link ApplicationReadyEvent} rather than implementing {@code ApplicationRunner}
 * because the demo seeder is itself a runner. Two runners without explicit ordering are an
 * arbitrary order, and getting it wrong here means summarising an empty database. {@code
 * ApplicationReadyEvent} is published only after every runner has finished, so the data is
 * guaranteed to be there. It does nothing when a briefing already exists, and never fails
 * startup: a briefing is a nicety, not a reason to refuse to boot.
 */
@Component
public class InitialBriefingRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialBriefingRunner.class);

    private final AiBriefingRepository repository;
    private final WeeklyBriefingService briefingService;

    public InitialBriefingRunner(AiBriefingRepository repository, WeeklyBriefingService briefingService) {
        this.repository = repository;
        this.briefingService = briefingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void generateIfMissing() {
        if (repository.findFirstByOrderByWeekStartDescCreatedAtDesc().isPresent()) {
            return;
        }
        try {
            BriefingResponse created = briefingService.regenerate();
            log.info("Generated the first weekly briefing for week starting {} ({})",
                    created.weekStart(), created.aiGenerated() ? "model-written" : "rule-based");
        } catch (RuntimeException e) {
            log.warn("Could not generate an initial briefing; the dashboard will offer to create one: {}",
                    e.toString());
        }
    }
}
