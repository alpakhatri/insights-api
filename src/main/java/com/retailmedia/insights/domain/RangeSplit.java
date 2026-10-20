package com.retailmedia.insights.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Splits a requested range at the reconciliation boundary.
 *
 * <p>Days up to and including {@code reconciledThrough} are read from the reconciled table; later
 * days are read from the real-time table. Either part may be empty.
 *
 * <pre>
 *   requested:          |-------------------------|
 *   reconciledThrough:              ^
 *   reconciled part:    |-----------|
 *   realtime part:                   |------------|
 * </pre>
 */
public final class RangeSplit {

    private final DateRange reconciled;
    private final DateRange realtime;

    private RangeSplit(DateRange reconciled, DateRange realtime) {
        this.reconciled = reconciled;
        this.realtime = realtime;
    }

    /**
     * @param range the requested range
     * @param reconciledThrough last reconciled day for the tenant, or {@code null} if none yet
     */
    public static RangeSplit of(DateRange range, LocalDate reconciledThrough) {
        if (reconciledThrough == null || reconciledThrough.isBefore(range.from())) {
            return new RangeSplit(null, range);
        }
        if (!reconciledThrough.isBefore(range.to())) {
            return new RangeSplit(range, null);
        }
        return new RangeSplit(
                new DateRange(range.from(), reconciledThrough),
                new DateRange(reconciledThrough.plusDays(1), range.to()));
    }

    public Optional<DateRange> reconciled() {
        return Optional.ofNullable(reconciled);
    }

    public Optional<DateRange> realtime() {
        return Optional.ofNullable(realtime);
    }

    public DayStatus statusOf(LocalDate day) {
        return reconciled != null && !day.isAfter(reconciled.to()) ? DayStatus.FINAL : DayStatus.PRELIMINARY;
    }
}
