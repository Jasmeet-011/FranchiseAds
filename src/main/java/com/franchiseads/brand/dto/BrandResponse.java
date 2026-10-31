package com.franchiseads.brand.dto;

import com.franchiseads.brand.Brand;
import java.time.Instant;
import java.util.List;

public record BrandResponse(Long id, String name, String voiceGuidelines, List<String> bannedWords,
		Instant createdAt) {

	public static BrandResponse from(Brand brand) {
		return new BrandResponse(brand.getId(), brand.getName(), brand.getVoiceGuidelines(), brand.getBannedWords(),
				brand.getCreatedAt());
	}

}
