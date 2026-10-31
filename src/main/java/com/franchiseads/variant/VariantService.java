package com.franchiseads.variant;

import com.franchiseads.campaign.Campaign;
import com.franchiseads.campaign.CampaignService;
import com.franchiseads.common.error.ResourceNotFoundException;
import com.franchiseads.location.Location;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VariantService {

	private final AdVariantRepository variants;

	private final CampaignService campaignService;

	private final EntityManager entityManager;

	VariantService(AdVariantRepository variants, CampaignService campaignService, EntityManager entityManager) {
		this.variants = variants;
		this.campaignService = campaignService;
		this.entityManager = entityManager;
	}

	/**
	 * Step 7 of the generate flow, in its own short transaction (ADR-012). The campaign and location are referenced
	 * by ID, not reloaded: they were checked when the inputs were loaded.
	 */
	@Transactional
	public AdVariant saveGenerated(GeneratedVariant generated) {
		Campaign campaign = entityManager.getReference(Campaign.class, generated.campaignId());
		Location location = entityManager.getReference(Location.class, generated.locationId());
		return variants.save(new AdVariant(campaign, location, generated.headlines(), generated.descriptions(),
				generated.violations().isEmpty(), generated.violations(), generated.generationAttempts(),
				generated.model()));
	}

	/**
	 * Variants of an existing campaign, oldest first, optionally only those in one status. An unknown campaign is
	 * 404, not an empty list (ADR-018).
	 * @param status null for every status
	 */
	@Transactional(readOnly = true)
	public List<AdVariant> listForCampaign(Long campaignId, VariantStatus status) {
		campaignService.get(campaignId);
		return (status == null) ? variants.findAllByCampaignIdOrderByIdAsc(campaignId)
				: variants.findAllByCampaignIdAndStatusOrderByIdAsc(campaignId, status);
	}

	@Transactional(readOnly = true)
	public AdVariant get(Long id) {
		return variants.findById(id).orElseThrow(() -> new ResourceNotFoundException("Variant", id));
	}

}
