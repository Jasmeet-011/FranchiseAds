package com.franchiseads.generation;

import java.util.List;
import java.util.Objects;

/**
 * Everything the prompt needs about one brand, location and campaign, as plain values: prompt building never touches
 * JPA entities. Every field except the brand name is user-supplied text and goes into the prompt as data (ADR-023).
 *
 * @param voiceGuidelines optional
 * @param bannedWords may be empty, never null
 * @param region optional
 * @param localOffer optional
 */
public record AdBrief(String brandName, String voiceGuidelines, List<String> bannedWords, String locationName,
		String city, String region, String localOffer, String campaignTheme) {

	public AdBrief {
		Objects.requireNonNull(brandName, "brandName");
		Objects.requireNonNull(locationName, "locationName");
		Objects.requireNonNull(city, "city");
		Objects.requireNonNull(campaignTheme, "campaignTheme");
		bannedWords = (bannedWords != null) ? List.copyOf(bannedWords) : List.of();
	}

}
