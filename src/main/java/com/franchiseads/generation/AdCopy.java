package com.franchiseads.generation;

import java.util.List;

/**
 * Ad copy as parsed from model output: the right shape, but not yet validated. Counts, lengths and content rules are
 * the validator's job (ADR-010).
 */
public record AdCopy(List<String> headlines, List<String> descriptions) {

	public AdCopy {
		headlines = List.copyOf(headlines);
		descriptions = List.copyOf(descriptions);
	}

}
