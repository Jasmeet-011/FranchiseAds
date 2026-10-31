package com.franchiseads.campaign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.franchiseads.brand.BrandService;
import com.franchiseads.campaign.dto.CreateCampaignRequest;
import com.franchiseads.common.error.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CampaignServiceTests {

	private final CampaignRepository campaigns = mock(CampaignRepository.class);

	private final BrandService brandService = mock(BrandService.class);

	private final CampaignService service = new CampaignService(campaigns, brandService);

	@Test
	void createUnderUnknownBrandThrowsNotFoundAndSavesNothing() {
		given(brandService.get(99L)).willThrow(new ResourceNotFoundException("Brand", 99L));

		assertThatThrownBy(() -> service.create(99L, new CreateCampaignRequest("Spring", "New season")))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Brand 99 not found");
		verifyNoInteractions(campaigns);
	}

	@Test
	void listForUnknownBrandThrowsNotFound() {
		given(brandService.get(99L)).willThrow(new ResourceNotFoundException("Brand", 99L));

		assertThatThrownBy(() -> service.listForBrand(99L)).isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(campaigns);
	}

	@Test
	void getUnknownCampaignThrowsNotFound() {
		given(campaigns.findById(42L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(42L)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Campaign 42 not found");
	}

}
