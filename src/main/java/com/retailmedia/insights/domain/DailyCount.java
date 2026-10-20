package com.retailmedia.insights.domain;

import java.time.LocalDate;

/**
 * Unique customers on one day.
 *
 * @param invalidRemoved events removed by reconciliation (bots, duplicates); {@code null} for
 *     preliminary days, where reconciliation has not run yet
 */
public record DailyCount(LocalDate date, long uniqueCustomers, Long invalidRemoved, DayStatus status) {

    /** A day with no recorded activity. */
    public static DailyCount empty(LocalDate date, DayStatus status) {
        return new DailyCount(date, 0, status == DayStatus.FINAL ? 0L : null, status);
    }
}
