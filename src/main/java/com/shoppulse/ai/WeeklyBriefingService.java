package com.shoppulse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.time.temporal.TemporalAdjusters;

/**
 * Produces the Monday-morning briefing for the last completed Monday-to-Sunday week. The numbers
 * are always computed in SQL over the analytics views; the model only words them, and a
 * rule-based writer produces the same five bullets when no model is available.
 */
@Service
public class WeeklyBriefingService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyBriefingService.class);

    /** Value stored in {@code ai_briefings.model} when no language model wrote the text. */
    public static final String RULE_BASED = "rule-based";

    private static final String SYSTEM_PROMPT = """
            You are a retail analyst writing a weekly briefing for a small online store owner.
            You receive the week's numbers as JSON. Write exactly 5 bullets in plain English.
            Each bullet states one insight grounded in the numbers and gives one concrete suggested action.
            Cover: overall revenue and orders, average order value, the best and worst category week on week,
            the top products, and the share of this week's buyers who are returning customers. Use only the numbers provided; never invent figures.
            Respond with ONLY a JSON object: {"bullets": [{"insight": "...", "action": "..."}, ...]} with exactly 5 items.
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final AiBriefingRepository repository;
    private final AiAvailability availability;
    private final ChatGateway chat;
    private final ObjectMapper mapper;
    private final String modelName;

    public WeeklyBriefingService(NamedParameterJdbcTemplate jdbc, AiBriefingRepository repository,
                                 AiAvailability availability, ChatGateway chat, ObjectMapper mapper,
                                 @org.springframework.beans.factory.annotation.Value("${spring.ai.anthropic.chat.options.model:claude}") String modelName) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.availability = availability;
        this.chat = chat;
        this.mapper = mapper;
        this.modelName = modelName;
    }

    @Scheduled(cron = "${shoppulse.ai.briefing.cron}")
    public void generateWeekly() {
        try {
            AiBriefing b = regenerateEntity();
            log.info("Weekly briefing generated for week {} using {}", b.getWeekStart(), b.getModel());
        } catch (RuntimeException e) {
            log.error("Scheduled weekly briefing failed", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<BriefingResponse> latest() {
        return repository.findFirstByOrderByWeekStartDescCreatedAtDesc().map(BriefingResponse::from);
    }

    /** Generates and stores a fresh briefing for the last completed week; used by the dashboard button. */
    public BriefingResponse regenerate() {
        return BriefingResponse.from(regenerateEntity());
    }

    private AiBriefing regenerateEntity() {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        WeekStats stats = computeStats(weekStart);
        String content = null;
        String model = RULE_BASED;
        if (availability.isLive()) {
            try {
                content = askModel(stats);
                model = modelName;
            } catch (RuntimeException e) {
                log.warn("Briefing model call failed, using rule-based briefing: {}", e.toString());
            }
        }
        if (content == null) {
            content = RuleBasedBriefing.write(stats);
        }
        return repository.save(new AiBriefing(weekStart, content, model));
    }

    private String askModel(WeekStats stats) {
        try {
            String reply = chat.call(SYSTEM_PROMPT, mapper.writeValueAsString(stats.toJsonMap()));
            JsonNode bullets = ModelJson.parse(mapper, reply).map(n -> n.path("bullets")).orElse(null);
            if (bullets == null || !bullets.isArray() || bullets.size() != 5) {
                throw new IllegalStateException("model did not return exactly 5 bullets");
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode b : bullets) {
                String insight = b.path("insight").asText("").trim();
                String action = b.path("action").asText("").trim();
                if (insight.isEmpty()) {
                    throw new IllegalStateException("empty bullet from model");
                }
                sb.append("- ").append(insight);
                if (!action.isEmpty()) {
                    sb.append(" Suggested action: ").append(action.replaceFirst("(?i)^suggested action:\\s*", ""));
                }
                sb.append('\n');
            }
            return sb.toString().trim();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("could not serialise stats", e);
        }
    }

    // ------------------------------------------------------------------ numbers

    WeekStats computeStats(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate prevStart = weekStart.minusDays(7);
        LocalDate prevEnd = weekStart.minusDays(1);

        BigDecimal[] cur = totals(weekStart, weekEnd);
        BigDecimal[] prev = totals(prevStart, prevEnd);

        Map<String, BigDecimal> curCats = categoryRevenue(weekStart, weekEnd);
        Map<String, BigDecimal> prevCats = categoryRevenue(prevStart, prevEnd);
        List<WeekStats.CategoryChange> changes = new ArrayList<>();
        for (String name : new TreeSet<>(curCats.keySet())) {
            BigDecimal c = curCats.get(name);
            BigDecimal p = prevCats.getOrDefault(name, BigDecimal.ZERO);
            changes.add(new WeekStats.CategoryChange(name, c, p, WeekStats.percentChange(c, p)));
        }

        List<WeekStats.ProductRow> products = jdbc.query("""
                        SELECT product_name, SUM(revenue) AS revenue, SUM(units_sold) AS units
                        FROM v_product_sales_daily
                        WHERE sale_date BETWEEN :from AND :to
                        GROUP BY product_name ORDER BY revenue DESC LIMIT 3""",
                range(weekStart, weekEnd),
                (rs, i) -> new WeekStats.ProductRow(rs.getString("product_name"), rs.getBigDecimal("revenue"), rs.getLong("units")));

        // Share of this week's buyers who have ordered more than once in their lifetime.
        // This is NOT the dashboard's "repeat customer rate", which is window-scoped; see WeekStats.
        BigDecimal repeat = jdbc.query("""
                        SELECT COUNT(*) AS buyers, COUNT(*) FILTER (WHERE order_count > 1) AS repeaters
                        FROM v_customer_summary WHERE last_order_date BETWEEN :from AND :to""",
                range(weekStart, weekEnd),
                rs -> {
                    if (!rs.next() || rs.getLong("buyers") == 0) {
                        return null;
                    }
                    return BigDecimal.valueOf(rs.getLong("repeaters") * 100.0 / rs.getLong("buyers"))
                            .setScale(1, java.math.RoundingMode.HALF_UP);
                });

        return new WeekStats(weekStart.toString(), weekEnd.toString(), cur[0], prev[0], cur[1].longValue(),
                prev[1].longValue(), changes, products, repeat);
    }

    private BigDecimal[] totals(LocalDate from, LocalDate to) {
        return jdbc.query("""
                        SELECT COALESCE(SUM(revenue), 0) AS revenue, COALESCE(SUM(order_count), 0) AS orders
                        FROM v_sales_by_day WHERE sale_date BETWEEN :from AND :to""",
                range(from, to),
                rs -> {
                    rs.next();
                    return new BigDecimal[]{rs.getBigDecimal("revenue"), rs.getBigDecimal("orders")};
                });
    }

    private Map<String, BigDecimal> categoryRevenue(LocalDate from, LocalDate to) {
        Map<String, BigDecimal> out = new HashMap<>();
        jdbc.query("""
                        SELECT category_name, SUM(revenue) AS revenue FROM v_sales_by_category
                        WHERE sale_date BETWEEN :from AND :to GROUP BY category_name""",
                range(from, to),
                rs -> {
                    out.put(rs.getString("category_name"), rs.getBigDecimal("revenue"));
                });
        return out;
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("from", java.sql.Date.valueOf(from)).addValue("to", java.sql.Date.valueOf(to));
    }
}
