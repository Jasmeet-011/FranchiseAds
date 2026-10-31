package com.franchiseads.location.dto;

import com.franchiseads.location.Location;
import java.time.Instant;

public record LocationResponse(Long id, Long brandId, String name, String city, String region, String localOffer,
		Instant createdAt) {

	public static LocationResponse from(Location location) {
		return new LocationResponse(location.getId(), location.getBrand().getId(), location.getName(),
				location.getCity(), location.getRegion(), location.getLocalOffer(), location.getCreatedAt());
	}

}
