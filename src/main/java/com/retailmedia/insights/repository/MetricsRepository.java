package com.retailmedia.insights.repository;

import com.retailmedia.insights.domain.DailyCount;
import com.retailmedia.insights.domain.DateRange;
import com.retailmedia.insights.domain.Metric;
import com.retailmedia.insights.domain.RangeSplit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reads aggregated campaign metrics. Every method takes the tenant explicitly, and every query
 * filters on it, as a second line of defence behind the access check.
 */
public interface MetricsRepository {

    /** Last day the reconciliation job has finished for this tenant, if any. */
    Optional<LocalDate> findReconciledThrough(String tenantId);

    /** Unique customers per day from one source. Days with no data are omitted. */
    List<DailyCount> findDailyCounts(MetricsSource source, String tenantId, String campaignId,
                                     Metric metric, DateRange range);

    /**
     * Unique customers across the whole split range, merging both sources so a customer active on
     * a reconciled day and a real-time day is counted once.
     */
    long countUniqueCustomers(String tenantId, String campaignId, Metric metric, RangeSplit split);
}
