package com.retailmedia.insights.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.retailmedia.insights.domain.DailyCount;
import com.retailmedia.insights.domain.MetricResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * JSON response for all three metric endpoints. Separate from the domain model so the public API
 * contract can stay stable while internals change.
 *
 * <pre>
 * {
 *   "campaignId": "c-1001",
 *   "metric": "clicks",
 *   "from": "2026-10-13",
 *   "to": "2026-10-19",
 *   "uniqueCustomers": 18342,
 *   "reconciledThrough": "2026-10-18",
 *   "breakdown": [
 *     {"date": "2026-10-18", "uniqueCustomers": 2961, "status": "final", "invalidRemoved": 118},
 *     {"date": "2026-10-19", "uniqueCustomers": 1204, "status": "preliminary"}
 *   ],
 *   "generatedAt": "2026-10-19T14:05:00Z"
 * }
 * </pre>
 */
public record MetricResponse(
        String campaignId,
        String metric,
        LocalDate from,
        LocalDate to,
        long uniqueCustomers,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate reconciledThrough,
        List<Day> breakdown,
        Instant generatedAt) {

    /** One day in the breakdown. {@code invalidRemoved} only appears on final days. */
    public record Day(
            LocalDate date,
            long uniqueCustomers,
            String status,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long invalidRemoved) {

        static Day from(DailyCount count) {
            return new Day(count.date(), count.uniqueCustomers(),
                    count.status().name().toLowerCase(Locale.ROOT), count.invalidRemoved());
        }
    }

    public static MetricResponse from(MetricResult result) {
        return new MetricResponse(
                result.campaignId(),
                result.metric().apiName(),
                result.range().from(),
                result.range().to(),
                result.uniqueCustomers(),
                result.reconciledThrough(),
                result.breakdown().stream().map(Day::from).toList(),
                result.generatedAt());
    }
}
