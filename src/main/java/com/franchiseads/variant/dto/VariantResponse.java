package com.franchiseads.variant.dto;

import com.franchiseads.variant.AdVariant;
import com.franchiseads.variant.VariantStatus;
import java.time.Instant;
import java.util.List;

/** One ad variant, as in the response example in docs/flow.md. */
public record VariantResponse(Long id, Long campaignId, Long locationId, VariantStatus status,
		boolean validationPassed, List<String> violations, List<String> headlines, List<String> descriptions,
		int generationAttempts, String model, Instant createdAt) {

	public static VariantResponse from(AdVariant variant) {
		return new VariantResponse(variant.getId(), variant.getCampaign().getId(), variant.getLocation().getId(),
				variant.getStatus(), variant.isValidationPassed(), variant.getViolations(), variant.getHeadlines(),
				variant.getDescriptions(), variant.getGenerationAttempts(), variant.getModel(),
				variant.getCreatedAt());
	}

}
