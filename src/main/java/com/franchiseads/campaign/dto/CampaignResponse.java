package com.franchiseads.campaign.dto;

import com.franchiseads.campaign.Campaign;
import java.time.Instant;

public record CampaignResponse(Long id, Long brandId, String name, String theme, Instant createdAt) {

	public static CampaignResponse from(Campaign campaign) {
		return new CampaignResponse(campaign.getId(), campaign.getBrand().getId(), campaign.getName(),
				campaign.getTheme(), campaign.getCreatedAt());
	}

}
