package com.retailmedia.insights.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The answer to one metric query.
 *
 * @param uniqueCustomers unique customers across the whole range. Not the sum of the daily values:
 *     a customer active on several days is counted once.
 * @param reconciledThrough last final day for the tenant, or {@code null} if nothing is reconciled yet
 * @param breakdown one entry per day in the range, oldest first
 */
public record MetricResult(
        String campaignId,
        Metric metric,
        DateRange range,
        long uniqueCustomers,
        LocalDate reconciledThrough,
        List<DailyCount> breakdown,
        Instant generatedAt) {

    public MetricResult {
        breakdown = List.copyOf(breakdown);
    }
}
