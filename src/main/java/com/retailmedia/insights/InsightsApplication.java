package com.retailmedia.insights;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Insights API: serves unique-customer counts (impressions, clicks, clickToBasket) per campaign.
 *
 * <p>Reads pre-aggregated, mergeable counts from ClickHouse. Closed days come from the reconciled
 * table (exact, final); open days come from the real-time table (fresh, preliminary).
 */
@SpringBootApplication
@EnableCaching
@ConfigurationPropertiesScan
public class InsightsApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightsApplication.class, args);
    }
}
