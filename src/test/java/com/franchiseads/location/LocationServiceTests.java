package com.franchiseads.location;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.franchiseads.brand.BrandService;
import com.franchiseads.common.error.ResourceNotFoundException;
import com.franchiseads.location.dto.CreateLocationRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocationServiceTests {

	private final LocationRepository locations = mock(LocationRepository.class);

	private final BrandService brandService = mock(BrandService.class);

	private final LocationService service = new LocationService(locations, brandService);

	@Test
	void createUnderUnknownBrandThrowsNotFoundAndSavesNothing() {
		given(brandService.get(99L)).willThrow(new ResourceNotFoundException("Brand", 99L));

		assertThatThrownBy(() -> service.create(99L, new CreateLocationRequest("Downtown", "Austin", null, null)))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Brand 99 not found");
		verifyNoInteractions(locations);
	}

	@Test
	void listForUnknownBrandThrowsNotFound() {
		given(brandService.get(99L)).willThrow(new ResourceNotFoundException("Brand", 99L));

		assertThatThrownBy(() -> service.listForBrand(99L)).isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(locations);
	}

	@Test
	void getUnknownLocationThrowsNotFound() {
		given(locations.findById(42L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(42L)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Location 42 not found");
	}

}
