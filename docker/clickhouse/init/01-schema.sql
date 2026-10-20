-- Insights store schema.
--
-- Metric tables hold one mergeable unique-count state per
-- (tenant, campaign, ad, placement, metric, hour). Unique counts cannot be summed, so the API
-- merges states with uniqExactMerge for any time range. Both tables use the same aggregate
-- function so their states can be merged together across the reconciliation boundary.
--
-- customer_hash = cityHash64(tenant_id || ':' || customer_id), computed upstream, so raw customer
-- IDs never reach this store.

CREATE DATABASE IF NOT EXISTS insights;

-- Campaign ownership, used for access control.
-- In the full platform this is replicated from the Ads Campaigns DB (PostgreSQL) by CDC.
CREATE TABLE IF NOT EXISTS insights.campaigns
(
    campaign_id String,
    tenant_id   LowCardinality(String),
    brand_id    LowCardinality(String),
    name        String,
    updated_at  DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY campaign_id;

-- Written by the stream processor (Kafka Streams -> attributed_events -> ClickHouse sink).
-- Fresh, preliminary. Kept only until reconciliation has replaced each day.
CREATE TABLE IF NOT EXISTS insights.metrics_realtime
(
    tenant_id    LowCardinality(String),
    campaign_id  String,
    ad_id        String,
    placement_id LowCardinality(String),
    metric       LowCardinality(String),   -- impressions | clicks | click_to_basket
    hour         DateTime,
    customers    AggregateFunction(uniqExact, UInt64)
)
ENGINE = AggregatingMergeTree
PARTITION BY toYYYYMMDD(hour)
ORDER BY (tenant_id, campaign_id, metric, hour, ad_id, placement_id)
TTL hour + INTERVAL 3 DAY;

-- Rebuilt nightly by the Spark reconciliation job from S3, one whole day at a time
-- (ALTER TABLE ... REPLACE PARTITION). Exact, final.
CREATE TABLE IF NOT EXISTS insights.metrics_reconciled
(
    tenant_id      LowCardinality(String),
    campaign_id    String,
    ad_id          String,
    placement_id   LowCardinality(String),
    metric         LowCardinality(String),
    hour           DateTime,
    customers      AggregateFunction(uniqExact, UInt64),
    invalid_events SimpleAggregateFunction(sum, UInt64)   -- bots and duplicates removed
)
ENGINE = AggregatingMergeTree
PARTITION BY toYYYYMMDD(hour)
ORDER BY (tenant_id, campaign_id, metric, hour, ad_id, placement_id)
TTL hour + INTERVAL 25 MONTH;

-- Last fully reconciled day per tenant. Written by the reconciliation job after each run.
CREATE TABLE IF NOT EXISTS insights.reconciliation_watermark
(
    tenant_id          LowCardinality(String),
    reconciled_through Date,
    updated_at         DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY tenant_id;
