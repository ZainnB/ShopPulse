package com.shoppulse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests over a real PostgreSQL: Flyway migrations, the analytics views, the JWT
 * filter chain and the full HTTP stack. These are the tests that would catch a schema drift
 * or a broken security rule, which no amount of mocking can.
 */
class ShopPulseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper mapper;

    Long productId;

    @BeforeEach
    void seedFixtures() {
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM cart_items");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM daily_sales");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM users");

        jdbc.update("INSERT INTO users (email, password_hash, display_name, role) VALUES (?, ?, ?, ?)",
                "merchant@test.dev", passwordEncoder.encode("merchant123"), "Test Merchant", "MERCHANT");
        Long categoryId = jdbc.queryForObject(
                "INSERT INTO categories (name, slug) VALUES ('Audio', 'audio') RETURNING id", Long.class);
        productId = jdbc.queryForObject("""
                INSERT INTO products (name, description, price, stock, category_id, active)
                VALUES ('Studio Headphones', 'Closed-back monitors', 199.99, 3, ?, true) RETURNING id
                """, Long.class, categoryId);
    }

    // ---------------------------------------------------------------- migrations

    @Test
    @DisplayName("Flyway creates all five analytics views and they are queryable")
    void analyticsViewsExist() {
        for (String view : new String[]{"v_sales_by_day", "v_sales_by_category",
                "v_product_sales_daily", "v_top_products", "v_customer_summary"}) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM pg_views WHERE viewname = ?",
                    Integer.class, view);
            assertThat(count).as("view %s exists", view).isEqualTo(1);
            jdbc.queryForList("SELECT * FROM " + view + " LIMIT 1");
        }
    }

    // ---------------------------------------------------------------- auth

    @Test
    @DisplayName("register then login returns a usable token, and /me reflects the caller")
    void registerLoginAndMe() throws Exception {
        String token = registerCustomer("shopper@test.dev", "customer123");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("shopper@test.dev"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("a bad password is rejected with a problem detail, not a stack trace")
    void badCredentialsReturnProblemDetail() throws Exception {
        registerCustomer("shopper2@test.dev", "customer123");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"shopper2@test.dev","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").exists());
    }

    // ---------------------------------------------------------------- security rules

    @Test
    @DisplayName("merchant analytics reject anonymous callers and plain customers")
    void merchantEndpointsAreLockedDown() throws Exception {
        mvc.perform(get("/api/v1/merchant/analytics/kpis"))
                .andExpect(status().isUnauthorized());

        String customerToken = registerCustomer("nosy@test.dev", "customer123");
        mvc.perform(get("/api/v1/merchant/analytics/kpis")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a merchant can read KPIs, the series and the anomaly list")
    void merchantCanReadDashboard() throws Exception {
        String merchantToken = login("merchant@test.dev", "merchant123");

        mvc.perform(get("/api/v1/merchant/analytics/kpis").header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue.unit").value("currency"))
                .andExpect(jsonPath("$.repeatCustomerRate.unit").value("percent"));

        mvc.perform(get("/api/v1/merchant/analytics/series").header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mvc.perform(get("/api/v1/merchant/analytics/anomalies").header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("the storefront catalogue is readable without a token")
    void catalogueIsPublic() throws Exception {
        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Studio Headphones"));
    }

    // ---------------------------------------------------------------- the money path

    @Test
    @DisplayName("cart to checkout: stock drops, the order is PAID, and the rollup is refreshed")
    void checkoutEndToEnd() throws Exception {
        String token = registerCustomer("buyer@test.dev", "customer123");

        mvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(2));

        mvc.perform(post("/api/v1/orders/checkout").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.total").value(399.98))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        Integer stock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(stock).as("two of three units were sold").isEqualTo(1);

        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PAID"));

        // The OrderPlacedEvent listener refreshes daily_sales after commit.
        BigDecimal revenue = jdbc.queryForObject(
                "SELECT coalesce(sum(revenue), 0) FROM daily_sales", BigDecimal.class);
        assertThat(revenue).as("rollup refreshed by the after-commit listener")
                .isEqualByComparingTo("399.98");

        // The analytics view agrees with the rollup.
        BigDecimal viewRevenue = jdbc.queryForObject(
                "SELECT coalesce(sum(revenue), 0) FROM v_sales_by_day", BigDecimal.class);
        assertThat(viewRevenue).isEqualByComparingTo("399.98");
    }

    @Test
    @DisplayName("buying more than the stock on hand is refused with 409, not an oversell")
    void cannotOversell() throws Exception {
        String token = registerCustomer("greedy@test.dev", "customer123");

        mvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":99}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        Integer stock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(stock).as("stock untouched by the refused request").isEqualTo(3);
    }

    // ---------------------------------------------------------------- AI, fallback mode

    @Test
    @DisplayName("ask-your-store answers from the built-in library when no model key is set")
    void askYourStoreFallsBackWithoutAnApiKey() throws Exception {
        String merchantToken = login("merchant@test.dev", "merchant123");

        mvc.perform(post("/api/v1/merchant/ai/ask")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"How did revenue trend over the last 30 days?"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.sql").isNotEmpty())
                .andExpect(jsonPath("$.note").isNotEmpty());
    }

    @Test
    @DisplayName("the weekly briefing can be generated and read back without a model")
    void briefingRegenerateAndRead() throws Exception {
        String merchantToken = login("merchant@test.dev", "merchant123");

        mvc.perform(post("/api/v1/merchant/ai/briefing/regenerate")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.aiGenerated").value(false));

        mvc.perform(get("/api/v1/merchant/ai/briefing/latest")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").isNotEmpty());
    }

    // ---------------------------------------------------------------- helpers

    private String registerCustomer(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Test Shopper"}"""
                                .formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn();
        return readToken(result);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return readToken(result);
    }

    private String readToken(MvcResult result) throws Exception {
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        String token = body.path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
