package com.franchiseads.campaign;

import com.franchiseads.campaign.dto.CampaignResponse;
import com.franchiseads.campaign.dto.CreateCampaignRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CampaignController {

	private final CampaignService campaignService;

	CampaignController(CampaignService campaignService) {
		this.campaignService = campaignService;
	}

	@PostMapping("/brands/{brandId}/campaigns")
	ResponseEntity<CampaignResponse> create(@PathVariable Long brandId,
			@Valid @RequestBody CreateCampaignRequest request) {
		CampaignResponse body = CampaignResponse.from(campaignService.create(brandId, request));
		return ResponseEntity.created(URI.create("/campaigns/" + body.id())).body(body);
	}

	@GetMapping("/brands/{brandId}/campaigns")
	List<CampaignResponse> listForBrand(@PathVariable Long brandId) {
		return campaignService.listForBrand(brandId).stream().map(CampaignResponse::from).toList();
	}

	@GetMapping("/campaigns/{id}")
	CampaignResponse get(@PathVariable Long id) {
		return CampaignResponse.from(campaignService.get(id));
	}

}
