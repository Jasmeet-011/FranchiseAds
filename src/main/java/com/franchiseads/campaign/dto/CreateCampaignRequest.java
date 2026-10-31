package com.franchiseads.campaign.dto;

import com.franchiseads.common.Strings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /brands/{brandId}/campaigns}. Both fields are required: the theme goes into the generation
 * prompt. Strings are stripped on construction.
 */
public record CreateCampaignRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 500) String theme) {

	public CreateCampaignRequest {
		name = Strings.strip(name);
		theme = Strings.strip(theme);
	}

}
