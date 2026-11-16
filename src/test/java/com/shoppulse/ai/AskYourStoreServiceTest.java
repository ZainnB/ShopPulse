package com.shoppulse.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppulse.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AskYourStoreServiceTest {

    private AiAvailability availability;
    private ChatGateway chat;
    private ReadOnlySqlExecutor executor;
    private AskYourStoreService service;

    private final QueryResult sample = new QueryResult(List.of("category_name", "revenue"),
            List.of(List.of("Books", new BigDecimal("120.50")), List.of("Toys", new BigDecimal("80.00"))));

    @BeforeEach
    void setUp() {
        availability = mock(AiAvailability.class);
        chat = mock(ChatGateway.class);
        executor = mock(ReadOnlySqlExecutor.class);
        service = new AskYourStoreService(availability, chat, new SqlGuard(200), executor, new ObjectMapper());
    }

    @ParameterizedTest
    @ValueSource(strings = {"What was my revenue this week?", "best customers", "sales by category", "low stock", "asdf qwerty", ""})
    void fallbackModeAnswersWithoutCallingTheModel(String question) {
        when(availability.isLive()).thenReturn(false);
        when(executor.execute(anyString())).thenReturn(sample);

        AskResponse r = service.ask(question);

        assertFalse(r.aiGenerated());
        assertNotNull(r.answer());
        assertTrue(r.note().contains("built-in query library"), r.note());
        assertFalse(r.rows().isEmpty());
        verify(chat, never()).call(anyString(), anyString());
    }

    @Test
    void fallbackModeNeverThrowsEvenWhenTheDatabaseFails() {
        when(availability.isLive()).thenReturn(false);
        when(executor.execute(anyString())).thenThrow(new BusinessException("The query could not be run."));

        AskResponse r = assertDoesNotThrow(() -> service.ask("revenue"));

        assertFalse(r.aiGenerated());
        assertTrue(r.answer().contains("could not load"));
        assertTrue(r.rows().isEmpty());
        assertEquals("none", r.chartType());
    }

    @Test
    void fallbackModeHandlesNullQuestion() {
        when(availability.isLive()).thenReturn(false);
        when(executor.execute(anyString())).thenReturn(sample);
        assertDoesNotThrow(() -> service.ask(null));
    }

    @Test
    void liveModeParsesJsonWrappedInProseAndSummarises() {
        when(availability.isLive()).thenReturn(true);
        when(chat.call(anyString(), anyString())).thenReturn(
                "Sure! ```json\n{\"sql\": \"SELECT category_name, SUM(revenue) AS revenue FROM v_sales_by_category GROUP BY 1;\","
                        + " \"chartType\": \"PIE\", \"labelColumn\": \"category_name\", \"valueColumn\": \"revenue\","
                        + " \"explanation\": \"x\"}\n```",
                "Books led with $120.50.");
        when(executor.execute(anyString())).thenReturn(sample);

        AskResponse r = service.ask("revenue by category");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(executor).execute(sql.capture());
        assertTrue(sql.getValue().endsWith("LIMIT 200"), sql.getValue());
        assertTrue(r.aiGenerated());
        assertEquals("pie", r.chartType());
        assertEquals("Books led with $120.50.", r.answer());
        assertEquals(sql.getValue(), r.sql());
    }

    @Test
    void liveModeFallsBackToDeterministicSummaryWhenSecondCallFails() {
        when(availability.isLive()).thenReturn(true);
        when(chat.call(anyString(), anyString()))
                .thenReturn("{\"sql\": \"SELECT category_name, revenue FROM v_sales_by_category\", \"chartType\": \"bar\"}")
                .thenThrow(new ChatGateway.ModelUnavailableException("down", null));
        when(executor.execute(anyString())).thenReturn(sample);

        AskResponse r = service.ask("revenue by category");

        assertTrue(r.aiGenerated());
        assertTrue(r.answer().startsWith("Found 2 rows"), r.answer());
        assertEquals("bar", r.chartType());
        assertEquals("category_name", r.chartLabelColumn());
        assertEquals("revenue", r.chartValueColumn());
    }

    @Test
    void unsafeModelSqlIsRejectedAndTheFallbackAnswers() {
        when(availability.isLive()).thenReturn(true);
        when(chat.call(anyString(), anyString())).thenReturn("{\"sql\": \"DROP TABLE orders\"}");
        when(executor.execute(anyString())).thenReturn(sample);

        AskResponse r = service.ask("drop everything");

        assertFalse(r.aiGenerated());
        assertTrue(r.note().contains("Query rejected"), r.note());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(executor).execute(sql.capture());
        assertFalse(sql.getValue().toUpperCase().contains("DROP"));
    }

    @Test
    void modelOutageFallsBack() {
        when(availability.isLive()).thenReturn(true);
        when(chat.call(anyString(), anyString())).thenThrow(new ChatGateway.ModelUnavailableException("down", null));
        when(executor.execute(anyString())).thenReturn(sample);

        AskResponse r = service.ask("revenue");

        assertFalse(r.aiGenerated());
        assertTrue(r.note().contains("unavailable"), r.note());
    }

    @Test
    void garbageFromTheModelFallsBack() {
        when(availability.isLive()).thenReturn(true);
        when(chat.call(anyString(), anyString())).thenReturn("I cannot help with that.");
        when(executor.execute(anyString())).thenReturn(sample);

        assertFalse(service.ask("revenue").aiGenerated());
    }

    @Test
    void chartResolutionDropsUnknownColumns() {
        var chart = AskYourStoreService.resolveChart("bar", "nope", "nada", sample);
        assertEquals("bar", chart.type());
        assertEquals("category_name", chart.label());
        assertEquals("revenue", chart.value());
        assertEquals("none", AskYourStoreService.resolveChart("scatter", "a", "b", sample).type());
        assertEquals("none", AskYourStoreService.resolveChart("bar", "a", "b", new QueryResult(List.of("a"), List.of())).type());
    }
}
