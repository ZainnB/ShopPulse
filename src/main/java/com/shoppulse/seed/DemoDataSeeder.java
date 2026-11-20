package com.shoppulse.seed;

import com.shoppulse.analytics.DailySalesRollupService;
import com.shoppulse.seed.SeedCatalog.CategorySeed;
import com.shoppulse.seed.SeedCatalog.ProductSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Populates an empty database with a convincing demo: two demo accounts, a catalogue,
 * customers and months of orders whose daily revenue has a trend, weekly seasonality,
 * a sale-day spike and deliberate anomalies for the detector to find.
 *
 * <p>Generation uses a FIXED random seed ({@code new Random(20240101L)}) so every fresh
 * start produces the same shape of data relative to today, which makes demos and
 * screenshots reproducible. Everything is inserted with JDBC batches in one transaction
 * so the whole run takes a few seconds. It only runs when seeding is enabled and the
 * products table is empty, so restarts never duplicate data.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final long RANDOM_SEED = 20240101L;
    private static final int BATCH_SIZE = 1000;

    private static final String[] FIRST_NAMES = {"Ava", "Liam", "Noah", "Mia", "Ethan", "Zoe", "Lucas", "Ivy",
            "Omar", "Priya", "Mateo", "Sofia", "Kenji", "Amara", "Hugo", "Layla", "Felix", "Nora", "Ravi", "Chloe"};
    private static final String[] LAST_NAMES = {"Carter", "Nguyen", "Okafor", "Silva", "Kowalski", "Haddad",
            "Brooks", "Tanaka", "Moreau", "Singh", "Fischer", "Reyes", "Larsen", "Mensah", "Costa", "Bennett",
            "Ivanov", "Park", "Rossi", "Walker"};

    /** Relative likelihood of an order at each hour of the day (index = hour). */
    private static final double[] HOUR_WEIGHTS = {1, 1, 0.5, 0.5, 0.5, 1, 2, 3, 4, 5, 6, 6,
            6, 6, 5, 5, 5, 6, 7, 8, 8, 7, 4, 2};
    /** Monday..Sunday demand multipliers: weekends are busier. */
    private static final double[] WEEKDAY_FACTOR = {0.92, 0.92, 0.96, 1.0, 1.1, 1.35, 1.25};

    private final SeedProperties properties;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final DailySalesRollupService rollup;
    private final TransactionTemplate tx;

    public DemoDataSeeder(SeedProperties properties, JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                          DailySalesRollupService rollup, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.rollup = rollup;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Demo data seeding disabled");
            return;
        }
        Long products = jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class);
        if (products != null && products > 0) {
            log.info("Products table already populated; skipping demo data seeding");
            return;
        }
        long started = System.currentTimeMillis();
        Summary summary = tx.execute(status -> seed());
        int rollupRows = rollup.rebuildAll();
        log.info("Seeded demo data in {} ms: {} categories, {} products, {} customers, {} orders "
                        + "({} line items), daily_sales rows {}, orders from {} to {}",
                System.currentTimeMillis() - started, summary.categories, summary.products, summary.customers,
                summary.orders, summary.items, rollupRows, summary.firstDay, summary.lastDay);
    }

    private record Summary(int categories, int products, int customers, int orders, int items,
                           LocalDate firstDay, LocalDate lastDay) {
    }

    private record Customer(long id, LocalDate signedUp, double weight) {
    }

    private record PendingOrder(int customerIdx, Instant createdAt, String status, int[] productIdx, int[] qty) {
    }

    private Summary seed() {
        Random rnd = new Random(RANDOM_SEED);
        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(zone);
        int months = Math.max(1, properties.monthsOfHistory());
        LocalDate start = today.minusMonths(months);
        int totalDays = (int) ChronoUnit.DAYS.between(start, today) + 1;

        // ---- catalogue ---------------------------------------------------------------
        List<CategorySeed> categories = SeedCatalog.categories();
        List<Long> categoryIds = new ArrayList<>();
        for (CategorySeed c : categories) {
            categoryIds.add(jdbc.queryForObject("""
                    INSERT INTO categories (name, slug) VALUES (?, ?)
                    ON CONFLICT (name) DO UPDATE SET slug = EXCLUDED.slug
                    RETURNING id""", Long.class, c.name(), c.slug()));
        }

        List<ProductSeed> productSeeds = new ArrayList<>();
        List<Integer> productCategory = new ArrayList<>();
        for (int ci = 0; ci < categories.size(); ci++) {
            for (ProductSeed p : categories.get(ci).products()) {
                productSeeds.add(p);
                productCategory.add(ci);
            }
        }
        int productCount = productSeeds.size();
        // A handful of products are nearly sold out so the low-stock panel has content.
        Set<Integer> lowStock = new HashSet<>();
        while (lowStock.size() < 6) {
            lowStock.add(rnd.nextInt(productCount));
        }
        Timestamp catalogueSince = Timestamp.from(start.minusDays(7).atStartOfDay(zone).toInstant());
        List<Object[]> productRows = new ArrayList<>();
        for (int i = 0; i < productCount; i++) {
            ProductSeed p = productSeeds.get(i);
            int stock = lowStock.contains(i) ? rnd.nextInt(10) : 30 + rnd.nextInt(190);
            productRows.add(new Object[]{p.name(), p.description(), p.price(), stock,
                    categoryIds.get(productCategory.get(i)), true, 0L, catalogueSince});
        }
        batch("""
                INSERT INTO products (name, description, price, stock, category_id, active, version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", productRows);
        List<Long> productIds = jdbc.queryForList("SELECT id FROM products ORDER BY id", Long.class);

        // ---- users -------------------------------------------------------------------
        // One hash per password: BCrypt is deliberately slow, hashing 120 times would take seconds.
        String merchantHash = passwordEncoder.encode("merchant123");
        String customerHash = passwordEncoder.encode("customer123");
        Timestamp merchantSince = Timestamp.from(start.minusDays(90).atStartOfDay(zone).toInstant());
        jdbc.update("""
                INSERT INTO users (email, password_hash, display_name, role, created_at)
                VALUES (?, ?, ?, 'MERCHANT', ?) ON CONFLICT (email) DO NOTHING""",
                "merchant@shoppulse.dev", merchantHash, "Demo Merchant", merchantSince);

        List<String> emails = new ArrayList<>();
        List<LocalDate> signups = new ArrayList<>();
        List<Object[]> userRows = new ArrayList<>();
        emails.add("customer@shoppulse.dev");
        signups.add(start.minusDays(30));
        userRows.add(new Object[]{"customer@shoppulse.dev", customerHash, "Demo Customer",
                Timestamp.from(start.minusDays(30).atStartOfDay(zone).toInstant())});
        int generated = Math.max(0, properties.customers());
        for (int i = 0; i < generated; i++) {
            String first = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)];
            String last = LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
            String email = (first + "." + last + "." + (i + 1)).toLowerCase() + "@example.com";
            // Customers join over time (some before the history starts, most during the first ~70%).
            LocalDate signup = start.minusDays(45).plusDays(rnd.nextInt((int) (45 + totalDays * 0.7)));
            Instant signupAt = signup.atTime(8 + rnd.nextInt(14), rnd.nextInt(60)).atZone(zone).toInstant();
            if (signupAt.isAfter(now)) {
                signupAt = now;
            }
            emails.add(email);
            signups.add(signup);
            userRows.add(new Object[]{email, customerHash, first + " " + last, Timestamp.from(signupAt)});
        }
        batch("""
                INSERT INTO users (email, password_hash, display_name, role, created_at)
                VALUES (?, ?, ?, 'CUSTOMER', ?) ON CONFLICT (email) DO NOTHING""", userRows);
        Map<String, Long> idByEmail = new HashMap<>();
        jdbc.query("SELECT id, email FROM users", (RowCallbackHandler) rs -> {
            idByEmail.put(rs.getString("email"), rs.getLong("id"));
        });

        List<Customer> customers = new ArrayList<>();
        double[] customerCumulative = new double[emails.size()];
        double running = 0;
        for (int i = 0; i < emails.size(); i++) {
            // Heavy-tailed loyalty: a few customers order far more often than the rest.
            double weight = 0.1 + Math.pow(rnd.nextDouble(), 4) * 5;
            running += weight;
            customerCumulative[i] = running;
            customers.add(new Customer(idByEmail.get(emails.get(i)), signups.get(i), weight));
        }

        // ---- demand model ------------------------------------------------------------
        double[] productCumulative = new double[productCount];
        double pr = 0;
        for (int i = 0; i < productCount; i++) {
            double base = Math.exp(0.9 * rnd.nextGaussian());
            double price = productSeeds.get(i).price().doubleValue();
            pr += base / (1.0 + price / 120.0); // cheaper items sell more often
            productCumulative[i] = pr;
        }
        double[] hourCumulative = new double[24];
        double hr = 0;
        for (int h = 0; h < 24; h++) {
            hr += HOUR_WEIGHTS[h];
            hourCumulative[h] = hr;
        }

        int flashIdx = specialSaleIndex(start, today, totalDays);
        int dropIdx = (int) (totalDays * 0.55);   // simulated checkout outage: revenue collapses
        int spikeIdx = (int) (totalDays * 0.80);  // simulated viral mention: revenue surges
        // Two more anomalies inside the last 30 days. The dashboard opens on a 30-day window,
        // and anomaly detection is the headline feature: if the only planted anomalies were
        // months back, a reviewer's first screen would read "no anomalies detected".
        // Placement here is deliberate and is a property of the detector, not a guess.
        // A z-score is measured against the mean and standard deviation of the trailing 28 days,
        // so one extreme day inside that window inflates sigma and can mask a later anomaly.
        // The outage is therefore placed far enough after the sale day that the sale has left
        // its trailing window, which keeps its z-score honest.
        int recentDropIdx = Math.max(0, totalDays - 6);
        int recentSpikeIdx = Math.max(0, totalDays - 1);

        // ---- orders ------------------------------------------------------------------
        List<PendingOrder> pending = new ArrayList<>();
        for (int d = 0; d < totalDays; d++) {
            LocalDate day = start.plusDays(d);
            double t = totalDays > 1 ? (double) d / (totalDays - 1) : 1.0;
            double mean = 14 * (1 + 0.7 * t) * WEEKDAY_FACTOR[day.getDayOfWeek().getValue() - 1];
            if (d == flashIdx) {
                mean *= 3.2;
            } else if (d == flashIdx + 1) {
                mean *= 1.7;
            }
            int orders;
            if (d == dropIdx || d == recentDropIdx) {
                orders = rnd.nextInt(2);
            } else {
                if (d == spikeIdx || d == recentSpikeIdx) {
                    mean *= 4.5;
                }
                orders = (int) Math.max(0, Math.round(mean + Math.sqrt(mean) * 0.9 * rnd.nextGaussian()));
            }
            for (int o = 0; o < orders; o++) {
                int customerIdx = pickCustomer(rnd, customers, customerCumulative, day);
                Instant at = day.atTime(sampleHour(rnd, hourCumulative), rnd.nextInt(60), rnd.nextInt(60))
                        .atZone(zone).toInstant();
                if (at.isAfter(now)) {
                    at = now.minusSeconds(60L + rnd.nextInt(3 * 3600));
                }
                int lines = lineCount(rnd);
                int[] productIdx = new int[lines];
                int[] qty = new int[lines];
                for (int l = 0; l < lines; l++) {
                    int p;
                    int attempts = 0;
                    do {
                        p = sample(rnd, productCumulative);
                    } while (contains(productIdx, l, p) && ++attempts < 8);
                    productIdx[l] = p;
                    qty[l] = quantity(rnd);
                }
                long ageDays = ChronoUnit.DAYS.between(day, today);
                pending.add(new PendingOrder(customerIdx, at, status(rnd, ageDays), productIdx, qty));
            }
        }

        List<Long> orderIds = jdbc.queryForList(
                "SELECT nextval(pg_get_serial_sequence('orders', 'id')) FROM generate_series(1, ?)",
                Long.class, pending.size());
        List<Object[]> orderRows = new ArrayList<>(pending.size());
        List<Object[]> itemRows = new ArrayList<>(pending.size() * 2);
        for (int i = 0; i < pending.size(); i++) {
            PendingOrder po = pending.get(i);
            long orderId = orderIds.get(i);
            BigDecimal total = BigDecimal.ZERO;
            for (int l = 0; l < po.productIdx.length; l++) {
                BigDecimal price = productSeeds.get(po.productIdx[l]).price();
                total = total.add(price.multiply(BigDecimal.valueOf(po.qty[l])));
                itemRows.add(new Object[]{orderId, productIds.get(po.productIdx[l]), po.qty[l], price});
            }
            orderRows.add(new Object[]{orderId, customers.get(po.customerIdx).id(), po.status, total,
                    Timestamp.from(po.createdAt)});
        }
        batch("INSERT INTO orders (id, user_id, status, total, created_at) VALUES (?, ?, ?, ?, ?)", orderRows);
        batch("INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)", itemRows);

        return new Summary(categories.size(), productCount, customers.size(), pending.size(), itemRows.size(),
                start, today);
    }

    /**
     * Index of the day of the big sale. Uses the most recent Black Friday inside the
     * history; if none falls in range (as when seeding mid-November) a "flash sale" a third
     * of the way through the history stands in so the chart always has a promotional peak.
     */
    private static int specialSaleIndex(LocalDate start, LocalDate today, int totalDays) {
        LocalDate best = null;
        for (int year = today.getYear(); year >= today.getYear() - 1; year--) {
            LocalDate thanksgiving = LocalDate.of(year, 11, 1)
                    .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
            LocalDate blackFriday = thanksgiving.plusDays(1);
            if (!blackFriday.isBefore(start) && blackFriday.isBefore(today)) {
                best = blackFriday;
                break;
            }
        }
        if (best != null) {
            return (int) ChronoUnit.DAYS.between(start, best);
        }
        return (int) (totalDays * 0.33);
    }

    private static int pickCustomer(Random rnd, List<Customer> customers, double[] cumulative, LocalDate day) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int idx = sample(rnd, cumulative);
            if (!customers.get(idx).signedUp().isAfter(day)) {
                return idx;
            }
        }
        return 0; // the demo customer signed up before the history began
    }

    private static int sample(Random rnd, double[] cumulative) {
        double x = rnd.nextDouble() * cumulative[cumulative.length - 1];
        int idx = Arrays.binarySearch(cumulative, x);
        if (idx < 0) {
            idx = -idx - 1;
        }
        return Math.min(idx, cumulative.length - 1);
    }

    private static int sampleHour(Random rnd, double[] hourCumulative) {
        return sample(rnd, hourCumulative);
    }

    private static boolean contains(int[] arr, int upTo, int value) {
        for (int i = 0; i < upTo; i++) {
            if (arr[i] == value) {
                return true;
            }
        }
        return false;
    }

    private static int lineCount(Random rnd) {
        double r = rnd.nextDouble();
        return r < 0.55 ? 1 : r < 0.85 ? 2 : 3;
    }

    private static int quantity(Random rnd) {
        double r = rnd.nextDouble();
        return r < 0.72 ? 1 : r < 0.92 ? 2 : 3;
    }

    /** Older orders have progressed further through fulfilment; some are cancelled or unpaid. */
    private static String status(Random rnd, long ageDays) {
        double r = rnd.nextDouble();
        if (ageDays >= 7) {
            return r < 0.86 ? "DELIVERED" : r < 0.94 ? "CANCELLED" : "SHIPPED";
        }
        if (ageDays >= 2) {
            return r < 0.45 ? "SHIPPED" : r < 0.75 ? "DELIVERED" : r < 0.90 ? "PAID" : r < 0.96 ? "CANCELLED" : "PENDING";
        }
        return r < 0.55 ? "PAID" : r < 0.70 ? "SHIPPED" : r < 0.90 ? "PENDING" : r < 0.97 ? "CANCELLED" : "DELIVERED";
    }

    private void batch(String sql, List<Object[]> rows) {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            jdbc.batchUpdate(sql, rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE)));
        }
    }
}
