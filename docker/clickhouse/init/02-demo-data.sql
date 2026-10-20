-- Demo data for local runs. Dates are relative to today, so the demo always looks current:
--   * reconciled data for the last 14 days up to yesterday
--   * real-time data for today, up to the current hour
--
-- Tenants and campaigns:
--   tesco  c-1001  brand nestle   "KitKat Autumn"
--   tesco  c-1002  brand cadbury  "Dairy Milk Festive"
--   amzn   c-2001  brand nestle   "Nescafe Mornings"

INSERT INTO insights.campaigns (campaign_id, tenant_id, brand_id, name) VALUES
    ('c-1001', 'tesco', 'nestle',  'KitKat Autumn'),
    ('c-1002', 'tesco', 'cadbury', 'Dairy Milk Festive'),
    ('c-2001', 'amzn',  'nestle',  'Nescafe Mornings');

INSERT INTO insights.reconciliation_watermark (tenant_id, reconciled_through) VALUES
    ('tesco', today() - 1),
    ('amzn',  today() - 1);

-- Reconciled history: 14 days x 24 hours. Per campaign and hour: 2,000 impressions, 150 clicks
-- and 30 click-to-basket events, drawn from overlapping customer pools, so the range total is
-- well below the sum of the daily values (one customer on several days is counted once).
INSERT INTO insights.metrics_reconciled
    (tenant_id, campaign_id, ad_id, placement_id, metric, hour, customers, invalid_events)
SELECT tenant_id, campaign_id, 'ad-1', 'homepage', metric, hour,
       uniqExactState(customer_hash), toUInt64(round(count() * 0.04))
FROM
(
    SELECT c.1 AS tenant_id, c.2 AS campaign_id, m.1 AS metric,
           toDateTime(today() - 1 - intDiv(h.number, 24)) + toIntervalHour(h.number % 24) AS hour,
           cityHash64(c.1, (h.number * 1000003 + i * 7919) % m.2) AS customer_hash
    FROM numbers(14 * 24) AS h
    ARRAY JOIN [('tesco', 'c-1001'), ('tesco', 'c-1002'), ('amzn', 'c-2001')] AS c
    ARRAY JOIN [('impressions', 60000, 2000), ('clicks', 6000, 150), ('click_to_basket', 1500, 30)] AS m
    ARRAY JOIN range(m.3) AS i
)
GROUP BY tenant_id, campaign_id, metric, hour;

-- Today so far, from the real-time stream (same rates, up to the current hour).
INSERT INTO insights.metrics_realtime
    (tenant_id, campaign_id, ad_id, placement_id, metric, hour, customers)
SELECT tenant_id, campaign_id, 'ad-1', 'homepage', metric, hour, uniqExactState(customer_hash)
FROM
(
    SELECT c.1 AS tenant_id, c.2 AS campaign_id, m.1 AS metric,
           toDateTime(today()) + toIntervalHour(h.number) AS hour,
           cityHash64(c.1, (h.number * 1000003 + i * 7919 + 17) % m.2) AS customer_hash
    FROM numbers(toHour(now()) + 1) AS h
    ARRAY JOIN [('tesco', 'c-1001'), ('tesco', 'c-1002'), ('amzn', 'c-2001')] AS c
    ARRAY JOIN [('impressions', 60000, 2000), ('clicks', 6000, 150), ('click_to_basket', 1500, 30)] AS m
    ARRAY JOIN range(m.3) AS i
)
GROUP BY tenant_id, campaign_id, metric, hour;
