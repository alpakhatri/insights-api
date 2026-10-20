package com.retailmedia.insights.domain;

/**
 * The three campaign metrics. Each counts unique customers, never raw events.
 *
 * <p>{@code apiName} is what clients see; {@code storedName} is the value in the ClickHouse
 * {@code metric} column. Keeping them separate lets either side change without breaking the other.
 */
public enum Metric {

    /** Unique customers who saw at least one of the campaign's ads. */
    IMPRESSIONS("impressions", "impressions"),

    /** Unique customers who clicked at least one of the campaign's ads. */
    CLICKS("clicks", "clicks"),

    /**
     * Unique customers who added a campaign product to the cart after clicking the ad, within the
     * tenant's attribution window. Attribution happens upstream in the stream processor.
     */
    CLICK_TO_BASKET("clickToBasket", "click_to_basket");

    private final String apiName;
    private final String storedName;

    Metric(String apiName, String storedName) {
        this.apiName = apiName;
        this.storedName = storedName;
    }

    public String apiName() {
        return apiName;
    }

    public String storedName() {
        return storedName;
    }
}
