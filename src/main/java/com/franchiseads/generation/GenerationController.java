package com.franchiseads.generation;

import com.franchiseads.generation.dto.GenerateRequest;
import com.franchiseads.variant.dto.VariantResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GenerationController {

	private final GenerationService generationService;

	GenerationController(GenerationService generationService) {
		this.generationService = generationService;
	}

	/** 201 with the saved variant, valid or flagged. Errors per docs/flow.md. */
	@PostMapping("/campaigns/{campaignId}/generate")
	ResponseEntity<VariantResponse> generate(@PathVariable Long campaignId,
			@Valid @RequestBody GenerateRequest request) {
		VariantResponse body = VariantResponse.from(generationService.generate(campaignId, request.locationId()));
		return ResponseEntity.created(URI.create("/variants/" + body.id())).body(body);
	}

}
