package com.retailmedia.insights.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RangeSplitTest {

    private static final LocalDate OCT_10 = LocalDate.of(2026, 10, 10);
    private static final LocalDate OCT_19 = LocalDate.of(2026, 10, 19);
    private static final DateRange RANGE = new DateRange(OCT_10, OCT_19);

    @Test
    void splitsAtReconciliationBoundary() {
        RangeSplit split = RangeSplit.of(RANGE, LocalDate.of(2026, 10, 18));

        assertThat(split.reconciled()).contains(new DateRange(OCT_10, LocalDate.of(2026, 10, 18)));
        assertThat(split.realtime()).contains(new DateRange(OCT_19, OCT_19));
        assertThat(split.statusOf(LocalDate.of(2026, 10, 18))).isEqualTo(DayStatus.FINAL);
        assertThat(split.statusOf(OCT_19)).isEqualTo(DayStatus.PRELIMINARY);
    }

    @Test
    void wholeRangeIsRealtimeWhenNothingReconciledYet() {
        RangeSplit split = RangeSplit.of(RANGE, null);

        assertThat(split.reconciled()).isEmpty();
        assertThat(split.realtime()).contains(RANGE);
    }

    @Test
    void wholeRangeIsRealtimeWhenBoundaryIsBeforeRange() {
        RangeSplit split = RangeSplit.of(RANGE, OCT_10.minusDays(1));

        assertThat(split.reconciled()).isEmpty();
        assertThat(split.realtime()).contains(RANGE);
    }

    @Test
    void wholeRangeIsReconciledWhenBoundaryCoversIt() {
        RangeSplit split = RangeSplit.of(RANGE, OCT_19);

        assertThat(split.reconciled()).contains(RANGE);
        assertThat(split.realtime()).isEmpty();
        assertThat(split.statusOf(OCT_19)).isEqualTo(DayStatus.FINAL);
    }

    @Test
    void boundaryOnFirstDayGivesOneReconciledDay() {
        RangeSplit split = RangeSplit.of(RANGE, OCT_10);

        assertThat(split.reconciled()).contains(new DateRange(OCT_10, OCT_10));
        assertThat(split.realtime()).contains(new DateRange(OCT_10.plusDays(1), OCT_19));
    }
}
