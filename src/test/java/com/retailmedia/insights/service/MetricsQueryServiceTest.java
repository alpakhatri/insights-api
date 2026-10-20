package com.retailmedia.insights.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retailmedia.insights.domain.DailyCount;
import com.retailmedia.insights.domain.DateRange;
import com.retailmedia.insights.domain.DayStatus;
import com.retailmedia.insights.domain.Metric;
import com.retailmedia.insights.domain.MetricResult;
import com.retailmedia.insights.repository.MetricsRepository;
import com.retailmedia.insights.repository.MetricsSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetricsQueryServiceTest {

    private static final LocalDate OCT_17 = LocalDate.of(2026, 10, 17);
    private static final LocalDate OCT_18 = LocalDate.of(2026, 10, 18);
    private static final LocalDate OCT_19 = LocalDate.of(2026, 10, 19);
    private static final Instant NOW = Instant.parse("2026-10-19T14:05:00Z");

    private final MetricsRepository repository = mock(MetricsRepository.class);
    private final MetricsQueryService service = new MetricsQueryService(
            repository, new SimpleMeterRegistry(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void combinesReconciledAndRealtimeDays() {
        DateRange range = new DateRange(OCT_17, OCT_19);
        when(repository.findReconciledThrough("tesco")).thenReturn(Optional.of(OCT_18));
        when(repository.findDailyCounts(eq(MetricsSource.RECONCILED), eq("tesco"), eq("c-1001"), eq(Metric.CLICKS),
                eq(new DateRange(OCT_17, OCT_18))))
                .thenReturn(List.of(
                        new DailyCount(OCT_17, 900, 40L, DayStatus.FINAL),
                        new DailyCount(OCT_18, 950, 38L, DayStatus.FINAL)));
        when(repository.findDailyCounts(eq(MetricsSource.REALTIME), eq("tesco"), eq("c-1001"), eq(Metric.CLICKS),
                eq(new DateRange(OCT_19, OCT_19))))
                .thenReturn(List.of(new DailyCount(OCT_19, 410, null, DayStatus.PRELIMINARY)));
        when(repository.countUniqueCustomers(eq("tesco"), eq("c-1001"), eq(Metric.CLICKS), any()))
                .thenReturn(1800L);

        MetricResult result = service.query("tesco", "c-1001", Metric.CLICKS, range);

        assertThat(result.uniqueCustomers()).isEqualTo(1800);
        assertThat(result.reconciledThrough()).isEqualTo(OCT_18);
        assertThat(result.generatedAt()).isEqualTo(NOW);
        assertThat(result.breakdown())
                .extracting(DailyCount::date, DailyCount::uniqueCustomers, DailyCount::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(OCT_17, 900L, DayStatus.FINAL),
                        org.assertj.core.groups.Tuple.tuple(OCT_18, 950L, DayStatus.FINAL),
                        org.assertj.core.groups.Tuple.tuple(OCT_19, 410L, DayStatus.PRELIMINARY));
    }

    @Test
    void fillsDaysWithNoActivityWithZero() {
        DateRange range = new DateRange(OCT_17, OCT_18);
        when(repository.findReconciledThrough("tesco")).thenReturn(Optional.of(OCT_18));
        when(repository.findDailyCounts(eq(MetricsSource.RECONCILED), any(), any(), any(), any()))
                .thenReturn(List.of(new DailyCount(OCT_18, 120, 3L, DayStatus.FINAL)));

        MetricResult result = service.query("tesco", "c-1001", Metric.IMPRESSIONS, range);

        assertThat(result.breakdown()).first().isEqualTo(new DailyCount(OCT_17, 0, 0L, DayStatus.FINAL));
        verify(repository, never()).findDailyCounts(eq(MetricsSource.REALTIME), any(), any(), any(), any());
    }

    @Test
    void treatsEverythingAsPreliminaryWhenNothingReconciled() {
        DateRange range = new DateRange(OCT_18, OCT_19);
        when(repository.findReconciledThrough("tesco")).thenReturn(Optional.empty());
        when(repository.findDailyCounts(any(), any(), any(), any(), any())).thenReturn(List.of());

        MetricResult result = service.query("tesco", "c-1001", Metric.CLICK_TO_BASKET, range);

        assertThat(result.reconciledThrough()).isNull();
        assertThat(result.breakdown()).allSatisfy(day -> assertThat(day.status()).isEqualTo(DayStatus.PRELIMINARY));
        verify(repository, never()).findDailyCounts(eq(MetricsSource.RECONCILED), any(), any(), any(), any());
    }
}
