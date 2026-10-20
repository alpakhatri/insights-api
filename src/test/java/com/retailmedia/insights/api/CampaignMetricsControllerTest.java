package com.retailmedia.insights.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retailmedia.insights.config.ClockConfig;
import com.retailmedia.insights.config.InsightsProperties;
import com.retailmedia.insights.config.SecurityConfig;
import com.retailmedia.insights.domain.CampaignNotFoundException;
import com.retailmedia.insights.domain.DailyCount;
import com.retailmedia.insights.domain.DateRange;
import com.retailmedia.insights.domain.DayStatus;
import com.retailmedia.insights.domain.Metric;
import com.retailmedia.insights.domain.MetricResult;
import com.retailmedia.insights.security.Caller;
import com.retailmedia.insights.security.Role;
import com.retailmedia.insights.service.CampaignMetricsService;
import com.retailmedia.insights.web.RequestIdFilter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP contract: status codes, JSON shape, security and validation. The service is mocked. */
@WebMvcTest(CampaignMetricsController.class)
@Import({SecurityConfig.class, ClockConfig.class})
@EnableConfigurationProperties(InsightsProperties.class)
@TestPropertySource(properties = {
    "insights.security.jwt.hmac-secret=test-secret-test-secret-test-secret-123",
    "insights.rate-limit.capacity=100",
    "insights.rate-limit.refill-per-second=100",
    "insights.query.default-range-days=7",
    "insights.query.max-range-days=400"
})
class CampaignMetricsControllerTest {

    private static final LocalDate YESTERDAY = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CampaignMetricsService service;

    @Test
    void returnsMetricWithDailyBreakdown() throws Exception {
        when(service.getMetric(any(), eq("c-1001"), eq(Metric.CLICKS), any())).thenReturn(sampleResult());

        mvc.perform(get("/ad/c-1001/clicks").with(tescoRetailer()))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER))
                .andExpect(jsonPath("$.campaignId").value("c-1001"))
                .andExpect(jsonPath("$.metric").value("clicks"))
                .andExpect(jsonPath("$.uniqueCustomers").value(1800))
                .andExpect(jsonPath("$.reconciledThrough").value(YESTERDAY.toString()))
                .andExpect(jsonPath("$.breakdown[0].status").value("final"))
                .andExpect(jsonPath("$.breakdown[0].invalidRemoved").value(38))
                .andExpect(jsonPath("$.breakdown[1].status").value("preliminary"))
                .andExpect(jsonPath("$.breakdown[1].invalidRemoved").doesNotExist());

        verify(service).getMetric(eq(new Caller("user-1", "tesco", Role.RETAILER, null)), eq("c-1001"),
                eq(Metric.CLICKS), any());
    }

    @Test
    void eachEndpointAsksForItsOwnMetric() throws Exception {
        when(service.getMetric(any(), any(), any(), any())).thenReturn(sampleResult());

        mvc.perform(get("/ad/c-1001/impressions").with(tescoRetailer())).andExpect(status().isOk());
        mvc.perform(get("/ad/c-1001/clickToBasket").with(tescoRetailer())).andExpect(status().isOk());

        verify(service).getMetric(any(), eq("c-1001"), eq(Metric.IMPRESSIONS), any());
        verify(service).getMetric(any(), eq("c-1001"), eq(Metric.CLICK_TO_BASKET), any());
    }

    @Test
    void passesRequestedDateRange() throws Exception {
        when(service.getMetric(any(), any(), any(), any())).thenReturn(sampleResult());
        LocalDate from = TODAY.minusDays(30);

        mvc.perform(get("/ad/c-1001/clicks").param("from", from.toString()).param("to", TODAY.toString())
                        .with(tescoRetailer()))
                .andExpect(status().isOk());

        verify(service).getMetric(any(), any(), any(), eq(new DateRange(from, TODAY)));
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mvc.perform(get("/ad/c-1001/clicks")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutTenant() throws Exception {
        mvc.perform(get("/ad/c-1001/clicks").with(jwt().jwt(j -> j.claim("role", "RETAILER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unknownOrForeignCampaignIsNotFound() throws Exception {
        when(service.getMetric(any(), eq("c-9999"), any(), any())).thenThrow(new CampaignNotFoundException("c-9999"));

        mvc.perform(get("/ad/c-9999/clicks").with(tescoRetailer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Campaign not found"));
    }

    @Test
    void rejectsFromAfterTo() throws Exception {
        mvc.perform(get("/ad/c-1001/clicks").param("from", TODAY.toString())
                        .param("to", YESTERDAY.toString()).with(tescoRetailer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("must be on or before")));
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mvc.perform(get("/ad/c-1001/clicks").param("from", "19-10-2026").with(tescoRetailer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidCampaignId() throws Exception {
        mvc.perform(get("/ad/{id}/clicks", "bad id!").with(tescoRetailer()))
                .andExpect(status().isBadRequest());
    }

    private static JwtRequestPostProcessor tescoRetailer() {
        return jwt().jwt(j -> j.subject("user-1").claim("tenant_id", "tesco").claim("role", "RETAILER"));
    }

    private static MetricResult sampleResult() {
        return new MetricResult("c-1001", Metric.CLICKS, new DateRange(YESTERDAY, TODAY), 1800, YESTERDAY,
                List.of(new DailyCount(YESTERDAY, 950, 38L, DayStatus.FINAL),
                        new DailyCount(TODAY, 410, null, DayStatus.PRELIMINARY)),
                Instant.parse("2026-10-19T14:05:00Z"));
    }
}
