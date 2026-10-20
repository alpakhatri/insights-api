package com.retailmedia.insights.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.retailmedia.insights.domain.Campaign;
import com.retailmedia.insights.domain.CampaignNotFoundException;
import com.retailmedia.insights.domain.DateRange;
import com.retailmedia.insights.domain.Metric;
import com.retailmedia.insights.repository.CampaignRepository;
import com.retailmedia.insights.security.Caller;
import com.retailmedia.insights.security.Role;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CampaignMetricsServiceTest {

    private static final DateRange RANGE = new DateRange(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 19));

    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final MetricsQueryService queries = mock(MetricsQueryService.class);
    private final CampaignMetricsService service =
            new CampaignMetricsService(new CampaignAccessService(campaigns), queries);

    @Test
    void queriesWithCallersTenantWhenAllowed() {
        when(campaigns.findById("c-1001")).thenReturn(Optional.of(new Campaign("c-1001", "tesco", "nestle")));
        Caller caller = new Caller("u1", "tesco", Role.BRAND, "nestle");

        service.getMetric(caller, "c-1001", Metric.CLICKS, RANGE);

        verify(queries).query("tesco", "c-1001", Metric.CLICKS, RANGE);
    }

    @Test
    void unknownCampaignIsNotFound() {
        when(campaigns.findById("c-404")).thenReturn(Optional.empty());
        Caller caller = new Caller("u1", "tesco", Role.RETAILER, null);

        assertThatThrownBy(() -> service.getMetric(caller, "c-404", Metric.CLICKS, RANGE))
                .isInstanceOf(CampaignNotFoundException.class);
        verifyNoInteractions(queries);
    }

    @Test
    void otherTenantsCampaignLooksNotFoundAndIsNeverQueried() {
        when(campaigns.findById(any())).thenReturn(Optional.of(new Campaign("c-1001", "tesco", "nestle")));
        Caller amazon = new Caller("u2", "amzn", Role.RETAILER, null);

        assertThatThrownBy(() -> service.getMetric(amazon, "c-1001", Metric.CLICKS, RANGE))
                .isInstanceOf(CampaignNotFoundException.class);
        verifyNoInteractions(queries);
    }
}
