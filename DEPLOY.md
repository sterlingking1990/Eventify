# Deploying the Eventify backend

Verified by rehearsal on 11 Aug 2026: the packaged jar starts on the `prod`
profile, honours an injected `PORT`, passes `ddl-auto=validate` against the live
Supabase schema, and serves traffic. Measurements from that run are quoted below.

**No secrets in this file, or in any tracked file.** Every credential is read from
an environment variable set on the platform.

---

## Platform

**Railway, region `europe-west4`.**

Two measurements drove that choice:

- **Memory: 416 MB private / 381 MB working set, idle.** Render's Starter tier caps
  at 512 MB, leaving under 100 MB for all concurrent traffic, and its next tier up
  is $25/mo with nothing between. Railway bills actual usage on Hobby ($5/mo
  including $5 credit) with headroom to 8 GB.
- **Startup: 16–57 s, averaging ~32 s.** Render's free tier sleeps after 15 minutes
  of inactivity; a payment webhook arriving at a sleeping instance would likely
  time out, taking money without issuing a ticket. Disqualifying here.

Region matters for the database, not the user: one request makes several DB round
trips but only one trip to the browser. The Supabase `eventify` project is West EU
(Ireland), so put the app beside it.

---

## Environment variables

### Database
| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require` |
| `DB_USERNAME` | `postgres.<project-ref>` — the pooler username, **not** plain `postgres` |
| `DB_PASSWORD` | Supabase → Settings → Database |

Use the **session-mode pooler on 5432**. Direct connections are IPv6-only on newer
projects, and transaction mode on 6543 breaks Hibernate's prepared statements.

`DB_DRIVER` and `DB_SCHEMA` are already set by the `prod` profile — leave them unset.

### Application
| Variable | Notes |
|---|---|
| `SPRING_PROFILES_ACTIVE` | **`prod`** — without it Hibernate targets `public`, not `eventify` |
| `PORT` | injected by Railway; do not set manually |
| `JWT_SECRET` | **generate a fresh one.** Never reuse the local-dev value: anyone holding it can forge a token for any user id |

### Paystack
| Variable | Notes |
|---|---|
| `PAYSTACK_SECRET_KEY` | must be the **same account** Brandible's Supabase `PAYSTACK_SECRET_KEY` uses, or organiser balances span two accounts |
| `PAYSTACK_PUBLIC_KEY` | |

### QR hosting (Supabase Storage)
| Variable | Notes |
|---|---|
| `STORAGE_SUPABASE_URL` | `https://<project-ref>.supabase.co` |
| `STORAGE_SUPABASE_SERVICE_KEY` | service_role key |
| `STORAGE_SUPABASE_BUCKET` | defaults to `tickets`; the bucket must be **public** |

### WhatsApp integration
| Variable | Notes |
|---|---|
| `INTEGRATION_SERVICE_KEY` | shared secret for `/api/v1/integration/**`. Blank leaves those endpoints **closed**, not open |
| `INTEGRATION_DELIVERY_WEBHOOK_URL` | `https://<brandible-ref>.supabase.co/functions/v1/send-ticket-whatsapp` |
| `INTEGRATION_DELIVERY_API_KEY` | must equal `EVENTIFY_DELIVERY_KEY` in Brandible's Supabase secrets |

Delivery reconciliation is on by default: every 15 min it replays channel orders
paid in the last 48 h whose message may not have landed. The far end dedupes, so
worst case is a redundant webhook call — tune with
`INTEGRATION_DELIVERY_RECONCILE_MS` / `_MIN_AGE_MINUTES` / `_MAX_HOURS`.

### QR signing
| Variable | Notes |
|---|---|
| `QR_SIGNING_SECRET` | **generate a fresh one** (`openssl rand -hex 32`). Blank means new tickets get unsigned codes with no event binding — the app starts anyway and logs a warning. Rotating it makes future codes unverifiable against old signatures; only legacy-format codes keep working |

### URLs — the easiest thing to get wrong
| Variable | Notes |
|---|---|
| `APP_BASE_URL` | the Railway URL |
| `FRONTEND_URL` | **Paystack redirects buyers to `<FRONTEND_URL>/payment/success`.** Left at localhost, real buyers land on a dead address holding a paid ticket |
| `CORS_ALLOWED_ORIGINS` | must include the deployed frontend origin or every API call is blocked |

### Mail (optional)
`MAIL_USERNAME`, `MAIL_PASSWORD`. Sending is wrapped in a try/catch, so blank
values mean no email rather than a broken checkout.

---

## Platform settings

**Health check grace period: at least 90 seconds.** Startup was measured at up to
57 s. A shorter grace period kills the container mid-boot and loops forever — the
most common first-deploy failure for Spring Boot.

**No build config needed.** Railway detects the `Dockerfile`. It is multi-stage
(`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`) and resolves
dependencies in their own layer so source-only changes rebuild fast.

---

## After deploying

1. **Update Brandible's Supabase secret `EVENTIFY_API_BASE_URL`** to the Railway
   URL. Until then the WhatsApp flow still calls the ngrok tunnel.
2. **Tighten `EVENTIFY_TIMEOUT_MS`** in Brandible's secrets from 25 s to ~5 s.
   The 25 s default exists only because the tunnel measured 4.6–11.4 s per call;
   a datacentre-hosted backend answers in tens of milliseconds.
3. **Confirm Paystack's webhook URL** points at
   `https://<brandible-ref>.supabase.co/functions/v1/paystack-dva-callback`.
   Wrong or blank means payments succeed and nothing is fulfilled.
4. **Rotate the credentials still in git history** — the JWT signing secret above
   all, plus the Gmail app password and the original Paystack test keys. Removing
   them from the working file does not remove them from earlier commits.

---

## One-off: `event_scanners` table

Scanner accounts can now only check tickets in for events they were assigned to
(`POST /api/v1/events/{eventId}/scanners`, organizer-only). The assignment table
does not exist in a schema created before it, and `ddl-auto=validate` will refuse
to start until it does:

```sql
CREATE TABLE IF NOT EXISTS eventify.event_scanners (
    id         BIGSERIAL PRIMARY KEY,
    event_id   BIGINT      NOT NULL REFERENCES eventify.events (id),
    user_id    BIGINT      NOT NULL REFERENCES eventify.users (id),
    created_at TIMESTAMP,
    CONSTRAINT uq_event_scanners UNIQUE (event_id, user_id)
);
```

## One-off: backfill `releasable_at`

Organiser funds are released a fixed period after an event ends, driven by
`orders.releasable_at`. Orders created before that column existed have no value
and are treated as **held** — safe, but permanently locked until backfilled.

Run once against the database:

```sql
UPDATE eventify.orders o
SET releasable_at = e.end_date + interval '24 hours'
FROM eventify.events e
WHERE o.event_id = e.id
  AND o.releasable_at IS NULL;
```

Adjust the interval if `INTEGRATION_PAYOUT_HOLD_HOURS` is not 24.

Note this also means **`ddl-auto=validate` will refuse to start** until the new
columns exist (`orders.releasable_at`, `payouts.reference`, `payouts.bank_code`,
`payouts.failure_reason`). Either run the app once with `DDL_AUTO=update` to
create them, or add them by hand before deploying.

## Why `ddl-auto=validate` in production

Hibernate must never alter a production schema on boot. If entities and tables
disagree, **startup fails** — that is the signal to write a migration, not
something to work around. `DDL_AUTO=update` overrides it; do that knowingly.

Expect this to bite the first time a field is added and no migration is written.
That is the intended behaviour.

## Secrets and the image

`.dockerignore` excludes `application-local.properties`. This matters: the
Dockerfile runs `COPY src ./src`, so without that exclusion the local file — live
Paystack key, Supabase service-role key, database password — would be baked into
an image layer. Being git-ignored does not protect a Docker build context.
