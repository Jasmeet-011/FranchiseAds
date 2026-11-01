package com.franchiseads.campaign;

import com.franchiseads.campaign.dto.CampaignResponse;
import com.franchiseads.campaign.dto.CreateCampaignRequest;
import com.franchiseads.common.openapi.ProblemResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Campaigns")
class CampaignController {

	private final CampaignService campaignService;

	CampaignController(CampaignService campaignService) {
		this.campaignService = campaignService;
	}

	@PostMapping("/brands/{brandId}/campaigns")
	@Operation(summary = "Add a campaign to a brand")
	@ApiResponse(responseCode = "201", description = "Campaign created", useReturnTypeSchema = true)
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND })
	ResponseEntity<CampaignResponse> create(@PathVariable Long brandId,
			@Valid @RequestBody CreateCampaignRequest request) {
		CampaignResponse body = CampaignResponse.from(campaignService.create(brandId, request));
		return ResponseEntity.created(URI.create("/campaigns/" + body.id())).body(body);
	}

	@GetMapping("/brands/{brandId}/campaigns")
	@Operation(summary = "List a brand's campaigns")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND })
	List<CampaignResponse> listForBrand(@PathVariable Long brandId) {
		return campaignService.listForBrand(brandId).stream().map(CampaignResponse::from).toList();
	}

	@GetMapping("/campaigns/{id}")
	@Operation(summary = "Get a campaign")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND })
	CampaignResponse get(@PathVariable Long id) {
		return CampaignResponse.from(campaignService.get(id));
	}

}
