package com.franchiseads.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.franchiseads.brand.Brand;
import com.franchiseads.campaign.Campaign;
import com.franchiseads.campaign.CampaignService;
import com.franchiseads.common.error.ResourceNotFoundException;
import com.franchiseads.location.Location;
import com.franchiseads.location.LocationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GenerationInputLoaderTests {

	private final CampaignService campaignService = mock(CampaignService.class);

	private final LocationService locationService = mock(LocationService.class);

	private final GenerationInputLoader loader = new GenerationInputLoader(campaignService, locationService);

	private final Brand acme = withId(new Brand("Acme Fitness", "Plain-spoken.", List.of("cheap", "best in town")), 1L);

	@Test
	void copiesBrandLocationAndCampaignIntoTheBrief() {
		when(campaignService.get(3L)).thenReturn(withId(new Campaign(acme, "Spring", "New year, new routine"), 3L));
		when(locationService.get(42L))
			.thenReturn(withId(new Location(acme, "Acme Downtown", "Austin", "TX", "First month free"), 42L));

		GenerationInputs inputs = loader.load(3L, 42L);

		assertThat(inputs.campaignId()).isEqualTo(3L);
		assertThat(inputs.locationId()).isEqualTo(42L);
		assertThat(inputs.brief()).isEqualTo(new AdBrief("Acme Fitness", "Plain-spoken.", List.of("cheap",
				"best in town"), "Acme Downtown", "Austin", "TX", "First month free", "New year, new routine"));
	}

	@Test
	void rejectsLocationOfAnotherBrand() {
		Brand other = withId(new Brand("Other Gym", null, List.of()), 2L);
		when(campaignService.get(3L)).thenReturn(withId(new Campaign(acme, "Spring", "Theme"), 3L));
		when(locationService.get(42L)).thenReturn(withId(new Location(other, "Other", "Dallas", null, null), 42L));

		assertThatThrownBy(() -> loader.load(3L, 42L)).isInstanceOf(LocationBrandMismatchException.class)
			.hasMessage("Location 42 does not belong to the brand of campaign 3");
	}

	@Test
	void propagatesUnknownCampaignAndLocation() {
		when(campaignService.get(3L)).thenThrow(new ResourceNotFoundException("Campaign", 3L));
		assertThatThrownBy(() -> loader.load(3L, 42L)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Campaign 3 not found");

		when(campaignService.get(4L)).thenReturn(withId(new Campaign(acme, "Spring", "Theme"), 4L));
		when(locationService.get(43L)).thenThrow(new ResourceNotFoundException("Location", 43L));
		assertThatThrownBy(() -> loader.load(4L, 43L)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Location 43 not found");
	}

	private static <T> T withId(T entity, Long id) {
		ReflectionTestUtils.setField(entity, "id", id);
		return entity;
	}

}
