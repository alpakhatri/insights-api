package com.retailmedia.insights.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** An inclusive range of UTC calendar days. */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new InvalidRequestException("'from' (" + from + ") must be on or before 'to' (" + to + ")");
        }
    }

    /**
     * Builds the range a caller asked for, filling in defaults and enforcing limits.
     *
     * @param from optional start date; defaults to {@code defaultDays} ending on {@code to}
     * @param to optional end date; defaults to today
     * @param today today's date (UTC)
     * @param defaultDays length of the default range
     * @param maxDays longest range allowed, which bounds query cost
     */
    public static DateRange resolve(LocalDate from, LocalDate to, LocalDate today, int defaultDays, int maxDays) {
        LocalDate end = to != null ? to : today;
        if (end.isAfter(today)) {
            throw new InvalidRequestException("'to' cannot be in the future");
        }
        LocalDate start = from != null ? from : end.minusDays(defaultDays - 1L);
        DateRange range = new DateRange(start, end);
        if (range.lengthInDays() > maxDays) {
            throw new InvalidRequestException("Date range cannot be longer than " + maxDays + " days");
        }
        return range;
    }

    public long lengthInDays() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Every day in the range, in order. */
    public List<LocalDate> days() {
        return from.datesUntil(to.plusDays(1)).toList();
    }
}
