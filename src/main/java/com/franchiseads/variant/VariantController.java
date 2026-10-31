package com.franchiseads.variant;

import com.franchiseads.variant.dto.VariantResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VariantController {

	private final VariantService variantService;

	VariantController(VariantService variantService) {
		this.variantService = variantService;
	}

	/** {@code ?status=} takes the enum names (e.g. {@code DRAFT}); anything else is 400. */
	@GetMapping("/campaigns/{campaignId}/variants")
	List<VariantResponse> listForCampaign(@PathVariable Long campaignId,
			@RequestParam(required = false) VariantStatus status) {
		return variantService.listForCampaign(campaignId, status).stream().map(VariantResponse::from).toList();
	}

	@GetMapping("/variants/{id}")
	VariantResponse get(@PathVariable Long id) {
		return VariantResponse.from(variantService.get(id));
	}

}
