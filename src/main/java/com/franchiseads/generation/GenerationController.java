package com.franchiseads.generation;

import com.franchiseads.common.openapi.ProblemResponses;
import com.franchiseads.generation.dto.GenerateRequest;
import com.franchiseads.variant.dto.VariantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Generation")
class GenerationController {

	private final GenerationService generationService;

	GenerationController(GenerationService generationService) {
		this.generationService = generationService;
	}

	/** 201 with the saved variant, valid or flagged. Errors per docs/flow.md. */
	@PostMapping("/campaigns/{campaignId}/generate")
	@Operation(summary = "Generate ad copy for a location",
			description = "At most two model calls: one regeneration with the violations fed back.")
	@ApiResponse(responseCode = "201",
			description = "Variant saved as DRAFT: valid, or flagged (validationPassed=false) with its violations",
			useReturnTypeSchema = true)
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.UNPROCESSABLE_CONTENT,
			HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT })
	ResponseEntity<VariantResponse> generate(@PathVariable Long campaignId,
			@Valid @RequestBody GenerateRequest request) {
		VariantResponse body = VariantResponse.from(generationService.generate(campaignId, request.locationId()));
		return ResponseEntity.created(URI.create("/variants/" + body.id())).body(body);
	}

}
