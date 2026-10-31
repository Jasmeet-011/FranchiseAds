package com.franchiseads.generation;

import com.franchiseads.brand.Brand;
import com.franchiseads.campaign.Campaign;
import com.franchiseads.campaign.CampaignService;
import com.franchiseads.location.Location;
import com.franchiseads.location.LocationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 1 of the generate flow: loads the campaign, location and brand in one read-only transaction and copies what
 * the prompt needs into plain values. A separate bean on purpose: {@link GenerationService} calls it through the
 * Spring proxy, so the transaction applies and ends before any Claude call (ADR-029).
 */
@Component
public class GenerationInputLoader {

	private final CampaignService campaignService;

	private final LocationService locationService;

	GenerationInputLoader(CampaignService campaignService, LocationService locationService) {
		this.campaignService = campaignService;
		this.locationService = locationService;
	}

	/**
	 * @throws com.franchiseads.common.error.ResourceNotFoundException unknown campaign or location
	 * @throws LocationBrandMismatchException the location belongs to another brand
	 */
	@Transactional(readOnly = true)
	public GenerationInputs load(Long campaignId, Long locationId) {
		Campaign campaign = campaignService.get(campaignId);
		Location location = locationService.get(locationId);
		Brand brand = campaign.getBrand();
		if (!brand.getId().equals(location.getBrand().getId())) {
			throw new LocationBrandMismatchException(locationId, campaignId);
		}
		AdBrief brief = new AdBrief(brand.getName(), brand.getVoiceGuidelines(), brand.getBannedWords(),
				location.getName(), location.getCity(), location.getRegion(), location.getLocalOffer(),
				campaign.getTheme());
		return new GenerationInputs(campaign.getId(), location.getId(), brief);
	}

}
