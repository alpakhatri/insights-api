package com.retailmedia.insights.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All service settings in one typed place, bound from the {@code insights.*} properties.
 * Invalid values fail the application at startup instead of at the first request.
 */
@Validated
@ConfigurationProperties(prefix = "insights")
public record InsightsProperties(
        @NotNull @Valid Security security,
        @NotNull @Valid RateLimit rateLimit,
        @NotNull @Valid Query query) {

    public record Security(@NotNull @Valid Jwt jwt) {
    }

    /**
     * Either {@code issuerUri} (production: keys fetched from the identity provider) or
     * {@code hmacSecret} (local development and tests) must be set.
     */
    public record Jwt(String issuerUri, String hmacSecret) {
    }

    /** Token bucket per tenant: {@code capacity} requests burst, refilled at {@code refillPerSecond}. */
    public record RateLimit(@Positive int capacity, @Positive double refillPerSecond) {
    }

    /** Limits on the date range a caller may ask for. */
    public record Query(@Positive int defaultRangeDays, @Positive int maxRangeDays) {
    }
}
