# Insights API

Campaign insights for a multi-retailer retail media platform. This service is the read side of the
streaming platform: it answers how many **unique customers** saw, clicked, and clicked-then-added-to-basket
for an ad campaign, within seconds for today and exactly for every reconciled day.

| Endpoint | Returns |
| --- | --- |
| `GET /ad/{campaignId}/impressions` | Unique customers who saw the campaign's ads |
| `GET /ad/{campaignId}/clicks` | Unique customers who clicked |
| `GET /ad/{campaignId}/clickToBasket` | Unique customers who added a campaign product to the cart after clicking, within the attribution window |

Optional query parameters: `from`, `to` (ISO dates, UTC). Default: the last 7 days ending today. Maximum 400 days.

## Where it fits

```
Customer events → Kafka → Kafka Streams (dedupe, enrich, click→basket join) → ClickHouse metrics_realtime
                    └→ S3 (Parquet) → nightly Spark reconciliation ─────────→ ClickHouse metrics_reconciled
                                                                                   │
Retailer / brand dashboards → API Gateway (JWT) → **Insights API** ←────────────────┘
```

The service never touches raw events. It reads hourly, pre-aggregated rows, so a six-month query scans
thousands of rows rather than billions of events.

## Response

```json
{
  "campaignId": "c-1001",
  "metric": "clicks",
  "from": "2026-10-13",
  "to": "2026-10-19",
  "uniqueCustomers": 6000,
  "reconciledThrough": "2026-10-18",
  "breakdown": [
    { "date": "2026-10-13", "uniqueCustomers": 3599, "status": "final", "invalidRemoved": 144 },
    { "date": "2026-10-19", "uniqueCustomers": 3150, "status": "preliminary" }
  ],
  "generatedAt": "2026-10-19T14:05:00Z"
}
```

- `uniqueCustomers` is the unique count for the **whole range**. It is not the sum of the days: a customer
  active on several days counts once.
- `status` tells the dashboard whether a day is `final` (reconciled) or `preliminary` (real-time, may still move).
- `invalidRemoved` shows how many events reconciliation removed (bots, duplicates) on final days, so brands
  can see why a number changed.

Errors use [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `application/problem+json`:

| Status | When |
| --- | --- |
| 400 | Malformed date, `from` after `to`, future `to`, range over 400 days, invalid campaign ID |
| 401 | Missing, expired or invalid token |
| 403 | Token without `tenant_id` or a valid `role` |
| 404 | Campaign does not exist **or** belongs to another tenant or brand (deliberately indistinguishable) |
| 429 | Tenant over its rate limit (`Retry-After` header set) |
| 503 | ClickHouse unavailable |

## Run it locally

Requirements: Docker, Python 3 (for the token script). Java 21 and Maven 3.9 only to build or test outside Docker.

```bash
docker compose up --build
```

ClickHouse starts with the schema and demo data (two retailers, three campaigns, 14 reconciled days plus today).

```bash
# A Tesco analyst sees every Tesco campaign
TOKEN=$(python3 scripts/make_token.py --tenant tesco --role RETAILER)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/ad/c-1001/clicks | jq
curl -s -H "Authorization: Bearer $TOKEN" "localhost:8080/ad/c-1001/impressions?from=2026-10-10" | jq

# Nestlé on Tesco sees its own campaign, but not Cadbury's (404)
BRAND=$(python3 scripts/make_token.py --tenant tesco --role BRAND --brand nestle)
curl -s -H "Authorization: Bearer $BRAND" localhost:8080/ad/c-1001/clickToBasket | jq
curl -s -H "Authorization: Bearer $BRAND" localhost:8080/ad/c-1002/clicks | jq

# Amazon cannot see a Tesco campaign (404)
AMZN=$(python3 scripts/make_token.py --tenant amzn --role RETAILER)
curl -s -H "Authorization: Bearer $AMZN" localhost:8080/ad/c-1001/clicks | jq

# Operations
curl -s localhost:8080/actuator/health | jq
curl -s localhost:8080/actuator/prometheus | grep insights_
```

## Tests

```bash
mvn verify
```

Built and tested on Java 21 (`java -version` should show 21). On macOS with Homebrew:
`export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"`.

| Test | Covers |
| --- | --- |
| `DateRangeTest`, `RangeSplitTest` | Defaults, limits, and splitting a range at the reconciliation boundary |
| `CampaignAccessPolicyTest`, `CallerTest` | Tenant and brand isolation; token claim validation |
| `TokenBucketTest` | Rate limiting, with an injected clock (no sleeps) |
| `MetricsQueryServiceTest`, `CampaignMetricsServiceTest` | Combining sources, filling empty days, access checked before any query |
| `CampaignMetricsControllerTest` | HTTP contract: JSON shape, 400 / 401 / 403 / 404 |
| `InsightsApiIntegrationTest` | End to end on real ClickHouse (Testcontainers): unique customers merged across reconciled and real-time days. Skipped if Docker is not running. |

CI runs the full suite on every push (`.github/workflows/ci.yml`).

## Design decisions

**Layering.** `api` (HTTP contract) → `service` (rules) → `repository` (SQL). The domain model has no
framework dependencies, so its rules are unit-tested without Spring. Response DTOs are separate from domain
objects, so the public contract stays stable when internals change.

**Tenant comes only from the verified token.** The path never carries a tenant. `TenantContextFilter`
builds a `Caller` from the JWT claims; controllers receive it as a method argument. Every SQL query also
filters on `tenant_id`, a second line of defence behind the access check.

**404, not 403, for other tenants' campaigns.** Returning 403 would confirm the campaign exists. Denials
are still logged as warnings for security monitoring.

**Authorise before cache.** `CampaignMetricsService` checks access on every request, then calls the cached
`MetricsQueryService`. The cache key includes the tenant, so cached results can never cross tenants.

**Unique counts are merged, not added.** ClickHouse stores one `uniqExactState(customer_hash)` per
tenant, campaign, ad, placement, metric and hour. Any range is answered with `uniqExactMerge`. Both tables
use the same function, so states merge across the reconciliation boundary and the range total stays exact.
Trade-off: exact states use more memory than an approximate sketch. For very large campaigns the real-time
table could switch to `uniqCombined64` (about 1% error), with mixed-range totals then labelled approximate.

**Real-time vs reconciled.** `reconciliation_watermark` holds the last final day per tenant. `RangeSplit`
reads days up to it from `metrics_reconciled` and later days from `metrics_realtime`. The nightly job
replaces whole day partitions, so readers never see a half-written day.

**Defensive limits.** Range capped at 400 days. Per-tenant token bucket (429 + `Retry-After`). JDBC
connection timeout of 3 s, so a slow store fails fast with 503 rather than queueing requests. Graceful
shutdown for zero-downtime deploys.

**Observability.**
- Logs are structured JSON with `request_id` and `tenant_id` on every line. The request ID is taken from
  the gateway's `X-Request-Id` header or generated, and returned in the response.
- Metrics: `http_server_requests` (latency histogram by endpoint and status), `insights_query_duration`
  (by metric and tenant, cache misses only), `insights_ratelimit_rejected_total`, cache hit ratio,
  connection pool usage.
- Liveness and readiness probes at `/actuator/health/liveness` and `/readiness` for Kubernetes.

## Project structure

```
src/main/java/com/retailmedia/insights/
  api/          Controller and response DTO
  service/      Access check, then cached query
  domain/       Metric, DateRange, RangeSplit, results: no framework code
  repository/   ClickHouse SQL
  security/     Caller, access policy, tenant filter, rate limiting
  web/          Request IDs, error responses
  config/       Security, typed properties, clock
docker/clickhouse/init/   Schema and demo data
scripts/make_token.py     Local JWT minting
```

## Simplified for this exercise

| Here | In production |
| --- | --- |
| HS256 shared secret for local tokens | `JWT_ISSUER_URI`: keys from the identity provider (Keycloak, Cognito) |
| Campaign ownership in a ClickHouse table | Ads Campaigns DB (PostgreSQL), replicated by CDC and cached |
| In-memory cache, 30 s TTL | Redis shared across instances; closed days cached for hours since they never change |
| Rate limit per instance | Cluster-wide limit at the API gateway; this one stays as a safety net |
| Single ClickHouse node | Sharded by tenant, two replicas across availability zones |
| Daily granularity | `granularity=hour` and breakdowns by ad, placement or device |
