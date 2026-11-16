package com.shoppulse.ai;

import com.shoppulse.common.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface of the AI module for merchants. Thin on purpose: validation and rate limiting here,
 * everything else in the services.
 */
@RestController
@RequestMapping("/api/v1/merchant/ai")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant AI", description = "Ask your store, weekly briefing and anomaly explanations")
public class MerchantAiController {

    private final AskYourStoreService askService;
    private final WeeklyBriefingService briefingService;
    private final AnomalyExplainService anomalyService;
    /** About 20 questions a minute per instance so a demo cannot burn an API budget. */
    private final TokenBucket askBucket = new TokenBucket(20, 20);

    public MerchantAiController(AskYourStoreService askService, WeeklyBriefingService briefingService,
                                AnomalyExplainService anomalyService) {
        this.askService = askService;
        this.briefingService = briefingService;
        this.anomalyService = anomalyService;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a plain-English question about the store; answered by AI or the built-in query library")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        if (!askBucket.tryAcquire()) {
            throw new RateLimitExceededException("Too many questions in a short time. Please wait a few seconds and try again.");
        }
        return askService.ask(request.question());
    }

    @GetMapping("/briefing/latest")
    @Operation(summary = "The most recent weekly briefing (404 if none has been generated yet)")
    public BriefingResponse latestBriefing() {
        return briefingService.latest()
                .orElseThrow(() -> new NotFoundException("No weekly briefing has been generated yet"));
    }

    @PostMapping("/briefing/regenerate")
    @Operation(summary = "Generate a fresh weekly briefing now")
    public BriefingResponse regenerateBriefing() {
        return briefingService.regenerate();
    }

    @PostMapping("/anomalies/explain")
    @Operation(summary = "Explain why a given day's sales were unusual, with the supporting numbers")
    public AnomalyExplanation explainAnomaly(@Valid @RequestBody ExplainAnomalyRequest request) {
        return anomalyService.explain(request.date());
    }
}
