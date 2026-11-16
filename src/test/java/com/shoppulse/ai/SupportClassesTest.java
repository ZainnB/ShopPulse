package com.shoppulse.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small pure helpers of the AI module: JSON extraction, rate limit, availability, summaries, rule-based text. */
class SupportClassesTest {

    private static final AiProperties.Sql SQL = new AiProperties.Sql(200, 5, "", "u", "p");

    @Test
    void extractsFirstBalancedJsonObject() {
        assertEquals("{\"a\": {\"b\": \"}\"}}",
                ModelJson.firstObject("noise {\"a\": {\"b\": \"}\"}} trailing {\"c\":1}").orElseThrow());
        assertTrue(ModelJson.firstObject("no json here").isEmpty());
        assertTrue(ModelJson.firstObject("{ unbalanced").isEmpty());
        assertTrue(ModelJson.firstObject(null).isEmpty());
        assertEquals(1, ModelJson.parse(new ObjectMapper(), "```json\n{\"x\": 1}\n```").orElseThrow().path("x").asInt());
        assertTrue(ModelJson.parse(new ObjectMapper(), "{not json}").isEmpty());
    }

    @Test
    void tokenBucketAllowsBurstThenRefills() {
        AtomicLong now = new AtomicLong();
        TokenBucket bucket = new TokenBucket(3, 60, now::get);
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
        now.addAndGet(2_100_000_000L); // 2.1 seconds at 60/min = 2 whole tokens
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
    }

    @Test
    void availabilityRequiresEnabledFlagAndRealKey() {
        AiProperties on = new AiProperties(true, SQL, new AiProperties.Briefing("x"));
        AiProperties off = new AiProperties(false, SQL, new AiProperties.Briefing("x"));
        assertFalse(new AiAvailability(on, "not-set").isLive());
        assertFalse(new AiAvailability(on, "").isLive());
        assertFalse(new AiAvailability(on, "  ").isLive());
        assertFalse(new AiAvailability(on, null).isLive());
        assertFalse(new AiAvailability(off, "sk-real").isLive());
        assertTrue(new AiAvailability(on, "sk-real").isLive());
    }

    @Test
    void summariserDescribesRows() {
        List<String> cols = List.of("category_name", "revenue");
        assertEquals("No matching data was found for that question.", RowSummarizer.summarize(cols, List.of(), null, null));
        assertEquals("Result: category name = Books, revenue = $10.00.",
                RowSummarizer.summarize(cols, List.of(List.of("Books", new BigDecimal("10"))), null, null));
        String many = RowSummarizer.summarize(cols,
                List.of(List.of("Books", new BigDecimal("120.50")), List.of("Toys", new BigDecimal("80"))),
                "category_name", "revenue");
        assertTrue(many.contains("Highest revenue: Books ($120.50)"), many);
        assertTrue(many.contains("Lowest: Toys ($80.00)"), many);
        assertTrue(many.contains("$200.50"), many);
    }

    @Test
    void ruleBasedBriefingHasExactlyFiveBullets() {
        WeekStats stats = new WeekStats("2026-11-02", "2026-11-08", new BigDecimal("1500.00"), new BigDecimal("1000.00"),
                30, 25,
                List.of(new WeekStats.CategoryChange("Books", new BigDecimal("900"), new BigDecimal("500"), new BigDecimal("80.0")),
                        new WeekStats.CategoryChange("Toys", new BigDecimal("600"), new BigDecimal("500"), new BigDecimal("20.0")),
                        new WeekStats.CategoryChange("Games", new BigDecimal("0"), new BigDecimal("100"), new BigDecimal("-100.0"))),
                List.of(new WeekStats.ProductRow("Novel", new BigDecimal("400"), 10)),
                new BigDecimal("33.3"));
        String text = RuleBasedBriefing.write(stats);
        assertEquals(5, text.lines().filter(l -> l.startsWith("- ")).count(), text);
        assertTrue(text.contains("+50.0%"), text);
        assertTrue(text.contains("Best category: Books"), text);
        assertTrue(text.contains("Weakest category: Games"), text);
        assertEquals(3, ((List<?>) stats.toJsonMap().get("categories")).size());
    }

    @Test
    void ruleBasedBriefingSurvivesAnEmptyWeek() {
        WeekStats empty = new WeekStats("2026-11-02", "2026-11-08", BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                List.of(), List.of(), null);
        assertEquals(5, RuleBasedBriefing.write(empty).lines().filter(l -> l.startsWith("- ")).count());
    }

    @Test
    void anomalyMoversAndFacts() {
        Map<String, BigDecimal> day = Map.of("Books", new BigDecimal("500"), "Toys", new BigDecimal("50"));
        Map<String, BigDecimal> baselineTotals = Map.of("Books", new BigDecimal("2800"), "Toys", new BigDecimal("1400"),
                "Games", new BigDecimal("560"));
        List<AnomalyExplainService.Mover> movers = AnomalyExplainService.movers(day, baselineTotals);
        assertEquals("Books", movers.get(0).name());
        assertEquals(0, new BigDecimal("100.00").compareTo(movers.get(0).usual()));
        assertEquals(3, movers.size());

        LocalDate date = LocalDate.of(2026, 11, 10);
        List<String> facts = AnomalyExplainService.facts(date, new BigDecimal("550"), new BigDecimal("200"),
                new BigDecimal("12"), new BigDecimal("5"), movers, List.of());
        assertTrue(facts.get(0).contains("+175.0%"), facts.get(0));
        assertTrue(facts.size() >= 5);

        String text = AnomalyExplainService.ruleBased(date, new BigDecimal("550"), new BigDecimal("200"), movers, List.of());
        assertTrue(text.contains("above"), text);
        assertTrue(text.contains("Books"), text);
        assertTrue(AnomalyExplainService.ruleBased(date, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of())
                .contains("no recorded sales"));
    }

    @Test
    void jsonConversionOfDriverTypes() {
        assertEquals("2026-11-10", ReadOnlySqlExecutor.toJson(java.sql.Date.valueOf("2026-11-10")));
        assertEquals(new BigDecimal("1.23"), ReadOnlySqlExecutor.toJson(new BigDecimal("1.23456")));
        assertEquals(5L, ReadOnlySqlExecutor.toJson(5L));
        assertTrue(ReadOnlySqlExecutor.toJson(null) == null);
        assertTrue(ReadOnlySqlExecutor.toJson(java.sql.Timestamp.valueOf("2026-11-10 10:00:00")) instanceof String);
    }
}
