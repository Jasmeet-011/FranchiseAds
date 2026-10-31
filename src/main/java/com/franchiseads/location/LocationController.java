package com.franchiseads.location;

import com.franchiseads.location.dto.CreateLocationRequest;
import com.franchiseads.location.dto.LocationResponse;
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
class LocationController {

	private final LocationService locationService;

	LocationController(LocationService locationService) {
		this.locationService = locationService;
	}

	@PostMapping("/brands/{brandId}/locations")
	ResponseEntity<LocationResponse> create(@PathVariable Long brandId,
			@Valid @RequestBody CreateLocationRequest request) {
		LocationResponse body = LocationResponse.from(locationService.create(brandId, request));
		return ResponseEntity.created(URI.create("/locations/" + body.id())).body(body);
	}

	@GetMapping("/brands/{brandId}/locations")
	List<LocationResponse> listForBrand(@PathVariable Long brandId) {
		return locationService.listForBrand(brandId).stream().map(LocationResponse::from).toList();
	}

	@GetMapping("/locations/{id}")
	LocationResponse get(@PathVariable Long id) {
		return LocationResponse.from(locationService.get(id));
	}

}
