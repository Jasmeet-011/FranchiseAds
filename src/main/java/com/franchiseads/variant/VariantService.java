package com.franchiseads.variant;

import com.franchiseads.campaign.Campaign;
import com.franchiseads.location.Location;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VariantService {

	private final AdVariantRepository variants;

	private final EntityManager entityManager;

	VariantService(AdVariantRepository variants, EntityManager entityManager) {
		this.variants = variants;
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

}
