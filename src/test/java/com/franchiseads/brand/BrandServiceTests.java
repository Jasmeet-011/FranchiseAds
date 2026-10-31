package com.franchiseads.brand;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.franchiseads.common.error.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BrandServiceTests {

	private final BrandRepository brands = mock(BrandRepository.class);

	private final BrandService service = new BrandService(brands);

	@Test
	void getUnknownBrandThrowsNotFound() {
		given(brands.findById(42L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(42L)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Brand 42 not found");
	}

}
