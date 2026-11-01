package com.franchiseads.location;

import com.franchiseads.common.openapi.ProblemResponses;
import com.franchiseads.location.dto.CreateLocationRequest;
import com.franchiseads.location.dto.LocationResponse;
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
@Tag(name = "Locations")
class LocationController {

	private final LocationService locationService;

	LocationController(LocationService locationService) {
		this.locationService = locationService;
	}

	@PostMapping("/brands/{brandId}/locations")
	@Operation(summary = "Add a location to a brand")
	@ApiResponse(responseCode = "201", description = "Location created", useReturnTypeSchema = true)
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND })
	ResponseEntity<LocationResponse> create(@PathVariable Long brandId,
			@Valid @RequestBody CreateLocationRequest request) {
		LocationResponse body = LocationResponse.from(locationService.create(brandId, request));
		return ResponseEntity.created(URI.create("/locations/" + body.id())).body(body);
	}

	@GetMapping("/brands/{brandId}/locations")
	@Operation(summary = "List a brand's locations")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND })
	List<LocationResponse> listForBrand(@PathVariable Long brandId) {
		return locationService.listForBrand(brandId).stream().map(LocationResponse::from).toList();
	}

	@GetMapping("/locations/{id}")
	@Operation(summary = "Get a location")
	@ProblemResponses({ HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND })
	LocationResponse get(@PathVariable Long id) {
		return LocationResponse.from(locationService.get(id));
	}

}
