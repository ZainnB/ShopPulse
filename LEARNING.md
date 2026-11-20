# ShopPulse: the guide to defending this project

This is the document to read before an interview. It explains every concept the project uses,
*why* each choice was made, and where to point in the code. Every claim here is checked against
the code as it actually stands.

**How to use it:** read sections 1 and 2 to get the pitch and the map. Then work through 3–13,
opening the file named in each section as you go. Section 14 is mock interview questions,
section 15 is the limitations — know those honestly, because a good interviewer will find them
and the only wrong answer is pretending they don't exist.

---

## 1. The 60-second pitch

> ShopPulse is an e-commerce backend where the merchant side is the product. The storefront is
> deliberately thin — catalogue, cart, mock checkout — and the engineering depth is in the
> merchant dashboard.
>
> Three things make it more than a CRUD app. First, "ask your store": the merchant types a
> question in English, a language model writes SQL against a read-only analytics schema, and
> the answer comes back with the SQL, the rows, and a chart. Second, a scheduled job turns the
> week's numbers into a plain-English briefing. Third, anomaly detection — a rolling z-score
> flags unusual days in Java, and the model is only asked to explain a day the statistics
> already flagged.
>
> The part I'd want to talk about is the sandbox around the generated SQL. The model never sees
> the real schema — only five analytics views. Its output passes a parser that rejects anything
> that isn't a single SELECT, checks every table reference against an allow-list, and injects a
> row limit. It executes on a read-only connection with a statement timeout. And the whole AI
> layer degrades to a built-in query library when there's no API key, so the demo works for
> anyone who clones it.

Memorise the shape of that, not the words. The last sentence is the one that separates this
from a tutorial project: **it has a designed failure mode.**

---

## 2. Map of the codebase

```
src/main/java/com/shoppulse/
├── ShopPulseApplication.java     @SpringBootApplication, @EnableCaching, @EnableScheduling
├── common/        cross-cutting: error handling, cache config, OpenAPI config
├── auth/          User, JWT issuing and validation, SecurityConfig, CurrentUserService
├── catalog/       Category, Product (+ @Version), search with paging
├── cart/          Cart, CartItem, per-user cart rules
├── order/         Order, OrderItem, CheckoutService, OrderPlacedEvent
├── analytics/     KPI queries over views, AnomalyDetector, daily rollup, cache eviction
├── ai/            SqlGuard, ReadOnlySqlExecutor, ChatGateway, the three AI features
├── seed/          DemoDataSeeder: reproducible 6 months of orders
└── web/           Thymeleaf page controllers (view names only)

src/main/resources/
├── db/migration/  V1 schema, V2 the five views, V3 read-only role
├── templates/     Thymeleaf pages + fragments/layout.html
└── static/        app.css, js/*.js (plain ES modules, no bundler)
```

**The one architectural rule:** inside each feature it goes `controller → service → repository`.
DTOs (Java records) at the edge; entities never leave the service layer. If you remember one
thing about structure, remember that entities stay inside.

---

## 3. Why a modular monolith (and not microservices)

This is asked constantly, and "microservices are trendy" is a losing answer. The real one:

- There is **one database and one deployable**. Splitting into services would add network calls,
  partial-failure handling, distributed transactions and five deployment pipelines, and buy
  nothing, because nothing here needs to scale independently.
- The module boundaries are real, they're just **enforced by package structure rather than by
  HTTP**. `ai` does not import from `analytics` or `order`; it reaches data through the views.
  That means the seam exists if a module ever genuinely needs to move out.
- Domain events (`OrderPlacedEvent`) give the decoupling benefit — the checkout code doesn't
  know analytics exists — without the operational cost.

**Say this:** "Microservices solve organisational and independent-scaling problems. I had
neither, so I kept the seams and skipped the network."

---

## 4. Java language features you should be able to explain

You target **Java 25** (an LTS release), running on Spring Boot 3.5.16.

**Records** (`src/main/java/com/shoppulse/analytics/KpiMetric.java` and every DTO). Immutable
data carriers: the compiler generates the constructor, accessors, `equals`, `hashCode`,
`toString`. Accessors are `value()` not `getValue()`. Used for every DTO and for
`@ConfigurationProperties`. *Why:* a DTO has no behaviour and no identity, so a record states
that in one line and removes a whole class of bugs where someone mutates a response object.

**Text blocks** (`"""`). Used for every multi-line SQL string and every prompt. *Why:* SQL stays
readable and you stop escaping quotes. Know that the closing delimiter's indentation sets the
baseline, and `\` at line end joins lines without a newline.

**Switch expressions with arrow labels and exhaustiveness** — see
`ai/ChatGateway.java#activeModel()` switching over the `Provider` enum. *Why it matters:* over an
enum with no `default`, the compiler fails the build if you add an enum constant and forget a
branch. That's a real safety property, not sugar.

**`Optional`** as a return type for "might not be there" repository lookups
(`UserRepository.findByEmail`). Never as a field or parameter.

**Streams** for in-memory transformation (`Order.recalculateTotal()` reduces line totals).
Not for database work — that's what SQL is for.

**`var`** for local variables where the type is obvious from the right-hand side. Not in fields
or method signatures.

If asked "what's new in modern Java that you actually use?", answer records, text blocks, switch
expressions, and sealed types *(the last one you don't use here — say so rather than bluffing)*.

---

## 5. Spring Core: dependency injection

**The idea:** you don't construct your collaborators, you declare what you need and the
container supplies it. That makes code testable, because a test can supply a different
implementation.

**Constructor injection everywhere, no field `@Autowired`.** Look at any service. Why:

1. The object is fully initialised when the constructor returns — no half-built beans.
2. Dependencies can be `final`, so they can't be swapped at runtime.
3. **You can construct it in a plain unit test with `new`**, no Spring context. That's exactly
   what `CartServiceTest` and `CheckoutServiceTest` do, which is why the unit suite runs in
   milliseconds.
4. A constructor with eight parameters is visibly too big. Field injection hides that.

**`ObjectProvider<T>`** (`ai/ChatGateway.java`): lazy, optional lookup of a bean that may not
exist. Used because the Anthropic and OpenAI model beans are both optional, and asking for
`ChatModel` directly would fail with "two candidate beans". Explaining that trade-off is a
strong answer about how the container resolves ambiguity.

**`@ConfigurationProperties`** (`auth/JwtProperties`, `ai/AiProperties`, `seed/SeedProperties`):
binds a block of YAML onto a typed record, validated and relocatable, instead of scattering
`@Value("${...}")` strings. `@EnableConfigurationProperties` registers them.

**`@ConditionalOnExpression`** (`ai/AiConfig`): the read-only AI datasource bean only exists when
`shoppulse.ai.sql.readonly-url` is non-blank. Conditional beans are how Spring Boot's own
autoconfiguration works — this is the same mechanism in your own code.

---

## 6. Spring Boot: what "autoconfiguration" actually means

A **starter** is a dependency that pulls in a library *plus* autoconfiguration for it.
Autoconfiguration classes are listed in each jar's
`META-INF/spring/...AutoConfiguration.imports`, and each is guarded by conditions:
`@ConditionalOnClass` (is this library present?), `@ConditionalOnMissingBean` (did the developer
already define this?), `@ConditionalOnProperty`.

Why that design matters: **your bean always wins.** Autoconfiguration backs off when it sees you
defined your own. That's how `common/CacheConfig` replaces Boot's default `CacheManager` with a
tuned Caffeine one just by declaring a `@Bean`.

Concrete examples in this project:
- `spring-boot-starter-data-jpa` on the classpath + a `DataSource` → Hibernate, a
  `JdbcTemplate`, transaction management, repository proxies, all without configuration.
- `spring-ai-starter-model-anthropic` → an `AnthropicChatModel` bean built from
  `spring.ai.anthropic.*`.
- Adding `spring-ai-starter-model-openai` gave a *second* chat model bean — which is what forced
  the explicit provider selection in `ChatGateway`. Good story: adding a dependency changed
  bean resolution, and the fix was to be explicit rather than to fight the container.

**Be ready for:** "What happens if two autoconfigurations both want to create a `DataSource`?"
Answer: Boot's is `@ConditionalOnMissingBean`, so defining your own replaces it — and this is
exactly why `ai/AiReadOnlyDataSource` **wraps** a Hikari pool instead of being a second
`DataSource` bean. A bare second `DataSource` would have made the primary one ambiguous and
broken JPA and Flyway. That is a real bug that was designed around; it interviews very well.

---

## 7. Spring MVC and API design

**Request path:** servlet container → `DispatcherServlet` → `HandlerMapping` picks the
`@RequestMapping` method → argument resolvers bind `@RequestBody`/`@PathVariable`/`@RequestParam`
and run Bean Validation → your method → `HttpMessageConverter` (Jackson) serialises the return
value.

**Versioned paths** — everything is under `/api/v1/`. Cheap to do on day one, impossible to
retrofit politely.

**DTOs at the edge, never entities.** Three reasons, in order of how much they matter:
1. **Security:** returning a `User` entity would serialise `passwordHash`.
2. **Coupling:** your JSON contract would change every time you rename a column.
3. **Lazy loading:** serialising an entity with a lazy `@ManyToOne` outside a transaction throws
   `LazyInitializationException` — see section 8.

**Bean Validation** — `@NotBlank`, `@Email`, `@Positive`, `@Size`, triggered by `@Valid` on the
parameter. A violation becomes `MethodArgumentNotValidException`, which the global handler turns
into a 400 with a field-by-field `errors` map.

**RFC 7807 problem details** (`common/ApiExceptionHandler.java`). Every error — validation, not
found, conflict, 401, 403, 500 — leaves as `application/problem+json` with `type`, `title`,
`status`, `detail`, `timestamp`, `path`. One `@RestControllerAdvice` maps exception types to
statuses:

| Exception | Status | Why |
|---|---|---|
| `NotFoundException` | 404 | resource doesn't exist |
| `BusinessException` | 409 | a rule was broken (empty cart, not enough stock) |
| `ObjectOptimisticLockingFailureException` | 409 | someone else bought it first |
| `MethodArgumentNotValidException` | 400 | + per-field messages |
| `PropertyReferenceException` | 400 | unknown `?sort=` property is a client mistake |
| `NoResourceFoundException` / `NoHandlerFoundException` | 404 | no such URL |
| `Exception` | 500 | logged with a stack trace, generic message to the caller |

**Two of those rows are debugging stories worth telling.** The `PropertyReferenceException`
mapping exists because an unknown `?sort=` value was returning 500 — a client error reported as
a server fault. The `NoResourceFoundException` mapping exists because **every** unknown URL was
returning 500: the catch-all `Exception` handler was swallowing Spring's "no handler" exception,
so even the browser's automatic `/favicon.ico` probe produced a 500 and a logged stack trace.
Found by driving the app in a real browser and reading the console. The lesson, which is the
interview-ready version: *a catch-all exception handler will happily hide framework exceptions
that have a correct status of their own.*

**`@RestControllerAdvice` ordering:** more specific handlers win over `Exception`. Where you need
to be certain, set `@Order` — `ai/AiExceptionHandler` does, so the rate-limit 429 isn't caught by
the generic 500 handler.

**springdoc-openapi** generates `/v3/api-docs` from the annotations and serves Swagger UI at
`/swagger-ui.html`, with a Bearer scheme configured in `common/OpenApiConfig` so you can
authorise and click through the API in the browser.

---

## 8. JPA and Hibernate: the parts that actually bite

**Entity lifecycle:** transient → managed (inside a persistence context) → detached (after the
transaction) → removed. Inside a transaction, Hibernate **dirty-checks** managed entities and
writes the UPDATE at flush time. You'll see this in `CheckoutService`: it calls
`product.reduceStock(qty)` and never calls `save()` on the product. That isn't a bug — the
product is managed, so the UPDATE happens at flush. Be ready to explain that; it looks like a
missing save to anyone who hasn't internalised the persistence context.

**`spring.jpa.open-in-view: false`** — set deliberately in `application.yml`. The default (`true`)
keeps the persistence context open for the whole web request, so lazy loads in the view layer
silently work and fire extra queries you never see. Turning it off means a lazy access outside
the service layer throws immediately. **Say:** "I turned off open-in-view so N+1s fail loudly
instead of hiding in the view layer."

**The N+1 problem.** Load 20 orders, then touch `order.getItems()` on each: 1 + 20 queries.
Fixes used here:
- `@EntityGraph(attributePaths = ...)` on repository methods (`CartRepository`, `OrderRepository`,
  `ProductRepository`) to fetch the association in the same query.
- Fetch joins in `@Query` for single-aggregate loads.

**`ddl-auto: validate`.** Hibernate never creates or alters a table; Flyway owns the schema, and
Hibernate's only job at startup is to confirm the mapping matches. A mismatch fails fast at boot
rather than at the first query. Never use `update` outside a toy.

**Optimistic locking — the concurrency story.** `Product` has a `@Version long version`. On every
UPDATE, Hibernate adds `WHERE id = ? AND version = ?` and bumps the version. If two checkouts
race for the last unit, the second one's UPDATE matches zero rows, Hibernate throws
`ObjectOptimisticLockingFailureException`, and the handler returns **409**. No overselling, and
no row lock held across the transaction.

`CheckoutService` uses `saveAndFlush`, so the conflict surfaces *inside* the method, before the
`OrderPlacedEvent` is published — you never publish an event for an order that then fails.

**Why optimistic and not pessimistic?** `SELECT ... FOR UPDATE` serialises every checkout of a
popular product and holds a lock for the whole transaction. Contention on one product is rare;
the cost of being wrong is one retry. Optimistic locking is the right default for low-contention,
read-heavy workloads. Know the inverse too: with high contention on a single row, pessimistic
locking avoids a storm of failed retries. **Being able to say when your choice is wrong is the
answer that lands.**

**Price capture.** `order_items.unit_price` stores the price at purchase time. If prices were read
from `products` at report time, changing a price would silently rewrite history. Small decision,
and interviewers notice it.

---

## 9. SQL, views, and Flyway

**Flyway** runs versioned migrations in order, recording each in `flyway_schema_history`, so every
environment converges on the same schema. Migrations are **immutable once applied** — you add
`V4`, you never edit `V1`.

- `V1__core_schema.sql` — tables, constraints (`CHECK (stock >= 0)`, status enum check), indexes.
- `V2__analytics_views.sql` — the five views.
- `V3__ai_readonly_role.sql` — the `shoppulse_ai_ro` login, wrapped in a `DO $$ ... EXCEPTION
  WHEN insufficient_privilege $$` block so it degrades to a notice on managed Postgres that
  won't let you `CREATE ROLE`. **Migrations have to work on every target, not just your laptop.**

**Why views are the analytics contract.** The dashboard and the AI both read the same five views:

| View | Grain |
|---|---|
| `v_sales_by_day` | one row per day |
| `v_sales_by_category` | day × category |
| `v_product_sales_daily` | day × product |
| `v_top_products` | product, all-time, including stock |
| `v_customer_summary` | customer lifetime totals |

Three benefits, and you should give all three: a **single definition of "revenue"** (every view
filters `status IN ('PAID','SHIPPED','DELIVERED')`, so pending and cancelled orders never count);
**"what the merchant sees" can't drift from "what the model can see"**; and the views are the
unit of `GRANT`, which is what makes a least-privilege DB role possible at all.

**Indexes:** on `orders.created_at` (every dashboard query filters by date), on the foreign keys
used for joins, and a functional index on `lower(products.name)` to support case-insensitive
search.

**Upsert:** the rollup uses `INSERT ... ON CONFLICT (sale_date) DO UPDATE`, so recomputing a day
is idempotent — which is what makes the self-healing nightly job safe to re-run.

---

## 10. Transactions, events, caching, scheduling

**`@Transactional`** works by proxy: Spring wraps the bean, opens a transaction before the method
and commits or rolls back after. Two consequences that get asked:
1. **A self-call doesn't go through the proxy**, so `@Transactional` on an internal call does
   nothing.
2. Rollback is on unchecked exceptions by default, not checked ones.

`readOnly = true` on read paths lets Hibernate skip dirty-checking and hints the driver.

**Domain events.** `CheckoutService` publishes `OrderPlacedEvent`. `analytics/OrderPlacedListener`
consumes it with `@TransactionalEventListener(phase = AFTER_COMMIT)`.

**Why AFTER_COMMIT matters — this is a real interview question.** A plain `@EventListener` runs
*inside* the publishing transaction. The listener would read data that isn't committed yet, and
if the transaction then rolled back you'd have refreshed analytics for an order that never
existed. `AFTER_COMMIT` runs only once the data is durable.

**Then the follow-up:** after commit there's no active transaction, so a listener that writes
needs a new one — which is why the rollup methods are
`@Transactional(propagation = Propagation.REQUIRES_NEW)`. If you can explain that pair, you
understand Spring transactions better than most candidates.

**Caching.** Caffeine, configured in `common/CacheConfig`: 10-minute TTL, max 500 entries, stats
recorded. Dashboard service methods are `@Cacheable` keyed on the date range.

The invalidation is the interesting half: **TTL alone would be wrong**, because a merchant who
takes an order wants to see it now, not in ten minutes. So `OrderPlacedListener` also evicts
every dashboard cache on each order. TTL is the backstop for staleness you can't predict
(someone editing the database directly); the event is the precise answer. This was verified
end-to-end: place an order, and the KPI order count increments on the next request.

**Scheduling.** `@EnableScheduling` plus `@Scheduled`: the weekly briefing on a configurable cron
(`${shoppulse.ai.briefing.cron}`) and a nightly 02:15 job that rebuilds the last three days of
rollups so a missed event heals itself. **Know the limitation:** `@Scheduled` fires on *every*
instance, so on two replicas the briefing would be generated twice. The honest answer is that
this needs a lock (ShedLock, or a database advisory lock) before scaling out — see section 15.

---

## 11. Spring Security: the filter chain and JWT

**Where security happens:** a chain of servlet filters *in front of* `DispatcherServlet`. By the
time your controller runs, authentication is already resolved and stored in the
`SecurityContextHolder` (a `ThreadLocal`).

**The flow for an authenticated request:**
1. `JwtAuthenticationFilter` (registered before `UsernamePasswordAuthenticationFilter`) reads the
   `Authorization: Bearer <token>` header.
2. `JwtService` verifies the HMAC signature and expiry. A bad token is **not** an exception — the
   filter logs at debug and continues unauthenticated, so the authorization rules decide the
   response. (A filter that throws produces ugly, inconsistent errors.)
3. `AppUserDetailsService` loads the user by email; authorities are `ROLE_` + role name.
4. The authorization rules in `SecurityConfig` run.
5. If the request was rejected, a custom `AuthenticationEntryPoint` (401) or `AccessDeniedHandler`
   (403) writes **problem+json**, so an API error looks the same whether it came from a filter or
   a controller. Easy to forget, and it's exactly where most projects leak an HTML error page
   into a JSON API.

**Authentication vs authorization:** authentication is *who are you* (the token, the password
check); authorization is *may you do this* (role rules, `@PreAuthorize`). Don't blur them.

**Stateless sessions.** `SessionCreationPolicy.STATELESS` — no `JSESSIONID`, no server-side
session. Any instance can serve any request, which is what makes horizontal scaling trivial.

**Why CSRF is disabled, and why that's safe here.** CSRF attacks work because a browser attaches
cookies automatically to cross-site requests. This API carries its credential in an
`Authorization` header that JavaScript must set explicitly, so a cross-site form post has no
credential to ride on. **The reasoning is what matters — "I disabled CSRF because it was
breaking" is a failed answer; "there is no ambient cookie credential to forge" is a correct one.**
Note the converse: if you moved the token into a cookie, you would need CSRF protection back.

**Password hashing.** BCrypt via `PasswordEncoder`. It's deliberately slow and salts each hash,
so identical passwords hash differently and brute force is expensive. Never SHA-256 for
passwords — fast is the opposite of what you want.

**The authorization rules** (read `SecurityConfig` and be able to narrate them):
- Public: the page shells, static assets, `register`/`login`, `GET /api/v1/products/**`,
  `GET /api/v1/categories`, `/actuator/health/**`, Swagger.
- `/api/v1/merchant/**` → `hasRole("MERCHANT")`.
- Everything else under `/api/**` → authenticated.
- `/actuator/**` beyond health → authenticated (info and metrics are not public).

**The subtle bit to volunteer:** `/merchant/dashboard` is a *public HTML page* while
`/api/v1/merchant/**` is locked down. The page is an empty shell; every number on it is fetched
by JavaScript with the token. So the page being public leaks nothing, and the data is protected
in exactly one place instead of two. If asked "isn't that a hole?" — the answer is that the page
contains no data.

**`@EnableMethodSecurity`** enables `@PreAuthorize("hasRole('MERCHANT')")` as defence in depth on
the controllers, so a mistake in a URL pattern isn't the only thing standing between a customer
and the analytics API.

**JWT mechanics:** header.payload.signature, base64url, signed with HMAC-SHA256. The payload is
**signed, not encrypted** — anyone can read it, so no secrets go in the claims (here: subject =
email, plus `role` and `uid`). `JwtService` refuses a secret shorter than 32 bytes
(`MIN_SECRET_BYTES`), because HS256 with a short key is weak. **Known trade-off:** a JWT can't be
revoked before it expires; the mitigation here is a short 120-minute expiry, and the real fix is
refresh tokens plus a denylist.

---

## 12. The AI layer, which is what they'll ask about

### 12.1 Guarded text-to-SQL ("ask your store")

The pipeline, which you should be able to draw:

```
question
  → prompt = five view definitions + today's date + rules
  → model returns JSON {sql, chartType, labelColumn, valueColumn, explanation}
  → extract the first balanced JSON object (models wrap things in prose)
  → SqlGuard.validate(sql)          ← refuses anything unsafe
  → ReadOnlySqlExecutor.execute()   ← read-only connection, timeout, maxRows
  → mask emails (PiiMasker)
  → second cheap model call turns rows into a sentence
  → AskResponse{answer, sql, columns, rows, chartType, aiGenerated}
```

**Five layers of defence. Learn these as a list — it's the single best thing in the project.**

1. **Schema isolation.** The prompt contains only the five views. The model is never told that
   `users` or `password_hash` exist, so it can't ask for them.
2. **Statement parsing** (`ai/SqlGuard.java`). Must be a single `SELECT` or `WITH ... SELECT`.
   Rejected: all DML and DDL, stacked statements (a `;` with anything after it), SQL comments
   (`--`, `/* */`), `information_schema`, `pg_catalog`, `pg_sleep`, `dblink`, `generate_series`,
   `SELECT ... INTO`, `FOR UPDATE`. It masks string literals before scanning for keywords, so a
   product legitimately named `'drop'` isn't treated as a `DROP`.
3. **Identifier allow-list.** Every table-like identifier after `FROM`/`JOIN` must be one of the
   five views, or a CTE defined *earlier in the same statement*. That ordering detail matters:
   without it, `WITH a AS (SELECT * FROM orders), orders AS (...)` would sneak a real table past
   the check by shadowing its name.
4. **Least privilege at the database.** Migration `V3` creates `shoppulse_ai_ro` with `SELECT` on
   those five views and nothing else. Set `AI_READONLY_DATABASE_URL` and the AI path uses it;
   otherwise it falls back to the main pool opened read-only. The executor also disables
   auto-commit so the driver really issues a read-only transaction, and always rolls back.
5. **Resource limits.** `setQueryTimeout`, `setMaxRows`, a `LIMIT` injected when absent and
   clamped when too large, and a token-bucket rate limit of **20 questions/minute** on the
   endpoint so a public demo can't burn an API budget.

**The principle to state:** *never trust model output as code.* Treat generated SQL exactly like
input from an untrusted client — parse it, allow-list it, and run it with the least privilege
that works. 73 unit tests cover `SqlGuard` alone, including the rejections.

**Why not just use parameterised queries?** Because the whole feature is that the *structure* of
the query is dynamic. Parameterisation protects values, not structure. When the structure itself
is untrusted, you need a parser, an allow-list, and least privilege — which is what this does.

### 12.2 Statistics in Java, wording in the model

`analytics/AnomalyDetector.java` is a plain class with no Spring dependencies:
`WINDOW = 28`, `MIN_HISTORY = 7`, `THRESHOLD = 2.0`, `HIGH_THRESHOLD = 3.0`.

For each day with at least 7 prior days, it computes the mean and **sample** standard deviation
of up to the previous 28 days and the z-score `(value − mean) / stdDev`. `|z| ≥ 2` is flagged;
`≥ 3` is HIGH. Zero-variance windows are skipped rather than dividing by zero.

**Why this split?** The numbers are deterministic, reproducible and unit-tested; the model only
*describes* a day the statistics already flagged, from facts it's handed. A model asked to "find
anomalies" would invent them and give a different answer each time. **Say:** "Statistics in code,
wording in the model — so the finding is reproducible and the explanation is readable."

Know the statistics honestly: a z-score assumes a roughly normal distribution and is sensitive to
window length; a long sustained shift drags the mean along with it and stops being flagged. It's
a reasonable, cheap detector, not a forecasting model. Say that before they say it.

**The failure mode worth volunteering — it happened in this project.** A total-outage day with
£0 revenue sat in the data and the detector did not flag it. Nothing was broken: a big sale day
eleven days earlier was still inside the trailing 28-day window, and one extreme value inflates
the standard deviation enough that a later collapse falls under the 2-sigma threshold. *An
outlier in the window hides the next outlier.* The seed data now spaces its planted anomalies so
each is measured against a clean baseline, and the comment in `DemoDataSeeder` says why.

The proper fix, if this were production, is a **robust** statistic: median and MAD (median
absolute deviation) instead of mean and standard deviation, because the median barely moves when
one value is extreme. Being able to name that as the next step — and explain why mean/sigma is
fragile here — is a much better answer than claiming the detector is fine.

### 12.3 Graceful degradation

`ai/AiAvailability.java` resolves one of `ANTHROPIC`, `OPENAI`, `NONE` at startup (and logs which).
`NONE` is a **supported mode**, not an error:

- **Ask your store** matches the question against a 7-intent library of pre-written parameterised
  SELECTs over the views and summarises the rows deterministically.
- **Weekly briefing** writes its five bullets from the same computed numbers, recording the model
  as `rule-based`.
- **Anomaly explanation** states the arithmetic.

Every response carries `aiGenerated: false` and the UI shows a "rule-based fallback" badge.
Also: in live mode, *any* failure — guard rejection, SQL error, model outage, unparseable JSON —
falls through to the same path with the reason in `note`. **The feature never returns an error
page because a third party is down.**

Because `ChatGateway` is the only class that touches Spring AI, and the OpenAI starter speaks a
protocol that Groq, Gemini, OpenRouter and a local Ollama all implement, switching provider is
three environment variables (see `docs/DEPLOYMENT.md`). That's the payoff of putting the
integration behind one seam.

### 12.4 Privacy

`ai/PiiMasker.java` masks email and password-like columns before rows are sent to the model
(`jane@example.com` → `j***@example.com`). The merchant's own API response is unchanged — they're
entitled to see their customers. The reasoning: a model-written query may legitimately select
`v_customer_summary.email`, and there's no reason for real addresses to reach a third-party
inference provider just so it can phrase a sentence. **Volunteer this.** Thinking about what
leaves the building is a senior instinct, and free-tier LLM providers frequently reserve the
right to train on what you send them.

---

## 13. Testing, Docker, CI

**159 tests, two kinds.**

*Unit tests* — plain JUnit 5 and Mockito, no Spring context, no database, milliseconds. They
cover the logic worth isolating: `SqlGuard` (73 table-driven cases), `AnomalyDetector` (flat
series, spike, crash, zero variance, insufficient history), `CheckoutService` (totals, empty
cart, oversell), `JwtService` (round-trip, tampering, expiry, weak secret), `KpiMath`,
`PiiMasker`.

*Integration tests* — `ShopPulseIntegrationTest` over a **real PostgreSQL 16 in Testcontainers**.
`@ServiceConnection` wires the container's JDBC URL into the context automatically, Flyway runs
the production migrations, and `MockMvc` drives the full HTTP stack including the security filter
chain. It asserts things no mock can: that all five views exist and are queryable, that a
customer gets 403 and an anonymous caller 401 on the merchant API, that checkout decrements stock
and the after-commit listener refreshes `daily_sales`, that the view and the rollup agree, that
over-ordering returns 409 with stock untouched.

**Why a real database?** An H2 in-memory stand-in doesn't have Postgres's `::date` casts,
`FILTER (WHERE ...)`, or `ON CONFLICT`, so it would test a schema you don't ship. The views would
be untested.

**A debugging story worth telling.** The integration tests first failed with
`Could not resolve placeholder 'shoppulse.ai.briefing.cron'`. Cause: a
`src/test/resources/application.yml` **replaces** the main one wholesale rather than merging with
it, so every property not copied into it vanished. Fix: delete the test file and override only
the handful of values a test needs via `@DynamicPropertySource`. **Lesson: Spring Boot picks the
highest-precedence `application.yml` on the classpath — it does not merge them.** That is a
genuinely common mistake and a great answer to "tell me about a bug you debugged".

**Docker — a multi-stage build.** Stage one is `maven:3.9-eclipse-temurin-25` and produces the
jar; stage two is `eclipse-temurin:25-jre-alpine` and receives only the jar. The build tools,
the Maven cache and the source never reach the shipped image, so it's a fraction of the size and
has a far smaller attack surface. `COPY pom.xml` and `dependency:go-offline` happen *before*
`COPY src`, so Docker's layer cache keeps the dependency download when only code changes. It runs
as a **non-root** user and sets `-XX:MaxRAMPercentage=75` so the JVM sizes its heap to the
container limit rather than the host's memory.

**CI** (`.github/workflows/ci.yml`): JDK 25, `mvn verify` (Testcontainers works on GitHub runners
because Docker is available), plus a separate job that builds the image. Catching "it builds on
my machine" is the entire point.

---

## 14. Mock interview questions

Practise answering these out loud.

**"Walk me through what happens when a customer checks out."**
POST hits `OrderController` → the JWT filter has already resolved the caller → `CheckoutService`
in a transaction loads the cart, calls `reduceStock` on each managed `Product` (Hibernate writes
the UPDATE at flush; `@Version` makes a concurrent oversell fail with 409), builds the `Order`
with `unit_price` captured per line, mocks payment by setting PAID, clears the cart,
`saveAndFlush` so a conflict surfaces before publishing, commits, then `OrderPlacedEvent` fires
after commit and the listener refreshes the daily rollup and evicts the dashboard caches.

**"How do you stop the LLM from dropping your tables?"**
Give the five layers from 12.1, in order, then the principle: never trust model output as code,
treat it like input from an untrusted client.

**"Why not microservices?"** → section 3.

**"What's your biggest N+1 risk and how did you handle it?"**
Rendering orders with their line items and products. `@EntityGraph`/fetch joins, plus
`open-in-view: false` so a lazy access outside the service layer fails loudly instead of quietly
firing queries.

**"Two customers buy the last item at the same time. What happens?"**
`@Version` on `Product`; the losing UPDATE matches no rows;
`ObjectOptimisticLockingFailureException` → 409 with a readable message. Then volunteer when
pessimistic locking would be the better choice.

**"How do you know your cache isn't serving stale numbers?"**
10-minute TTL as a backstop *plus* explicit eviction on `OrderPlacedEvent`. Verified
end-to-end: place an order and the KPI count increments on the next request.

**"What happens if the AI provider is down or you have no key?"**
Describe `AiAvailability` and the fallback library; note that `aiGenerated` tells the UI which
mode produced the answer and the badge says so. Then: the demo works with zero credentials,
which is why anyone can clone and run it.

**"Where would this break at scale?"** → section 15. Lead with the `@Scheduled`-on-every-instance
problem; it shows you've thought past one box.

**"Tell me about a bug you debugged."**
Best two: the test `application.yml` replacing the main one (section 13), and every unknown URL
returning 500 because the catch-all handler swallowed `NoResourceFoundException` (section 7).
Both have a crisp cause, a crisp fix, and a transferable lesson.

**"What would you do differently?"** → section 15. Never answer "nothing".

---

## 15. Limitations — know these before they find them

Honesty here reads as seniority. Pretending the project is finished does not.

1. **`@Scheduled` on multiple instances.** Both the weekly briefing and the nightly rollup would
   run on every replica. Needs a distributed lock (ShedLock) or a single scheduler before
   scaling out.
2. **JWTs can't be revoked** before expiry. Mitigated by a 120-minute lifetime; the real fix is
   short access tokens plus refresh tokens and a denylist. There's also no refresh flow, so the
   UI simply redirects to login when a token expires.
3. **Caching is per-instance.** Caffeine is in-process, so two replicas have independent caches
   and an eviction on one doesn't reach the other. Redis would fix it.
4. **Views compute from raw tables on every query.** Fine at demo scale (a few thousand orders).
   At millions of rows, `v_product_sales_daily` scanning all history per request would need
   materialised views, a proper rollup table, or a date-partitioned fact table.
5. **The anomaly detector is simple by choice.** Z-scores assume near-normal data, are sensitive
   to window length, and miss slow sustained shifts because the trailing mean follows them.
   Seasonality is only handled implicitly by the 28-day window. Worst of all, one extreme day
   inflates sigma and can mask the next anomaly (see 12.2) — median/MAD would fix that.
6. **Payment is mocked.** Real payments mean idempotency keys, webhooks, a pending→paid state
   machine and reconciliation — deliberately out of scope, and say so that way.
7. **No refresh of stock on cart view.** A cart can hold a quantity that's no longer available;
   it's caught at checkout with a 409 rather than earlier. Acceptable, but a nicer UX would
   re-validate on cart load.
8. **The free-tier AI path is quality-limited.** A small local model writes worse SQL; it fails
   safely into the fallback, but the answers are weaker than with a frontier model.
9. **Second model call per question.** "Ask your store" calls the model twice (SQL, then
   summary). Simple, and it doubles latency and cost; one structured call could do both.
10. **Two metrics sounded identical and were not.** The dashboard's "repeat customer rate"
    counts customers with 2+ orders *inside the selected window*; the weekly briefing was
    reporting the share of that week's buyers who had *ever* ordered twice. Both are valid, but
    sharing a name meant the dashboard said 27% and the briefing said 84% on the same screen.
    The briefing's figure is now named "returning buyer share" and the code says why. The general
    lesson: a metric is its definition, and two definitions must never share one label.
11. **Seed data is synthetic.** The repeat-customer rate and category mix come from a generator
    with a fixed seed, not real behaviour. (The customer count was raised to 2,500 precisely
    because 120 customers against ~3,900 orders made the repeat rate meaninglessly high — a
    reminder that a demo's numbers have to be plausible, not just present.)

---

## 16. One-week revision plan

| Day | Do this |
|---|---|
| 1 | Run `docker compose up`. Log in as the merchant, click everything. Read `README.md`. |
| 2 | Read `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`. Write out the filter chain from memory. Section 11. |
| 3 | Read `CheckoutService` and `Product`. Explain optimistic locking aloud without notes. Section 8. |
| 4 | Read `SqlGuard` and its test. List the five defence layers from memory. Section 12.1. |
| 5 | Read `AnomalyDetector`, `AnalyticsService`, `OrderPlacedListener`. Explain AFTER_COMMIT + REQUIRES_NEW. Sections 10 and 12.2. |
| 6 | Read `ShopPulseIntegrationTest`. Explain why a real Postgres and not H2. Section 13. |
| 7 | Answer every question in section 14 out loud. Then recite section 15 without looking. |

**The test of whether you're ready:** can you draw the checkout sequence and the ask-your-store
pipeline on a whiteboard, from memory, and name a trade-off you'd revisit in each?
