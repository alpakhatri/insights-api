package com.retailmedia.insights.service;

import com.retailmedia.insights.domain.DailyCount;
import com.retailmedia.insights.domain.DateRange;
import com.retailmedia.insights.domain.Metric;
import com.retailmedia.insights.domain.MetricResult;
import com.retailmedia.insights.domain.RangeSplit;
import com.retailmedia.insights.repository.MetricsRepository;
import com.retailmedia.insights.repository.MetricsSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Builds a {@link MetricResult} from the two metric tables.
 *
 * <p>Only called after {@link CampaignAccessService} has approved the caller. Results are cached
 * per tenant, campaign, metric and range; the tenant is part of the key so cached data can never
 * be served across tenants.
 */
@Service
public class MetricsQueryService {

    static final String CACHE_NAME = "metrics";

    private final MetricsRepository metrics;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public MetricsQueryService(MetricsRepository metrics, MeterRegistry meterRegistry, Clock clock) {
        this.metrics = metrics;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "{#tenantId, #campaignId, #metric, #range}")
    public MetricResult query(String tenantId, String campaignId, Metric metric, DateRange range) {
        return Timer.builder("insights.query.duration")
                .description("Time to build a metric result from ClickHouse (cache misses only)")
                .tag("metric", metric.apiName())
                .tag("tenant", tenantId)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(() -> load(tenantId, campaignId, metric, range));
    }

    private MetricResult load(String tenantId, String campaignId, Metric metric, DateRange range) {
        LocalDate reconciledThrough = metrics.findReconciledThrough(tenantId).orElse(null);
        RangeSplit split = RangeSplit.of(range, reconciledThrough);

        Map<LocalDate, DailyCount> byDay = new HashMap<>();
        split.reconciled().ifPresent(part -> metrics
                .findDailyCounts(MetricsSource.RECONCILED, tenantId, campaignId, metric, part)
                .forEach(day -> byDay.put(day.date(), day)));
        split.realtime().ifPresent(part -> metrics
                .findDailyCounts(MetricsSource.REALTIME, tenantId, campaignId, metric, part)
                .forEach(day -> byDay.put(day.date(), day)));

        // One entry per day, so the dashboard never has to guess about gaps.
        List<DailyCount> breakdown = range.days().stream()
                .map(day -> byDay.getOrDefault(day, DailyCount.empty(day, split.statusOf(day))))
                .toList();

        long total = metrics.countUniqueCustomers(tenantId, campaignId, metric, split);

        return new MetricResult(campaignId, metric, range, total, reconciledThrough, breakdown, clock.instant());
    }
}
