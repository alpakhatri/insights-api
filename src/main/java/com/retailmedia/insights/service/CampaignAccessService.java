package com.retailmedia.insights.service;

import com.retailmedia.insights.domain.Campaign;
import com.retailmedia.insights.domain.CampaignNotFoundException;
import com.retailmedia.insights.repository.CampaignRepository;
import com.retailmedia.insights.security.CampaignAccessPolicy;
import com.retailmedia.insights.security.Caller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Loads a campaign and checks the caller may read it. Runs before any metric is read or cached. */
@Service
public class CampaignAccessService {

    private static final Logger log = LoggerFactory.getLogger(CampaignAccessService.class);

    private final CampaignRepository campaigns;

    public CampaignAccessService(CampaignRepository campaigns) {
        this.campaigns = campaigns;
    }

    /**
     * @throws CampaignNotFoundException if the campaign does not exist or belongs to someone else;
     *     the two cases are indistinguishable to the caller on purpose
     */
    public Campaign requireReadable(Caller caller, String campaignId) {
        return campaigns.findById(campaignId)
                .filter(campaign -> {
                    boolean allowed = CampaignAccessPolicy.canRead(caller, campaign);
                    if (!allowed) {
                        // Logged for security monitoring; never revealed in the response.
                        log.warn("Denied access to campaign {} for subject {} with role {}",
                                campaignId, caller.subject(), caller.role());
                    }
                    return allowed;
                })
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    }
}
