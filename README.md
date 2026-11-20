# ShopPulse

**An e-commerce backend whose product is the merchant side: a dashboard that answers questions about your store in plain English.**

Java 25 · Spring Boot 3.5 · PostgreSQL · Spring AI · Docker

```bash
git clone https://github.com/ZainnB/ShopPulse.git && cd ShopPulse
docker compose up
# → http://localhost:8080        storefront
# → http://localhost:8080/merchant/dashboard   merchant dashboard
# → http://localhost:8080/swagger-ui.html      API docs
```

Six months of realistic sales data are generated on first start, so the dashboard is full
the moment it loads. **No API key is required** — without one the AI features fall back to a
built-in query library and label themselves as doing so. If you want live AI for free, the
project runs on Groq, Gemini, OpenRouter or a local Ollama by changing three environment
variables: see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#4-running-the-ai-features-without-paying).

| Demo account | Password | Role |
|---|---|---|
| `merchant@shoppulse.dev` | `merchant123` | MERCHANT |
| `customer@shoppulse.dev` | `customer123` | CUSTOMER |

---

## Why this project is not another e-commerce clone

The storefront is deliberately thin. The engineering is in the merchant side:

**1. Ask your store — guarded text-to-SQL**
Type *"which category dropped the most this month?"* and get an answer, the SQL that produced it,
the rows, and a chart. The model never sees the real schema: it is shown five read-only analytics
views and nothing else. Its output passes through `SqlGuard` before anything touches the database.

**2. Weekly AI briefing**
A scheduled job computes the week's numbers in Java, hands them to the model as structured JSON,
and stores five plain-English bullets with suggested actions.

**3. Statistical anomaly detection, explained**
A rolling 28-day z-score over daily revenue flags unusual days — *in Java, deterministically*.
The model is only asked to explain a day the statistics already flagged, from the numbers supplied.
Keeping the statistics in code and the wording in the model is a deliberate split: the numbers are
reproducible and testable, and the model cannot invent a trend that isn't there.

---

## How the AI SQL is sandboxed

This is the part worth reading. Five independent layers:

| Layer | What it does |
|---|---|
| **Schema isolation** | The prompt contains only five views (`v_sales_by_day`, `v_sales_by_category`, `v_product_sales_daily`, `v_top_products`, `v_customer_summary`). No table names, no `users.password_hash`, no way to ask for one. |
| **Statement parsing** | `SqlGuard` rejects anything that is not a single `SELECT`/`WITH`: no DML, no DDL, no stacked statements, no SQL comments, no `information_schema`/`pg_catalog`/`pg_sleep`/`dblink`, no `SELECT … INTO`. |
| **Identifier allow-list** | Every table-like identifier after `FROM`/`JOIN` must resolve to one of the five views or a CTE defined in the same statement. Anything else is refused. |
| **Least-privilege connection** | Migration `V3` creates a `shoppulse_ai_ro` login with `SELECT` on those five views and nothing else. Set `AI_READONLY_DATABASE_URL` and the AI path uses it; otherwise it falls back to the main pool opened read-only. |
| **Resource limits** | Read-only connection, query timeout, `setMaxRows`, an enforced `LIMIT` injected or clamped, and a token-bucket rate limit on the endpoint. |

Every question, the SQL chosen, and the row count are logged, so there is an audit trail.

---

## Architecture

A **modular monolith**, package-by-feature. One deployable unit, clear seams. Microservices would
have added network failure modes and deployment overhead to a system with one database and one team
— the module boundaries are enforced by package structure, not by HTTP.

```
com.shoppulse
├── auth        JWT issuing/validation, CUSTOMER and MERCHANT roles
├── catalog     products, categories, stock
├── cart        per-user cart
├── order       checkout, order lifecycle, OrderPlacedEvent
├── analytics   KPI queries, daily rollups, z-score anomaly detection
├── ai          ask-your-store, weekly briefing, anomaly explanation
├── seed        reproducible six-month demo data generator
├── web         Thymeleaf page controllers (data fetched from the REST API)
└── common      RFC 7807 error handling, caching, OpenAPI config
```

Inside each feature: `controller → service → repository`. DTOs (Java records) at the edge;
entities never leave the service layer. Every error leaves the API as an RFC 7807 problem detail.

### Request flow for a checkout

```
POST /api/v1/orders/checkout
  → JwtAuthenticationFilter resolves the caller
  → CheckoutService (@Transactional)
      · reduces stock through Product.reduceStock(), guarded by @Version
      · captures unit_price per line, so later price edits don't rewrite history
      · mock payment → status PAID
      · clears the cart
  → commit
  → OrderPlacedEvent (@TransactionalEventListener AFTER_COMMIT)
      · refreshes the daily_sales rollup for that day
      · evicts every dashboard cache
```

### Design decisions

**Optimistic locking over pessimistic.** `Product.version` means two buyers racing for the last
unit produce one order and one `409 Conflict`, without holding a row lock across the checkout
transaction. Contention on a single product is rare; the cost of being wrong is a retry.

**Views as the analytics contract.** Dashboard queries and the AI both read the same five views, so
"what the merchant sees" and "what the model can see" can never drift apart. Changing a table does
not silently change what the model is allowed to read.

**Cache invalidation by event, not by TTL alone.** Dashboard queries are Caffeine-cached for ten
minutes *and* evicted on `OrderPlacedEvent`, so a new order shows up immediately instead of up to
ten minutes later.

**A pre-aggregated `daily_sales` table alongside live views.** Anomaly detection scans a long
history every time it runs; the rollup keeps that cheap. A nightly job rebuilds the last three days
so a missed event heals itself.

**Graceful degradation instead of a hard dependency on a model.** No API key means rule-based
answers, clearly labelled — a reviewer can run the project without credentials, and the failure mode
is designed rather than accidental.

---

## API

Full OpenAPI docs at `/swagger-ui.html`. The shape:

```
POST   /api/v1/auth/register | /login            GET /api/v1/auth/me
GET    /api/v1/products?q=&categoryId=&page=     GET /api/v1/products/{id}
GET    /api/v1/categories
GET    /api/v1/cart    POST|PUT|DELETE /api/v1/cart/items[/{productId}]
POST   /api/v1/orders/checkout                   GET /api/v1/orders[/{id}]

MERCHANT only:
GET    /api/v1/merchant/analytics/kpis|series|by-category|top-products|low-stock|anomalies
POST   /api/v1/merchant/ai/ask
GET    /api/v1/merchant/ai/briefing/latest       POST /api/v1/merchant/ai/briefing/regenerate
POST   /api/v1/merchant/ai/anomalies/explain
```

---

## Running it

### Docker (recommended)

```bash
docker compose up            # app + Postgres, seeded, on :8080
```

### With live AI

```bash
export ANTHROPIC_API_KEY=sk-ant-...
docker compose up
```

Without the key the AI endpoints still work and report `aiGenerated: false`.

### Locally against a Postgres you already run

```bash
mvn spring-boot:run          # expects postgres://shoppulse:shoppulse@localhost:5432/shoppulse
```

### Tests

```bash
mvn verify                   # unit tests + Testcontainers integration tests (needs Docker)
```

### Configuration

| Variable | Default | Meaning |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | local Postgres | main datasource |
| `ANTHROPIC_API_KEY` | `not-set` | enables live AI; absent → rule-based fallback |
| `SHOPPULSE_AI_MODEL` | `claude-sonnet-5-5` | Anthropic chat model id |
| `AI_PROVIDER` | `auto` | `auto` \| `anthropic` \| `openai` \| `none` |
| `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL` | `not-set` | any OpenAI-compatible endpoint (Groq, Gemini, OpenRouter, Ollama) |
| `JWT_SECRET` | dev value | **override in production**, ≥ 32 bytes |
| `SEED_ENABLED` / `SEED_MONTHS` / `SEED_CUSTOMERS` | `true` / `6` / `2500` | demo data generator |
| `AI_READONLY_DATABASE_URL` | empty | optional least-privilege connection for AI SQL |
| `BRIEFING_CRON` | `0 0 7 * * MON` | weekly briefing schedule |

---

## Testing

- **Unit tests** cover the logic worth isolating: `SqlGuard` (table-driven, every rejection rule),
  `AnomalyDetector` (flat series, spike, crash, zero-variance, insufficient history), checkout
  (totals, empty cart, oversell), JWT (round-trip, tampering, expiry, weak secret).
- **Integration tests** run against a real PostgreSQL via Testcontainers — Flyway migrations, the
  views, and the full HTTP stack including the security filter chain.
- **159 tests in total**, all green on JDK 25.
- **CI** (GitHub Actions) runs `mvn verify` and builds the Docker image on every push.

---

## Deploying

`render.yaml` is included for a one-click Render deploy (web service + managed Postgres).
Set `JWT_SECRET` and, optionally, `ANTHROPIC_API_KEY`. The image also runs unchanged on Railway,
Fly.io, or anywhere that takes a container and a `DATABASE_URL`.

---

## Roadmap — Phase 2

Product recommendations with pgvector: embeddings of `name + description + category` for
similar-product lookups, co-occurrence over `order_items` for "bought together", and a per-user
vector for "for you", falling back to category best-sellers on cold start. The merchant dashboard
gets a tile for recommendation click-through and attributed revenue, which ties it back to the
rest of the product.

---

## Documentation

| Document | What's in it |
|---|---|
| [LEARNING.md](LEARNING.md) | Every concept the project uses, why each choice was made, the known limitations, and mock interview questions |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Deployment flow chart, configuration reference, free AI provider setups, failure modes |
| [docs/DEMO.md](docs/DEMO.md) | A 30-second demo script with the exact questions to type |

---

## License

MIT
