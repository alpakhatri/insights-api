package com.retailmedia.insights.security;

import com.retailmedia.insights.domain.Campaign;

/**
 * Decides whether a caller may read a campaign's insights. Pure logic, so every rule is unit-tested.
 *
 * <ul>
 *   <li>The campaign must belong to the caller's tenant (retailer).
 *   <li>A brand user may only read their own brand's campaigns.
 * </ul>
 */
public final class CampaignAccessPolicy {

    private CampaignAccessPolicy() {
    }

    public static boolean canRead(Caller caller, Campaign campaign) {
        if (!campaign.tenantId().equals(caller.tenantId())) {
            return false;
        }
        return caller.role() != Role.BRAND || caller.brandId().equals(campaign.brandId());
    }
}
