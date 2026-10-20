package com.retailmedia.insights.security;

import com.retailmedia.insights.config.InsightsProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * One token bucket per tenant, so one busy retailer cannot slow the API for the others.
 *
 * <p>Limits are per instance. In production the gateway enforces the shared, cluster-wide limit;
 * this is a local safety net that also protects ClickHouse.
 */
public class TenantRateLimiter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final InsightsProperties.RateLimit limits;
    private final LongSupplier nanoTime;

    public TenantRateLimiter(InsightsProperties.RateLimit limits, LongSupplier nanoTime) {
        this.limits = limits;
        this.nanoTime = nanoTime;
    }

    public TokenBucket bucketFor(String tenantId) {
        return buckets.computeIfAbsent(tenantId,
                id -> new TokenBucket(limits.capacity(), limits.refillPerSecond(), nanoTime));
    }
}
