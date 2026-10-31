package com.franchiseads.location;

import com.franchiseads.brand.Brand;
import com.franchiseads.brand.BrandService;
import com.franchiseads.common.error.ResourceNotFoundException;
import com.franchiseads.location.dto.CreateLocationRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {

	private final LocationRepository locations;

	private final BrandService brandService;

	LocationService(LocationRepository locations, BrandService brandService) {
		this.locations = locations;
		this.brandService = brandService;
	}

	@Transactional
	public Location create(Long brandId, CreateLocationRequest request) {
		Brand brand = brandService.get(brandId);
		return locations.save(
				new Location(brand, request.name(), request.city(), request.region(), request.localOffer()));
	}

	/** Locations of an existing brand, oldest first. An unknown brand is 404, not an empty list. */
	@Transactional(readOnly = true)
	public List<Location> listForBrand(Long brandId) {
		brandService.get(brandId);
		return locations.findAllByBrandIdOrderByIdAsc(brandId);
	}

	@Transactional(readOnly = true)
	public Location get(Long id) {
		return locations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Location", id));
	}

}
