# ShopPulse: Spring Boot E-commerce with an AI Merchant Dashboard

## 1. The idea, sharpened

A generic "e-commerce clone" is the most common Spring project on CVs. Reviewers skim past it.

**Better pitch:** a small e-commerce backend where the *merchant side* is the product. The storefront is deliberately thin; the depth goes into analytics, AI insights, and clean backend engineering.

What makes it stand out:

- **"Ask your store"**: the merchant types a question ("which category dropped most this month?") and gets an answer plus a chart. The LLM writes SQL against a read-only analytics schema.
- **AI weekly briefing**: a scheduled job summarises the week's numbers into 5 plain-English bullets with suggested actions.
- **Anomaly flags**: simple statistics (z-score on daily revenue/orders) flag unusual days; the LLM explains the likely cause from the data.
- **Realistic seed data**: a generator creates ~6 months of orders with seasonality, so the dashboards look real in a demo.
- **Phase 2: AI recommendations** using embeddings in pgvector plus co-purchase data.

One-line CV description: *"E-commerce platform with an AI-powered merchant analytics dashboard (Spring Boot, PostgreSQL, Spring AI)."*

## 2. Scope

| In (Phase 1) | Out (don't build) |
|---|---|
| Product catalogue, cart, checkout, orders | Real payments (mock a payment step) |
| JWT auth with CUSTOMER / MERCHANT roles | Multi-vendor marketplace |
| Merchant dashboard: KPIs, charts, AI features | Shipping, taxes, coupons |
| Seed data generator | Microservices (use a modular monolith) |
| Tests, Docker, CI, deployed demo | Fancy storefront UI |

## 3. Tech stack

- **Core:** Java 25, latest stable Spring Boot, Maven
- **Web/API:** Spring Web (REST), springdoc-openapi (Swagger UI), Bean Validation
- **Security:** Spring Security, JWT, role-based access
- **Data:** Spring Data JPA, PostgreSQL, Flyway migrations
- **AI:** Spring AI (the release matching your Boot version) with any chat model; pgvector in Phase 2
- **Caching/async:** Spring Cache (Caffeine) for dashboard queries, `@Scheduled` for the briefing, Spring events for order-placed side effects
- **UI:** Thymeleaf + HTMX + Chart.js. One deployable, keeps the focus on Java. (Swap for React only if you want frontend on the CV too.)
- **Quality:** JUnit 5, Mockito, Testcontainers (real Postgres in tests), GitHub Actions
- **Run:** Docker Compose (app + Postgres), deploy to Render/Railway/Fly

## 4. Architecture

Modular monolith, package-by-feature:

```
com.shoppulse
├── auth          // users, JWT, roles
├── catalog       // products, categories, inventory
├── cart
├── order         // checkout, order lifecycle, OrderPlacedEvent
├── analytics     // KPI queries, daily aggregates, anomaly detection
├── ai            // ask-your-store, weekly briefing, (phase 2) recommendations
├── seed          // demo data generator
└── common        // error handling, config
```

Each module: `controller → service → repository`, DTOs at the edge (records), entities never leave the service layer. Global `@RestControllerAdvice` returning RFC 7807 problem details.

## 5. Data model

- `users` (id, email, password_hash, role)
- `categories` (id, name)
- `products` (id, name, description, price, stock, category_id, created_at)
- `carts`, `cart_items`
- `orders` (id, user_id, status, total, created_at)
- `order_items` (order_id, product_id, quantity, unit_price)
- `daily_sales` (date, revenue, orders, units, by category), refreshed by a scheduled job or on `OrderPlacedEvent`
- `ai_briefings` (id, week_start, content, created_at)
- Phase 2: `product_embeddings` (product_id, embedding vector), `product_views` (user_id, product_id, viewed_at)

Analytics reads go through SQL **views** (`v_sales_by_day`, `v_sales_by_category`, `v_top_products`, `v_customer_summary`). The AI only ever sees these views.

## 6. Phase 1 features

### Storefront (thin)
- Register/login, browse and search products, cart, mock checkout, order history
- Stock check with optimistic locking (`@Version`) so two buyers can't take the last item

### Merchant dashboard
- KPI cards: revenue, orders, average order value, repeat-customer rate, each with % change vs previous period
- Charts: revenue over time, sales by category, top 10 products, low-stock list
- Date-range filter; results cached and evicted on new orders

### AI features
1. **Ask your store** (`POST /api/merchant/ai/ask`)
   - Prompt contains the view schemas plus the question; the model returns SQL and a suggested chart type
   - Safety: SELECT-only validation, allow-list of views, separate read-only DB user, row limit, query timeout
   - Response: answer text, table rows, chart config
2. **Weekly briefing**
   - `@Scheduled` job computes the week's stats, sends them as structured JSON to the model, stores the summary
   - "Regenerate" button on the dashboard
3. **Anomaly flags**
   - Z-score over a rolling 28-day window on daily revenue and order count
   - Flagged days show on the chart; clicking one asks the model for an explanation from that day's breakdown

Keep the statistics in Java and the wording in the LLM. That split is a good talking point in interviews.

## 7. Phase 2: AI recommendations

- **Similar products:** embed `name + description + category` for each product, store in pgvector, nearest-neighbour query on the product page
- **Bought together:** co-occurrence query over `order_items`
- **For you:** average the embeddings of a user's recently viewed/bought products, then query nearest neighbours, excluding already-purchased items
- **Cold start:** fall back to top sellers in the category
- **Merchant side:** dashboard tile showing recommendation click-through and revenue from recommended items (this ties Phase 2 back to the dashboard)

## 8. Build schedule (about 3 weeks part-time)

| Days | Milestone |
|---|---|
| 1-2 | Project setup, Docker Compose, Flyway, entities, auth with JWT |
| 3-5 | Catalogue, cart, checkout, orders, Swagger docs |
| 6-7 | Seed generator, analytics views, KPI and chart endpoints |
| 8-10 | Dashboard UI (Thymeleaf + Chart.js), caching |
| 11-13 | Ask-your-store with SQL guardrails, weekly briefing, anomaly flags |
| 14-15 | Tests (Testcontainers), GitHub Actions, README, deploy |
| 16-20 | Phase 2: pgvector, embeddings, three recommendation types, metrics tile |

If time is short, ship after day 15. Phase 1 is a complete project on its own.

## 9. What it proves to a reviewer

| Skill | Where it shows |
|---|---|
| REST API design | Versioned endpoints, DTOs, validation, problem-detail errors, OpenAPI |
| Spring Security | JWT filter chain, role-based method security |
| JPA and SQL | Relations, N+1 avoidance (entity graphs / fetch joins), views, aggregate queries |
| Concurrency | Optimistic locking on stock |
| Architecture | Modular monolith, domain events |
| AI integration | Spring AI, prompt design, guarded text-to-SQL, embeddings |
| Testing | Unit tests plus integration tests on real Postgres |
| DevOps | Docker, CI, live demo |

## 10. README and demo checklist

- Live demo link with a demo merchant login
- 30-second GIF of the dashboard and an "ask your store" question
- Architecture diagram
- "Design decisions" section: why a monolith, how the AI SQL is sandboxed, how caching is invalidated
- One command to run: `docker compose up`

## 11. CV bullets (fill in the numbers once built)

- Built an e-commerce platform in Java 25 / Spring Boot with JWT role-based security, PostgreSQL, and Flyway, covering catalogue, cart, checkout, and order management.
- Designed a merchant analytics dashboard with natural-language querying (guarded text-to-SQL via Spring AI), automated weekly AI briefings, and statistical anomaly detection.
- Implemented product recommendations using vector embeddings in pgvector and co-purchase analysis.
- Wrote integration tests with Testcontainers and set up CI with GitHub Actions; containerised with Docker and deployed a live demo.