package com.retailmedia.insights.repository;

import com.retailmedia.insights.domain.DailyCount;
import com.retailmedia.insights.domain.DateRange;
import com.retailmedia.insights.domain.Metric;
import com.retailmedia.insights.domain.RangeSplit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * ClickHouse implementation of {@link MetricsRepository}.
 *
 * <p>Both metric tables are {@code AggregatingMergeTree} tables holding one
 * {@code uniqExactState(customer_hash)} per (tenant, campaign, ad, placement, metric, hour).
 * Unique counts cannot be added up, so queries merge these states with {@code uniqExactMerge}
 * instead. Because both tables use the same aggregate function, states from the two tables can be
 * merged together, which gives an exact total for ranges that span the reconciliation boundary.
 *
 * <p>Dates are passed as ISO strings and converted with {@code toDate(?)}, which avoids JDBC
 * time-zone conversion. The server stores and compares in UTC.
 */
@Repository
public class ClickHouseMetricsRepository implements MetricsRepository {

    private static final String RANGE_FILTER = """
            tenant_id = ? AND campaign_id = ? AND metric = ?
              AND hour >= toDateTime(toDate(?)) AND hour < toDateTime(toDate(?) + 1)""";

    private static final String RECONCILED_THROUGH = """
            SELECT toString(reconciled_through) AS reconciled_through
            FROM insights.reconciliation_watermark FINAL
            WHERE tenant_id = ?
            LIMIT 1
            """;

    private final JdbcTemplate jdbc;

    public ClickHouseMetricsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LocalDate> findReconciledThrough(String tenantId) {
        return jdbc.query(RECONCILED_THROUGH, (rs, row) -> LocalDate.parse(rs.getString(1)), tenantId)
                .stream()
                .findFirst();
    }

    @Override
    public List<DailyCount> findDailyCounts(MetricsSource source, String tenantId, String campaignId,
                                            Metric metric, DateRange range) {
        String invalidColumn = source == MetricsSource.RECONCILED
                ? "sum(invalid_events)"
                : "toUInt64(0)";
        String sql = """
                SELECT toString(toDate(hour)) AS day,
                       uniqExactMerge(customers) AS unique_customers,
                       %s AS invalid_removed
                FROM %s
                WHERE %s
                GROUP BY day
                ORDER BY day
                """.formatted(invalidColumn, source.table(), RANGE_FILTER);

        return jdbc.query(sql,
                (rs, row) -> new DailyCount(
                        LocalDate.parse(rs.getString("day")),
                        rs.getLong("unique_customers"),
                        source == MetricsSource.RECONCILED ? rs.getLong("invalid_removed") : null,
                        source.status()),
                rangeArgs(tenantId, campaignId, metric, range));
    }

    @Override
    public long countUniqueCustomers(String tenantId, String campaignId, Metric metric, RangeSplit split) {
        List<String> parts = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        split.reconciled().ifPresent(range -> {
            parts.add("SELECT customers FROM " + MetricsSource.RECONCILED.table() + " WHERE " + RANGE_FILTER);
            args.addAll(List.of(rangeArgs(tenantId, campaignId, metric, range)));
        });
        split.realtime().ifPresent(range -> {
            parts.add("SELECT customers FROM " + MetricsSource.REALTIME.table() + " WHERE " + RANGE_FILTER);
            args.addAll(List.of(rangeArgs(tenantId, campaignId, metric, range)));
        });

        String sql = "SELECT uniqExactMerge(customers) FROM (" + String.join(" UNION ALL ", parts) + ")";
        Long total = jdbc.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private static Object[] rangeArgs(String tenantId, String campaignId, Metric metric, DateRange range) {
        return new Object[] {
            tenantId, campaignId, metric.storedName(), range.from().toString(), range.to().toString()
        };
    }
}
