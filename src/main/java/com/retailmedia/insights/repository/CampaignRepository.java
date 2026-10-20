package com.retailmedia.insights.repository;

import com.retailmedia.insights.domain.Campaign;
import java.util.Optional;

/** Looks up campaign ownership (tenant and brand). */
public interface CampaignRepository {

    Optional<Campaign> findById(String campaignId);
}
