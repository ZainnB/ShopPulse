# Deploying ShopPulse

Everything here is a container plus a Postgres URL. If a platform can run those two things,
ShopPulse runs on it.

---

## 1. The deployment flow

```mermaid
flowchart TD
    A[git push to main] --> B{GitHub Actions CI}
    B -->|mvn verify| B1[159 tests<br/>Testcontainers starts real Postgres]
    B -->|docker build| B2[Image builds from Dockerfile]
    B1 --> C{All green?}
    B2 --> C
    C -->|no| CX[Stop: fix and push again]
    C -->|yes| D[Platform pulls the repo]

    D --> E[Stage 1: maven:3.9-eclipse-temurin-25<br/>mvn package -DskipTests]
    E --> F[Stage 2: eclipse-temurin:25-jre-alpine<br/>copy jar only, run as non-root]
    F --> G[Container starts]

    G --> H{DATABASE_URL reachable?}
    H -->|no| HX[Crash loop:<br/>check the database env vars]
    H -->|yes| I[Flyway runs V1 to V3<br/>schema, 5 views, read-only role]

    I --> J{products table empty<br/>AND SEED_ENABLED=true?}
    J -->|yes| K[DemoDataSeeder:<br/>6 months of orders, ~1s]
    J -->|no| L[Skip seeding]
    K --> M[Resolve the AI provider]
    L --> M

    M --> N{Which key is set?}
    N -->|ANTHROPIC_API_KEY| N1[Provider = ANTHROPIC]
    N -->|OPENAI_API_KEY<br/>+ OPENAI_BASE_URL| N2[Provider = OPENAI<br/>Groq / Gemini / OpenRouter / Ollama]
    N -->|neither| N3[Provider = NONE<br/>rule-based fallback]

    N1 --> O[/actuator/health returns UP/]
    N2 --> O
    N3 --> O
    O --> P[Platform routes traffic<br/>health check passes]
    P --> Q[Live: storefront, dashboard, Swagger UI]
```

The branch at `N` is the important one for a demo: **a missing API key is a supported
configuration, not a failure**. The app boots, the dashboard works, and every AI response
carries `aiGenerated: false` plus a visible "rule-based fallback" badge.

---

## 2. Required and optional configuration

| Variable | Required | Default | Notes |
|---|---|---|---|
| `DATABASE_URL` | **yes** | local Postgres | Either `jdbc:postgresql://host:5432/db` or a provider URI such as `postgresql://user:pass@host/db?sslmode=require` (converted at startup) |
| `DATABASE_USER` / `DATABASE_PASSWORD` | yes (unless in the URL) | `shoppulse` | |
| `JWT_SECRET` | **yes in production** | dev value | ≥ 32 bytes; the app refuses to start on a shorter one |
| `PORT` | no | `8080` | most platforms inject this |
| `SEED_ENABLED` | no | `true` | set `false` for a real deployment with real data |
| `AI_PROVIDER` | no | `auto` | `auto` \| `anthropic` \| `openai` \| `none` |
| `ANTHROPIC_API_KEY` | no | `not-set` | enables the Anthropic path |
| `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL` | no | `not-set` / OpenAI / `gpt-4o-mini` | any OpenAI-compatible endpoint |
| `AI_READONLY_DATABASE_URL` | no | empty | least-privilege connection for AI SQL |
| `BRIEFING_CRON` | no | `0 0 7 * * MON` | weekly briefing schedule |

> **One warning worth repeating:** `JWT_SECRET` has a development default so `docker compose up`
> works with no setup. Any deployment reachable from the internet must override it. `render.yaml`
> uses `generateValue: true` so Render does this for you.

---

## 3. Running it

### Local, everything included

```bash
docker compose up
```

Postgres, migrations, six months of seed data, app on `:8080`. No credentials needed.

### Render + Neon (the fastest permanent free link)

**Do not use Render's free Postgres for anything you want to keep.** Per Render's own docs it is
**deleted 30 days after creation** (14-day grace period to upgrade), with no backups. A portfolio
link backed by it stops working a month after you build it. `render.yaml` therefore creates only
the web service, and the database comes from **Neon**, whose free plan is permanent and needs no
card.

**1. Database — Neon (about 2 minutes)**
   - Sign up at [neon.com](https://neon.com) → create a project named `shoppulse`.
   - Copy the connection string. It looks like:
     `postgresql://user:password@ep-something-123.eu-central-1.aws.neon.tech/neondb?sslmode=require`
   - Keep `?sslmode=require` — Neon refuses plaintext connections.

**2. App — Render (about 10 minutes, mostly the first build)**
   - Push to GitHub.
   - Render → **New → Blueprint** → pick the repo. It reads `render.yaml`.
   - When prompted, paste the Neon string into `DATABASE_URL`. `JWT_SECRET` is generated for you.
     Leave the AI variables blank unless you want live AI (section 4).
   - First build takes roughly 5–10 minutes (Maven, inside Docker). Watch the logs for
     `Seeded demo data ...` and `Started ShopPulseApplication`.

`DATABASE_URL` accepts **either** form — Neon's `postgresql://user:pass@host/db` URI or a
`jdbc:postgresql://...` URL. The app converts the URI at startup and logs the redacted result,
so you can paste the provider's string without hand-editing it.

**Two free-tier behaviours to know before you send the link to anyone:**
- The Render free web service **sleeps after 15 minutes** of no traffic and takes roughly a
  minute to wake. Open the link yourself a minute before a demo or an interview.
- Neon's free compute also scales to zero and wakes in about a second.

Neither is a bug, and saying so first — "it's on a free tier, give it a minute to wake" — reads
much better than someone clicking a seemingly dead link.

### Railway / Fly.io / anything that takes a container

```bash
docker build -t shoppulse .
docker run -p 8080:8080 \
  -e DATABASE_URL='jdbc:postgresql://host:5432/shoppulse' \
  -e DATABASE_USER=... -e DATABASE_PASSWORD=... \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  shoppulse
```

Health check path: `/actuator/health`. Give it a 60-second start period — Flyway and seeding
run before the port opens.

---

## 4. Running the AI features without paying

The AI module talks to exactly one class, `ChatGateway`, and `AiAvailability` decides which
backend that class uses. Because `spring-ai-starter-model-openai` speaks the OpenAI wire
protocol, **any provider with an OpenAI-compatible endpoint works by changing three environment
variables and nothing else.** That covers most free tiers.

Limits below were published as of November 2026 and providers change them without notice —
check the provider's own page before relying on a number.

### Option A — Groq (recommended free option)

Fast, no card required, clearly published limits, and its terms do not claim training rights
over API data.

```bash
AI_PROVIDER=openai
OPENAI_API_KEY=gsk_...                              # console.groq.com/keys
OPENAI_BASE_URL=https://api.groq.com/openai/v1
OPENAI_MODEL=openai/gpt-oss-120b
```

Free plan on `gpt-oss-120b`: about 30 requests/min, 1,000 requests/day, 8K tokens/min,
200K tokens/day. The tokens-per-minute cap is the binding one here — ShopPulse's prompts are
small (a schema description plus a question), so this is comfortable for a demo.

### Option B — Google Gemini free tier

Widest free model range, via Google's OpenAI compatibility layer.

```bash
AI_PROVIDER=openai
OPENAI_API_KEY=AIza...                              # aistudio.google.com/apikey
OPENAI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
OPENAI_MODEL=gemini-2.5-flash
```

Caveat to know before you put a store's data through it: Google's terms say content sent to
the **unpaid** tier can be used to improve their products, and may be read by human reviewers.
Fine for seeded demo data, not for anything real. (ShopPulse masks customer emails before any
model call regardless — see `PiiMasker`.)

### Option C — OpenRouter free models

One key, many models, IDs ending in `:free`.

```bash
AI_PROVIDER=openai
OPENAI_API_KEY=sk-or-...                            # openrouter.ai/keys
OPENAI_BASE_URL=https://openrouter.ai/api/v1
OPENAI_MODEL=google/gemma-3-27b-it:free
```

About 20 requests/min and 50 requests/day without credit, rising to 1,000/day after a one-off
$10 top-up. Failed attempts count toward the cap, and the free model list rotates, so pin a
model you have tested and keep the fallback path working.

### Option D — fully local with Ollama (no key, no quota, no data leaving the machine)

```bash
ollama pull llama3.1:8b && ollama serve

AI_PROVIDER=openai
OPENAI_API_KEY=ollama                 # any non-empty value; Ollama ignores it
OPENAI_BASE_URL=http://localhost:11434/v1
OPENAI_MODEL=llama3.1:8b
```

From inside Docker, use `http://host.docker.internal:11434/v1`.

Honest note on quality: an 8B local model writes noticeably worse SQL than a frontier model.
This is the configuration where `SqlGuard` earns its keep — it rejects the malformed and
unsafe attempts, and the request falls through to the built-in query library rather than
failing. Worth demonstrating deliberately.

### Option E — no key at all (the default)

Leave everything unset. `AiAvailability` resolves to `NONE`, and:

- **Ask your store** matches the question against a 7-intent library of pre-written,
  parameterised SELECTs over the views, runs one, and summarises the rows deterministically.
- **Weekly briefing** writes its five bullets from the same computed numbers, recording the
  model as `rule-based`.
- **Anomaly explanation** states the arithmetic: this day versus the trailing 28-day average,
  which categories moved, which products moved.

Every response sets `aiGenerated: false` and the dashboard shows a badge saying so. Nothing
pretends to be a model.

---

## 5. Checking a deployment

```bash
BASE=https://your-app.onrender.com

curl -s $BASE/actuator/health                       # {"status":"UP"}

TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"merchant@shoppulse.dev","password":"merchant123"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s $BASE/api/v1/merchant/analytics/kpis -H "Authorization: Bearer $TOKEN"

# Which AI mode is live? Look at aiGenerated in the response.
curl -s -X POST $BASE/api/v1/merchant/ai/ask \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"question":"How did revenue trend over the last 30 days?"}'
```

The startup log prints `AI provider resolved to ANTHROPIC|OPENAI|NONE`, which is the quickest
way to confirm the keys arrived.

---

## 6. Things that will bite you

| Symptom | Cause | Fix |
|---|---|---|
| Crash loop at startup, Flyway error | bad `DATABASE_URL` | both JDBC and `postgresql://` URI forms are accepted; check the startup log line that prints the converted, redacted URL |
| `SSL required` / connection refused on Neon | `?sslmode=require` was dropped from the string | keep the full connection string exactly as Neon gives it |
| Deployed app worked, then died about a month later | you used Render's free Postgres | it is deleted 30 days after creation — use Neon, as `render.yaml` assumes |
| Out-of-memory during startup on Render free | 512 MB instance | `JAVA_OPTS` in `render.yaml` caps the heap at 70% and uses SerialGC |
| `Validation failed for query ... schema-validation` | database has an older schema | Hibernate is set to `validate`; let Flyway migrate, never hand-edit tables |
| App starts, dashboard is empty | `SEED_ENABLED=false`, or the products table is already non-empty | seeding only runs on an empty catalogue, by design, so restarts don't duplicate data |
| First request takes 20 seconds | free-tier container and database were asleep | expected on free plans; warm it before a demo |
| AI answers are all `aiGenerated: false` | no key reached the container | check the startup log line; `AI_PROVIDER=auto` ignores a key set under the wrong variable |
| 429 from `/ai/ask` | the built-in token bucket (20 questions/min) | deliberate, so a demo cannot burn an API budget |
| Migration `V3` logs "insufficient privileges" | managed Postgres won't let you `CREATE ROLE` | harmless and handled; the Java-side SQL guards still apply |
