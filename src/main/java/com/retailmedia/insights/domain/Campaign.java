package com.retailmedia.insights.domain;

/**
 * The parts of a campaign this service needs for access control.
 *
 * @param tenantId the retailer that runs the campaign
 * @param brandId the advertiser that paid for it
 */
public record Campaign(String campaignId, String tenantId, String brandId) {
}
