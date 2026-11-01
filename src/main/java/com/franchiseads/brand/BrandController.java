package com.franchiseads.brand;

import com.franchiseads.brand.dto.BrandResponse;
import com.franchiseads.brand.dto.CreateBrandRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/brands")
@Tag(name = "Brands")
class BrandController {

	private final BrandService brandService;

	BrandController(BrandService brandService) {
		this.brandService = brandService;
	}

	@PostMapping
	@Operation(summary = "Create a brand")
	@ApiResponse(responseCode = "201", description = "Brand created", useReturnTypeSchema = true)
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT })
	ResponseEntity<BrandResponse> create(@Valid @RequestBody CreateBrandRequest request) {
		BrandResponse body = BrandResponse.from(brandService.create(request));
		return ResponseEntity.created(URI.create("/brands/" + body.id())).body(body);
	}

	@GetMapping
	@Operation(summary = "List all brands")
	List<BrandResponse> list() {
		return brandService.list().stream().map(BrandResponse::from).toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a brand")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND })
	BrandResponse get(@PathVariable Long id) {
		return BrandResponse.from(brandService.get(id));
	}

}
