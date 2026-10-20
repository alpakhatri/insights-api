package com.retailmedia.insights.domain;

/**
 * The campaign does not exist, or the caller may not see it (mapped to HTTP 404).
 *
 * <p>Both cases deliberately look the same, so a caller cannot probe which campaign IDs exist in
 * other tenants.
 */
public class CampaignNotFoundException extends RuntimeException {

    private final String campaignId;

    public CampaignNotFoundException(String campaignId) {
        super("Campaign '" + campaignId + "' was not found");
        this.campaignId = campaignId;
    }

    public String campaignId() {
        return campaignId;
    }
}
