package com.retailmedia.insights.api;

import com.retailmedia.insights.config.InsightsProperties;
import com.retailmedia.insights.domain.DateRange;
import com.retailmedia.insights.domain.Metric;
import com.retailmedia.insights.security.Caller;
import com.retailmedia.insights.service.CampaignMetricsService;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three insight endpoints from the case study. They share one code path and differ only in
 * the metric they ask for.
 *
 * <p>Query parameters (both optional, ISO dates, UTC):
 * <ul>
 *   <li>{@code from}: first day, defaults to {@code to} minus the default range length
 *   <li>{@code to}: last day, defaults to today; cannot be in the future
 * </ul>
 *
 * <p>Responses: 200 with {@link MetricResponse}; 400 invalid input; 401 missing or invalid token;
 * 403 token without a tenant; 404 campaign unknown or not visible to the caller; 429 rate
 * limited; 503 data store unavailable.
 */
@RestController
@RequestMapping("/ad/{campaignId}")
public class CampaignMetricsController {

    private static final String CAMPAIGN_ID = "[A-Za-z0-9_-]{1,64}";

    private final CampaignMetricsService service;
    private final InsightsProperties.Query limits;
    private final Clock clock;

    public CampaignMetricsController(CampaignMetricsService service, InsightsProperties properties, Clock clock) {
        this.service = service;
        this.limits = properties.query();
        this.clock = clock;
    }

    /** Unique customers who saw the campaign's ads. */
    @GetMapping("/impressions")
    public MetricResponse impressions(
            Caller caller,
            @PathVariable @Pattern(regexp = CAMPAIGN_ID) String campaignId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return fetch(caller, campaignId, Metric.IMPRESSIONS, from, to);
    }

    /** Unique customers who clicked the campaign's ads. */
    @GetMapping("/clicks")
    public MetricResponse clicks(
            Caller caller,
            @PathVariable @Pattern(regexp = CAMPAIGN_ID) String campaignId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return fetch(caller, campaignId, Metric.CLICKS, from, to);
    }

    /** Unique customers who added a campaign product to the cart after clicking the ad. */
    @GetMapping("/clickToBasket")
    public MetricResponse clickToBasket(
            Caller caller,
            @PathVariable @Pattern(regexp = CAMPAIGN_ID) String campaignId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return fetch(caller, campaignId, Metric.CLICK_TO_BASKET, from, to);
    }

    private MetricResponse fetch(Caller caller, String campaignId, Metric metric, LocalDate from, LocalDate to) {
        DateRange range = DateRange.resolve(from, to, LocalDate.now(clock),
                limits.defaultRangeDays(), limits.maxRangeDays());
        return MetricResponse.from(service.getMetric(caller, campaignId, metric, range));
    }
}
