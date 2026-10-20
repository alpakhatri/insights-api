package com.retailmedia.insights.repository;

import com.retailmedia.insights.domain.Campaign;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads campaign ownership from a ClickHouse table.
 *
 * <p>Simplification for this exercise: in the full platform the source of truth is the Ads
 * Campaigns DB (PostgreSQL), replicated here by CDC. Swapping the implementation does not touch
 * the service layer.
 */
@Repository
public class ClickHouseCampaignRepository implements CampaignRepository {

    private static final String FIND_BY_ID = """
            SELECT campaign_id, tenant_id, brand_id
            FROM insights.campaigns FINAL
            WHERE campaign_id = ?
            LIMIT 1
            """;

    private final JdbcTemplate jdbc;

    public ClickHouseCampaignRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Campaign> findById(String campaignId) {
        return jdbc.query(FIND_BY_ID,
                        (rs, row) -> new Campaign(rs.getString("campaign_id"), rs.getString("tenant_id"),
                                rs.getString("brand_id")),
                        campaignId)
                .stream()
                .findFirst();
    }
}
