package com.franchiseads.variant;

import com.franchiseads.common.openapi.ProblemResponses;
import com.franchiseads.variant.dto.RejectRequest;
import com.franchiseads.variant.dto.VariantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Variants")
class VariantController {

	private final VariantService variantService;

	VariantController(VariantService variantService) {
		this.variantService = variantService;
	}

	/** {@code ?status=} takes the enum names (e.g. {@code DRAFT}); anything else is 400. */
	@GetMapping("/campaigns/{campaignId}/variants")
	@Operation(summary = "List a campaign's variants, optionally by status")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND })
	List<VariantResponse> listForCampaign(@PathVariable Long campaignId,
			@RequestParam(required = false) VariantStatus status) {
		return variantService.listForCampaign(campaignId, status).stream().map(VariantResponse::from).toList();
	}

	@GetMapping("/variants/{id}")
	@Operation(summary = "Get a variant")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND })
	VariantResponse get(@PathVariable Long id) {
		return VariantResponse.from(variantService.get(id));
	}

	@PostMapping("/variants/{id}/submit")
	@Operation(summary = "Submit a valid DRAFT variant for review")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT })
	VariantResponse submit(@PathVariable Long id) {
		return VariantResponse.from(variantService.submit(id));
	}

	@PostMapping("/variants/{id}/approve")
	@Operation(summary = "Approve a variant pending review")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT })
	VariantResponse approve(@PathVariable Long id) {
		return VariantResponse.from(variantService.approve(id));
	}

	@PostMapping("/variants/{id}/reject")
	@Operation(summary = "Reject a variant pending review, with a reason")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT })
	VariantResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
		return VariantResponse.from(variantService.reject(id, request.reason()));
	}

}
