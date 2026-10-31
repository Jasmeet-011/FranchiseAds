package com.franchiseads.location.dto;

import com.franchiseads.common.Strings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /brands/{brandId}/locations}. Strings are stripped on construction; blank optional fields
 * become null.
 */
public record CreateLocationRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 100) String city,
		@Size(max = 100) String region,
		@Size(max = 200) String localOffer) {

	public CreateLocationRequest {
		name = Strings.strip(name);
		city = Strings.strip(city);
		region = Strings.stripToNull(region);
		localOffer = Strings.stripToNull(localOffer);
	}

}
