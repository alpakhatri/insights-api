package com.retailmedia.insights.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void defaultsToLastSevenDaysEndingToday() {
        DateRange range = DateRange.resolve(null, null, TODAY, 7, 400);

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(range.to()).isEqualTo(TODAY);
        assertThat(range.lengthInDays()).isEqualTo(7);
    }

    @Test
    void defaultStartIsRelativeToRequestedEnd() {
        DateRange range = DateRange.resolve(null, LocalDate.of(2026, 9, 30), TODAY, 7, 400);

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 9, 24));
    }

    @Test
    void singleDayRangeIsAllowed() {
        DateRange range = DateRange.resolve(TODAY, TODAY, TODAY, 7, 400);

        assertThat(range.days()).containsExactly(TODAY);
    }

    @Test
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> DateRange.resolve(TODAY, TODAY.minusDays(1), TODAY, 7, 400))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must be on or before");
    }

    @Test
    void rejectsFutureEnd() {
        assertThatThrownBy(() -> DateRange.resolve(null, TODAY.plusDays(1), TODAY, 7, 400))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("future");
    }

    @Test
    void rejectsRangeLongerThanMaximum() {
        assertThatThrownBy(() -> DateRange.resolve(TODAY.minusDays(400), TODAY, TODAY, 7, 400))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("400 days");
    }

    @Test
    void acceptsRangeExactlyAtMaximum() {
        DateRange range = DateRange.resolve(TODAY.minusDays(399), TODAY, TODAY, 7, 400);

        assertThat(range.lengthInDays()).isEqualTo(400);
    }
}
