package com.franchiseads.variant;

import java.util.List;

/**
 * A generation result to store as a new {@code DRAFT} variant.
 *
 * @param violations empty for valid copy; otherwise the variant is flagged
 * @param generationAttempts model calls that returned a response (1 or 2)
 * @param model the model that wrote the stored copy
 */
public record GeneratedVariant(Long campaignId, Long locationId, List<String> headlines, List<String> descriptions,
		List<String> violations, int generationAttempts, String model) {

	public GeneratedVariant {
		headlines = List.copyOf(headlines);
		descriptions = List.copyOf(descriptions);
		violations = List.copyOf(violations);
	}

}
