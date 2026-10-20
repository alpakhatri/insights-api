package com.retailmedia.insights.service;

import com.retailmedia.insights.domain.DateRange;
import com.retailmedia.insights.domain.Metric;
import com.retailmedia.insights.domain.MetricResult;
import com.retailmedia.insights.security.Caller;
import org.springframework.stereotype.Service;

/**
 * Entry point for the controller: authorise first, then query.
 *
 * <p>Kept separate from {@link MetricsQueryService} so the access check always runs, even when
 * the result comes from the cache.
 */
@Service
public class CampaignMetricsService {

    private final CampaignAccessService access;
    private final MetricsQueryService queries;

    public CampaignMetricsService(CampaignAccessService access, MetricsQueryService queries) {
        this.access = access;
        this.queries = queries;
    }

    public MetricResult getMetric(Caller caller, String campaignId, Metric metric, DateRange range) {
        access.requireReadable(caller, campaignId);
        return queries.query(caller.tenantId(), campaignId, metric, range);
    }
}
