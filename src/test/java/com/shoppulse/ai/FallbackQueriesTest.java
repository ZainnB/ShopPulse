package com.shoppulse.ai;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackQueriesTest {

    private final SqlGuard guard = new SqlGuard(200);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "What was my revenue last week?|revenue-trend",
            "revenue trend over the last 14 days|revenue-trend",
            "Show me sales by category|category-revenue",
            "Who are my best customers?|top-customers",
            "top selling products this month|top-products",
            "how many orders did we get|orders",
            "which items are low on stock|low-stock",
            "which day was the busiest|revenue-trend",
            "hello there|summary",
            "How are we doing?|summary"
    })
    void picksTheRightIntentAndProducesGuardedSql(String question, String intent) {
        FallbackQueries.Plan plan = FallbackQueries.match(question);
        assertEquals(intent, plan.intent());
        assertDoesNotThrow(() -> guard.validate(plan.sql()), plan.sql());
    }

    @Test
    void everyIntentPassesTheGuard() {
        for (String q : new String[]{"stock", "customers", "categories", "top products", "orders", "revenue", "hmm"}) {
            FallbackQueries.Plan plan = FallbackQueries.match(q);
            assertDoesNotThrow(() -> guard.validate(plan.sql()), plan.sql());
        }
    }

    @Test
    void parsesTimeWindows() {
        assertEquals(30, FallbackQueries.windowDays("revenue"));
        assertEquals(14, FallbackQueries.windowDays("last 14 days"));
        assertEquals(14, FallbackQueries.windowDays("past 2 weeks"));
        assertEquals(90, FallbackQueries.windowDays("last 3 months"));
        assertEquals(7, FallbackQueries.windowDays("this week"));
        assertEquals(365, FallbackQueries.windowDays("last 900 days"));
        assertTrue(FallbackQueries.match("revenue in the last 7 days").sql().contains("CURRENT_DATE - 7"));
    }

    @Test
    void handlesNullQuestion() {
        assertEquals("summary", FallbackQueries.match(null).intent());
    }
}
