package com.retailmedia.insights.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.retailmedia.insights.domain.Campaign;
import org.junit.jupiter.api.Test;

class CampaignAccessPolicyTest {

    private static final Campaign TESCO_NESTLE = new Campaign("c-1001", "tesco", "nestle");

    @Test
    void retailerSeesAnyCampaignInOwnTenant() {
        Caller tescoAnalyst = new Caller("u1", "tesco", Role.RETAILER, null);

        assertThat(CampaignAccessPolicy.canRead(tescoAnalyst, TESCO_NESTLE)).isTrue();
    }

    @Test
    void retailerCannotSeeAnotherTenantsCampaign() {
        Caller amazonAnalyst = new Caller("u2", "amzn", Role.RETAILER, null);

        assertThat(CampaignAccessPolicy.canRead(amazonAnalyst, TESCO_NESTLE)).isFalse();
    }

    @Test
    void brandSeesOwnCampaign() {
        Caller nestleOnTesco = new Caller("u3", "tesco", Role.BRAND, "nestle");

        assertThat(CampaignAccessPolicy.canRead(nestleOnTesco, TESCO_NESTLE)).isTrue();
    }

    @Test
    void brandCannotSeeCompetitorsCampaignOnSameRetailer() {
        Caller cadburyOnTesco = new Caller("u4", "tesco", Role.BRAND, "cadbury");

        assertThat(CampaignAccessPolicy.canRead(cadburyOnTesco, TESCO_NESTLE)).isFalse();
    }

    @Test
    void brandCannotSeeOwnBrandOnAnotherRetailer() {
        Caller nestleOnAmazon = new Caller("u5", "amzn", Role.BRAND, "nestle");

        assertThat(CampaignAccessPolicy.canRead(nestleOnAmazon, TESCO_NESTLE)).isFalse();
    }
}
