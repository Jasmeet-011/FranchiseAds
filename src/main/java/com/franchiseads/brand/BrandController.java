package com.franchiseads.brand;

import com.franchiseads.brand.dto.BrandResponse;
import com.franchiseads.brand.dto.CreateBrandRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/brands")
class BrandController {

	private final BrandService brandService;

	BrandController(BrandService brandService) {
		this.brandService = brandService;
	}

	@PostMapping
	ResponseEntity<BrandResponse> create(@Valid @RequestBody CreateBrandRequest request) {
		BrandResponse body = BrandResponse.from(brandService.create(request));
		return ResponseEntity.created(URI.create("/brands/" + body.id())).body(body);
	}

	@GetMapping
	List<BrandResponse> list() {
		return brandService.list().stream().map(BrandResponse::from).toList();
	}

	@GetMapping("/{id}")
	BrandResponse get(@PathVariable Long id) {
		return BrandResponse.from(brandService.get(id));
	}

}
